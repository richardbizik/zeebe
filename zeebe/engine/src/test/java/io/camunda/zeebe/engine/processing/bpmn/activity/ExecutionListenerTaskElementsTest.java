/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static io.camunda.zeebe.test.util.record.RecordingExporter.records;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.oneOf;
import static org.junit.Assume.assumeThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.model.bpmn.builder.AbstractTaskBuilder;
import io.camunda.zeebe.model.bpmn.builder.StartEventBuilder;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
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

  @Test
  public void shouldRetryStartExecutionListenerAfterFailure() {
    // given
    deployProcess(createProcessWithTask(b -> b.zeebeStartExecutionListener(START_EL_TYPE)));
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when: fail start EL with retries
    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE).withRetries(1).fail();
    // complete failed start EL job
    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE).complete();

    // then
    // process the main task activity
    processTask.accept(processInstanceKey);

    // then: assert the start EL job was completed after the failure
    assertThat(records().betweenProcessInstance(processInstanceKey))
        .extracting(Record::getValueType, Record::getIntent)
        .containsSubsequence(
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(ValueType.JOB, JobIntent.CREATED),
            tuple(ValueType.JOB, JobIntent.FAILED),
            tuple(ValueType.JOB, JobIntent.COMPLETE),
            tuple(ValueType.JOB, JobIntent.COMPLETED),
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_ACTIVATED));

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
            tuple(elementType, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(elementType, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(elementType, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldRetryEndExecutionListenerAfterFailure() {
    // given
    deployProcess(createProcessWithTask(b -> b.zeebeEndExecutionListener(END_EL_TYPE)));
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // process the main task activity
    processTask.accept(processInstanceKey);

    // when: fail end EL with retries
    ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE).withRetries(1).fail();
    // complete failed end EL job
    ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE).complete();

    // then: assert the end EL job was completed after the failure
    assertThat(records().betweenProcessInstance(processInstanceKey))
        .extracting(Record::getValueType, Record::getIntent)
        .containsSubsequence(
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(ValueType.JOB, JobIntent.CREATED),
            tuple(ValueType.JOB, JobIntent.FAILED),
            tuple(ValueType.JOB, JobIntent.COMPLETE),
            tuple(ValueType.JOB, JobIntent.COMPLETED),
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_COMPLETED));

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
            tuple(elementType, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(elementType, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(elementType, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(elementType, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCreateIncidentForStartElWhenNoRetriesLeftAndProceedWithRemainingListeners() {
    // given
    deployProcess(
        createProcessWithTask(
            b ->
                b.zeebeStartExecutionListener(START_EL_TYPE + "_1")
                    .zeebeStartExecutionListener(START_EL_TYPE + "_2")));
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when: fail start EL job with no retries
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(START_EL_TYPE + "_1")
        .withRetries(0)
        .fail();

    // then
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    Assertions.assertThat(incident.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasErrorType(ErrorType.EXECUTION_LISTENER_NO_RETRIES)
        .hasErrorMessage("No more retries left.");

    // resolve incident & complete start EL jobs
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();
    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_1").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_2").complete();

    // process the main task activity
    processTask.accept(processInstanceKey);

    // then: assert the EL job was completed after incident resolution
    assertThat(
            records()
                .betweenProcessInstance(processInstanceKey)
                .withValueTypes(ValueType.JOB, ValueType.INCIDENT)
                .onlyEvents())
        .extracting(Record::getIntent)
        .containsSequence(
            JobIntent.CREATED,
            JobIntent.FAILED,
            IncidentIntent.CREATED,
            IncidentIntent.RESOLVED,
            JobIntent.COMPLETED,
            JobIntent.CREATED,
            JobIntent.COMPLETED);

    assertExecutionListenerJobsCompleted(
        processInstanceKey, START_EL_TYPE + "_1", START_EL_TYPE + "_2");

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
            tuple(elementType, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCreateIncidentForEndElWhenNoRetriesLeftAndProceedWithRemainingListeners() {
    // given
    deployProcess(
        createProcessWithTask(
            b ->
                b.zeebeEndExecutionListener(END_EL_TYPE + "_1")
                    .zeebeEndExecutionListener(END_EL_TYPE + "_2")));
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // process the main task activity
    processTask.accept(processInstanceKey);

    // when: fail end EL job with no retries
    ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_1").withRetries(0).fail();

    // then
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    Assertions.assertThat(incident.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasErrorType(ErrorType.EXECUTION_LISTENER_NO_RETRIES)
        .hasErrorMessage("No more retries left.");

    // resolve incident & complete end EL jobs
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();
    ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_1").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_2").complete();

    // assert the EL job was completed after incident resolution
    assertThat(
            records()
                .betweenProcessInstance(processInstanceKey)
                .withValueTypes(ValueType.JOB, ValueType.INCIDENT)
                .onlyEvents())
        .extracting(Record::getIntent)
        .containsSequence(
            JobIntent.CREATED,
            JobIntent.FAILED,
            IncidentIntent.CREATED,
            IncidentIntent.RESOLVED,
            JobIntent.COMPLETED,
            JobIntent.CREATED,
            JobIntent.COMPLETED);

    assertExecutionListenerJobsCompleted(
        processInstanceKey, END_EL_TYPE + "_1", END_EL_TYPE + "_2");

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
            tuple(elementType, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(elementType, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(elementType, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(elementType, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(elementType, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCreateIncidentDuringEvaluatingTaskInputMappingsAndProceedWithStartListeners() {
    // Skip test for `BpmnElementType.TASK` and `BpmnElementType.MANUAL_TASK` because
    // these element types do not support input mappings.
    assumeThat(elementType, is(not(oneOf(BpmnElementType.TASK, BpmnElementType.MANUAL_TASK))));

    // given
    deployProcess(
        createProcessWithTask(
            b ->
                b.zeebeInputExpression("assert(some_var, some_var != null)", "o_var_1")
                    .zeebeStartExecutionListener(START_EL_TYPE)));
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then: incident created
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    Assertions.assertThat(incident.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasErrorType(ErrorType.IO_MAPPING_ERROR)
        .hasErrorMessage(
            """
                Assertion failure on evaluate the expression '{o_var_1:assert(some_var, some_var != null)}': \
                The condition is not fulfilled The evaluation reported the following warnings:
                [NO_VARIABLE_FOUND] No variable found with name 'some_var'
                [NO_VARIABLE_FOUND] No variable found with name 'some_var'
                [ASSERT_FAILURE] The condition is not fulfilled""");

    // fix issue by providing missing `some_var` variable
    ENGINE
        .variables()
        .ofScope(processInstanceKey)
        .withDocument(Map.of("some_var", "foo_bar"))
        .update();
    // resolve incident
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // then: complete start EL job
    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE).complete();

    // process the main task activity
    processTask.accept(processInstanceKey);

    // assert the process instance has completed as expected
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(elementType, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(elementType, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(elementType, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(elementType, ProcessInstanceIntent.ELEMENT_COMPLETING),
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
