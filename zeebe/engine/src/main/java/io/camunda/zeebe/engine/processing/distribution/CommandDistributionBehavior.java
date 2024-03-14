/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.distribution;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.List;
import java.util.stream.IntStream;

/**
 * This behavior allows distributing a command to other partitions, and for those receiving
 * partitions to acknowledge the distributed commands back to the partition that started. This is
 * needed because the communication between partitions is unreliable.
 *
 * @see <a href="https://github.com/camunda/zeebe/blob/main/zeebe/docs/generalized_distribution.md">
 *     generalized_distribution.md</a>
 */
public final class CommandDistributionBehavior {

  private final StateWriter stateWriter;
  private final SideEffectWriter sideEffectWriter;
  private final List<Integer> otherPartitions;
  private final InterPartitionCommandSender interPartitionCommandSender;
  private final int currentPartitionId;

  public CommandDistributionBehavior(
      final Writers writers,
      final int currentPartition,
      final int partitionsCount,
      final InterPartitionCommandSender partitionCommandSender) {
    stateWriter = writers.state();
    sideEffectWriter = writers.sideEffect();
    interPartitionCommandSender = partitionCommandSender;
    otherPartitions =
        IntStream.range(Protocol.START_PARTITION_ID, Protocol.START_PARTITION_ID + partitionsCount)
            .filter(partition -> partition != currentPartition)
            .boxed()
            .toList();
    currentPartitionId = currentPartition;
  }

  /**
   * Distributes a command to all other partitions.
   *
   * @param distributionKey the key to identify this unique command distribution. The key is used to
   *     store the pending distribution, as the key of distributed command, to identify the
   *     distributing partition when processing the distributed command, and as the key to correlate
   *     the ACKNOWLEDGE command to the pending distribution. This can be a newly generated key, or
   *     the key identifying the entity that's being distributed. Please note that it must be unique
   *     for command distribution. Don't reuse the key to distribute another command.
   * @param command the command to distribute
   */
  public <T extends UnifiedRecordValue> void distributeCommand(
      final long distributionKey, final TypedRecord<T> command) {
    if (otherPartitions.isEmpty()) {
      return;
    }

    final var distributionRecord =
        new CommandDistributionRecord()
            .setPartitionId(currentPartitionId)
            .setValueType(command.getValueType())
            .setIntent(command.getIntent())
            .setCommandValue(command.getValue());

    stateWriter.appendFollowUpEvent(
        distributionKey, CommandDistributionIntent.STARTED, distributionRecord);

    otherPartitions.forEach(
        (partition) -> distributeToPartition(partition, distributionRecord, distributionKey));
  }

  private <T extends UnifiedRecordValue> void distributeToPartition(
      final int partition,
      final CommandDistributionRecord distributionRecord,
      final long distributionKey) {
    final var valueType = distributionRecord.getValueType();
    final var intent = distributionRecord.getIntent();

    // We don't need the actual record in the DISTRIBUTING event applier. In order to prevent
    // reaching the max message size we don't set the record value here.
    stateWriter.appendFollowUpEvent(
        distributionKey,
        CommandDistributionIntent.DISTRIBUTING,
        new CommandDistributionRecord()
            .setPartitionId(partition)
            .setValueType(valueType)
            .setIntent(intent));

    // This getter makes a hard copy of the command value, which we need to send the command to the
    // other partition in a side effect. It does not appear to be possible to reuse a single
    // instance for distributing to all partitions in the form of a method parameter, but it's not
    // fully clear why that leads to problems. We suspect that it's because the command value is a
    // mutable object, and it is somehow modified before the side effect is executed. Instead, we
    // have to copy this value for every partition.
    final var commandValue = distributionRecord.getCommandValue();

    sideEffectWriter.appendSideEffect(
        () -> {
          interPartitionCommandSender.sendCommand(
              partition, valueType, intent, distributionKey, commandValue);
          return true;
        });
  }

  /**
   * Acknowledges that a command was distributed to another partition successfully.
   *
   * @param command the command that was distributed
   */
  public <T extends UnifiedRecordValue> void acknowledgeCommand(final TypedRecord<T> command) {
    final long distributionKey = command.getKey();
    final var distributionRecord =
        new CommandDistributionRecord()
            .setPartitionId(currentPartitionId)
            .setValueType(command.getValueType())
            .setIntent(command.getIntent());

    final int receiverPartitionId = Protocol.decodePartitionId(distributionKey);
    sideEffectWriter.appendSideEffect(
        () -> {
          interPartitionCommandSender.sendCommand(
              receiverPartitionId,
              ValueType.COMMAND_DISTRIBUTION,
              CommandDistributionIntent.ACKNOWLEDGE,
              distributionKey,
              distributionRecord);
          return true;
        });
  }
}
