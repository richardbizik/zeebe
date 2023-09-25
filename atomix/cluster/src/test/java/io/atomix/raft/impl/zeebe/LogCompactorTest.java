/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.raft.impl.zeebe;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;

import io.atomix.raft.metrics.RaftServiceMetrics;
import io.atomix.raft.snapshot.InMemorySnapshot;
import io.atomix.raft.snapshot.TestSnapshotStore;
import io.atomix.raft.storage.log.RaftLog;
import io.atomix.utils.concurrent.ThreadContext;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

final class LogCompactorTest {
  private ThreadContext threadContext;
  private RaftLog raftLog;
  private LogCompactor compactor;

  @BeforeEach
  void beforeEach() {
    threadContext = Mockito.mock(ThreadContext.class);
    raftLog = Mockito.mock(RaftLog.class);
    // immediately run anything to be executed
    Mockito.doAnswer(
            i -> {
              i.getArgument(0, Runnable.class).run();
              return null;
            })
        .when(threadContext)
        .execute(Mockito.any());

    compactor =
        new LogCompactor(
            threadContext,
            raftLog,
            5,
            new RaftServiceMetrics("1"),
            LoggerFactory.getLogger(getClass()));
  }

  @Test
  void shouldCompact() {
    // given
    compactor.setCompactableIndex(12);

    // when
    compactor.compact();

    // then - should have compacted the log up to the compactable index - the replication threshold
    Mockito.verify(
            raftLog,
            Mockito.times(1)
                .description("should compact up to index minus the replication threshold"))
        .deleteUntil(7);
  }

  @Test
  void shouldNotCompactOnDifferentThread() {
    // given
    Mockito.doThrow(new IllegalStateException("Invalid thread")).when(threadContext).checkThread();
    compactor.setCompactableIndex(12);

    // when
    assertThatCode(compactor::compact).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldCompactBasedOnOldestSnapshot() {
    // given
    final var store = new TestSnapshotStore(new AtomicReference<>());
    InMemorySnapshot.newPersistedSnapshot(10L, 1, 30, store).reserve();
    InMemorySnapshot.newPersistedSnapshot(30L, 1, 30, store);

    // when
    compactor.compactFromSnapshots(store);

    // then
    Mockito.verify(
            raftLog,
            Mockito.times(1)
                .description(
                    "should compact up to lowest snapshot index, minus replication threshold"))
        .deleteUntil(Mockito.eq(5L));
  }
}