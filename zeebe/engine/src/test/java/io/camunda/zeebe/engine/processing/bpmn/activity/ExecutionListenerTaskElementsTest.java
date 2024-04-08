/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.model.bpmn.builder.AbstractTaskBuilder;
import io.camunda.zeebe.model.bpmn.builder.StartEventBuilder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Verifies the behavior of task-based elements with execution listeners. */
@RunWith(Parameterized.class)
public class ExecutionListenerTaskElementsTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "process";
  private static final String DMN_RESOURCE = "/dmn/drg-force-user.dmn";

  private static final String START_EL_TYPE = "start_execution_listener_job";
  private static final String END_EL_TYPE = "end_execution_listener_job";

  private static final Consumer<Long> DO_NOTHING = pik -> {};

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Parameter(0)
  public BpmnElementType elementType;

  @Parameter(1)
  public Function<StartEventBuilder, AbstractTaskBuilder<?, ?>> taskConfigurer;

  @Parameter(2)
  public Consumer<Long> processTask;

  @Parameters(name = "{index}: Test with {0}")
  public static Collection<Object[]> parameters() {
    return Arrays.asList(
        new Object[][] {
          {BpmnElementType.TASK, setup(AbstractFlowNodeBuilder::task), DO_NOTHING},
          {BpmnElementType.MANUAL_TASK, setup(AbstractFlowNodeBuilder::manualTask), DO_NOTHING},
          {
            BpmnElementType.SERVICE_TASK,
            setup(b -> b.serviceTask().zeebeJobType("service_task_job")),
            createCompleteJobWorkerTaskProcessor("service_task_job")
          },
          // script task with job worker implementation
          {
            BpmnElementType.SCRIPT_TASK,
            setup(b -> b.scriptTask().zeebeJobType("script_task_job")),
            createCompleteJobWorkerTaskProcessor("script_task_job")
          },
          // script task with Zeebe expression
          {
            BpmnElementType.SCRIPT_TASK,
            setup(b -> b.scriptTask().zeebeExpression("225 + 500").zeebeResultVariable("sum")),
            DO_NOTHING
          },
          // business rule task with job worker implementation
          {
            BpmnElementType.BUSINESS_RULE_TASK,
            setup(b -> b.businessRuleTask().zeebeJobType("business_rule_task_job")),
            createCompleteJobWorkerTaskProcessor("business_rule_task_job")
          },
          // business rule task with a called decision
          {
            BpmnElementType.BUSINESS_RULE_TASK,
            setup(
                b ->
                    b.businessRuleTask()
                        .zeebeCalledDecisionId("jedi_or_sith")
                        .zeebeResultVariable("result")),
            DO_NOTHING
          },
          {
            BpmnElementType.SEND_TASK,
            setup(b -> b.sendTask().zeebeJobType("send_task_job")),
            createCompleteJobWorkerTaskProcessor("send_task_job")
          },
          // user task with job worker implementation
          {
            BpmnElementType.USER_TASK,
            setup(AbstractFlowNodeBuilder::userTask),
            createCompleteJobWorkerTaskProcessor("io.camunda.zeebe:userTask")
          },
          // zeebe user task
          {
            BpmnElementType.USER_TASK,
            setup(b -> b.userTask().zeebeUserTask().zeebeAssignee("foo")),
            (Consumer<Long>) pik -> ENGINE.userTask().ofInstance(pik).complete()
          } /*,
            {
              BpmnElementType.RECEIVE_TASK,
              setup(
                  b ->
                      b.receiveTask()
                          .message(mb -> mb.name("msg").zeebeCorrelationKey("=\"id-123\""))),
              (Consumer<Long>)
                  pik -> ENGINE.message().withName("msg").withCorrelationKey("id-123").publish()
            }*/
        });
  }

  @Test
  public void shouldCompleteTaskWithMultipleExecutionListeners() {
    // given
    deployProcess(
        createProcessWithTask(
            b ->
                b.zeebeStartExecutionListener(START_EL_TYPE + "_1")
                    .zeebeStartExecutionListener(START_EL_TYPE + "_2")
                    .zeebeEndExecutionListener(END_EL_TYPE + "_1")
                    .zeebeEndExecutionListener(END_EL_TYPE + "_2")));
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when: complete the start execution listener jobs
    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_1").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_2").complete();

    // process the main task activity
    processTask.accept(processInstanceKey);

    // complete the end execution listener jobs
    ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_1").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_2").complete();

    // then
    assertExecutionListenerJobsCompleted(
        processInstanceKey,
        START_EL_TYPE + "_1",
        START_EL_TYPE + "_2",
        END_EL_TYPE + "_1",
        END_EL_TYPE + "_2");

    // assert the process instance has completed as expected
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(elementType, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(elementType, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(elementType, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(elementType, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(elementType, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(elementType, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(elementType, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(elementType, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  private void assertExecutionListenerJobsCompleted(
      final long processInstanceKey, final String... elJobTypes) {
    assertThat(
            RecordingExporter.jobRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .withIntent(JobIntent.COMPLETED)
                .withElementId(elementType.name())
                .limit(elJobTypes.length))
        .extracting(r -> r.getValue().getType())
        .containsExactly(elJobTypes);
  }

  private static void deployProcess(final BpmnModelInstance modelInstance) {
    ENGINE
        .deployment()
        .withXmlClasspathResource(DMN_RESOURCE)
        .withXmlResource(modelInstance)
        .deploy();
  }

  private BpmnModelInstance createProcessWithTask(
      final Consumer<AbstractTaskBuilder<?, ?>> consumer) {
    final var taskBuilder =
        taskConfigurer
            .apply(Bpmn.createExecutableProcess(PROCESS_ID).startEvent())
            .id(elementType.name());

    consumer.accept(taskBuilder);

    return taskBuilder.endEvent().done();
  }

  private static Consumer<Long> createCompleteJobWorkerTaskProcessor(final String taskType) {
    return pik -> ENGINE.job().ofInstance(pik).withType(taskType).complete();
  }

  /**
   * Helper method used to avoid verbose {@code Function<StartEventBuilder, AbstractTaskBuilder<?,
   * ?>>} type declaration for each task configurer inside: {@link
   * ExecutionListenerTaskElementsTest#parameters()} method
   */
  private static Function<StartEventBuilder, AbstractTaskBuilder<?, ?>> setup(
      final Function<StartEventBuilder, AbstractTaskBuilder<?, ?>> taskConfigurer) {
    return taskConfigurer;
  }
}
