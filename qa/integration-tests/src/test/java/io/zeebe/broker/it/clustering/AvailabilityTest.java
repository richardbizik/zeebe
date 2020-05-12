/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.clustering;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.client.api.response.ActivatedJob;
import io.zeebe.client.api.response.BrokerInfo;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.WorkflowInstanceCreationIntent;
import io.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class AvailabilityTest {

  private static final String JOBTYPE = "availability-test";
  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess("process")
          .startEvent()
          .serviceTask(
              "task",
              t -> {
                t.zeebeTaskType(JOBTYPE);
              })
          .endEvent()
          .done();
  private final int partitionCount = 3;
  private final Timeout testTimeout = Timeout.seconds(120);
  private final ClusteringRule clusteringRule = new ClusteringRule(partitionCount, 1, 3);
  private final GrpcClientRule clientRule = new GrpcClientRule(clusteringRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(testTimeout).around(clusteringRule).around(clientRule);

  private long workflowKey;

  @Before
  public void setup() {
    workflowKey = clientRule.deployWorkflow(WORKFLOW);
  }

  @Test
  public void shouldCreateWorkflowWhenOnePartitionDown() {
    final BrokerInfo leaderForPartition = clusteringRule.getLeaderForPartition(partitionCount);

    // when
    clusteringRule.stopBroker(leaderForPartition.getNodeId(), false);

    for (int i = 0; i < 2 * partitionCount; i++) {
      clientRule.createWorkflowInstance(workflowKey);
    }

    // then
    // all create instance requests should complete successfully
    assertThat(
            RecordingExporter.workflowInstanceCreationRecords()
                .withIntent(WorkflowInstanceCreationIntent.CREATED)
                .map(Record::getPartitionId)
                .limit(2 * partitionCount)
                .count())
        .isEqualTo(2 * partitionCount);
  }

  @Test
  public void shouldCreateWorkflowWhenPartitionRecovers() {
    // given
    final int failingPartition = partitionCount;
    final BrokerInfo leaderForPartition = clusteringRule.getLeaderForPartition(failingPartition);
    clusteringRule.stopBroker(leaderForPartition.getNodeId(), false);

    for (int i = 0; i < partitionCount; i++) {
      clientRule.createWorkflowInstance(workflowKey);
    }

    // when
    clusteringRule.restartBroker(leaderForPartition.getNodeId());

    for (int i = 0; i < partitionCount; i++) {
      clientRule.createWorkflowInstance(workflowKey);
    }

    // then
    // all create instance requests should complete successfully
    assertThat(
            RecordingExporter.workflowInstanceCreationRecords()
                .withIntent(WorkflowInstanceCreationIntent.CREATED)
                .filter(r -> r.getPartitionId() == failingPartition))
        .hasSizeGreaterThanOrEqualTo(1);
  }

  @Test
  public void shouldActivateJobsWhenOnePartitionDown() {
    // given
    final int numInstances = 2 * partitionCount;
    final BrokerInfo leaderForPartition = clusteringRule.getLeaderForPartition(partitionCount);
    clusteringRule.stopBroker(leaderForPartition.getNodeId(), false);

    for (int i = 0; i < numInstances; i++) {
      clientRule.createWorkflowInstance(workflowKey);
    }

    // when

    final Set<Long> activatedJobsKey = new HashSet<>();
    for (int i = 0; i < numInstances; i++) {
      final List<ActivatedJob> jobs =
          clientRule
              .getClient()
              .newActivateJobsCommand()
              .jobType(JOBTYPE)
              .maxJobsToActivate(1)
              .timeout(Duration.ofMinutes(5))
              .requestTimeout(Duration.ofSeconds(5)) // put a lower timeout than gateway timeout
              .send()
              .join()
              .getJobs();
      jobs.forEach(job -> activatedJobsKey.add(job.getKey()));
    }

    // then
    assertThat(activatedJobsKey).hasSize(numInstances);
  }
}
