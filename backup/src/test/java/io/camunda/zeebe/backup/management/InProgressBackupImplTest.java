/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.scheduler.testing.TestActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.PersistedSnapshotStore;
import io.camunda.zeebe.snapshots.SnapshotException.SnapshotNotFoundException;
import io.camunda.zeebe.snapshots.SnapshotMetadata;
import io.camunda.zeebe.snapshots.SnapshotReservation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InProgressBackupImplTest {

  private static final Path CHECKSUM_PATH = Path.of("snapshot-root/checksum");
  @TempDir Path snapshotDir;
  @Mock PersistedSnapshotStore snapshotStore;
  InProgressBackupImpl inProgressBackup;
  private final TestConcurrencyControl concurrencyControl = new TestConcurrencyControl();

  @BeforeEach
  void setup() {
    inProgressBackup =
        new InProgressBackupImpl(
            snapshotStore, new BackupIdentifierImpl(1, 1, 1), 10, 1, concurrencyControl);
  }

  @Test
  void shouldCompleteFutureWhenNoSnapshotExists() {
    // given
    setAvailableSnapshots(Set.of());

    // when
    final var future = inProgressBackup.findValidSnapshot();

    // then
    assertThat(future).succeedsWithin(Duration.ofMillis(100));
  }

  @Test
  void shouldCompleteFutureWhenValidSnapshotFound() {
    // given
    final var validSnapshot = snapshotWith(1L, 5L);
    final var invalidSnapshot = snapshotWith(8L, 20L);
    final Set<PersistedSnapshot> snapshots = Set.of(validSnapshot, invalidSnapshot);
    setAvailableSnapshots(snapshots);

    // when
    final var future = inProgressBackup.findValidSnapshot();

    // then
    assertThat(future).succeedsWithin(Duration.ofMillis(100));
  }

  @Test
  void shouldFailFutureWhenSnapshotIsPastCheckpointPosition() {
    // given - checkpointPosition < processedPosition <  followupPosition
    final var invalidSnapshot = snapshotWith(11L, 20L);
    setAvailableSnapshots(Set.of(invalidSnapshot));

    // when - then
    // when
    final var future = inProgressBackup.findValidSnapshot();

    // then
    assertThat(future)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withRootCauseInstanceOf(SnapshotNotFoundException.class);
  }

  @Test
  void shouldFailFutureWhenSnapshotOverlapsWithCheckpoint() {
    // given - processedPosition < checkpointPosition < followupPosition
    final var invalidSnapshot = snapshotWith(8L, 20L);
    setAvailableSnapshots(Set.of(invalidSnapshot));

    // when
    final var future = inProgressBackup.findValidSnapshot();

    // then
    assertThat(future)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withRootCauseInstanceOf(SnapshotNotFoundException.class);
  }

  @Test
  void shouldReserveSnapshotWhenValidSnapshotExists(
      @Mock final SnapshotReservation snapshotReservation) {
    // given
    final var validSnapshot = snapshotWith(1L, 5L);
    onReserve(validSnapshot, snapshotReservation);
    setAvailableSnapshots(Set.of(validSnapshot));

    final var backup = collectBackupContents();

    // then
    assertThat(backup.descriptor().snapshotId()).isEqualTo(validSnapshot.getId());
    verify(validSnapshot).reserve();
  }

  @Test
  void shouldNotFailReservationWhenNoSnapshotExists() {
    // given
    setAvailableSnapshots(Set.of());

    inProgressBackup.findValidSnapshot().join();

    // when
    final var future = inProgressBackup.reserveSnapshot();

    // then
    assertThat(future).succeedsWithin(Duration.ofMillis(100));
  }

  @Test
  void shouldReserveLatestSnapshotWhenMoreThanOneValidSnapshotExists(
      @Mock final SnapshotReservation snapshotReservation) {
    // given
    final var oldValidSnapshot = snapshotWith(1L, 5L);
    final var latestValidSnapshot = snapshotWith(2L, 6L);
    onReserve(oldValidSnapshot, snapshotReservation);
    onReserve(latestValidSnapshot, snapshotReservation);
    setAvailableSnapshots(Set.of(oldValidSnapshot, latestValidSnapshot));

    // when
    final var backup = collectBackupContents();

    // then
    assertThat(backup.descriptor().snapshotId()).isEqualTo(latestValidSnapshot.getId());
    verify(latestValidSnapshot).reserve();
  }

  @Test
  void shouldFailWhenSnapshotCannotBeReserved() {
    // given
    final var validSnapshot = snapshotWith(1L, 5L);
    failOnReserve(validSnapshot);
    final Set<PersistedSnapshot> snapshots = Set.of(validSnapshot);
    setAvailableSnapshots(snapshots);

    inProgressBackup.findValidSnapshot().join();
    final var future = inProgressBackup.reserveSnapshot();

    // then
    assertThat(future)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withMessageContaining("Reservation Failed");
  }

  @Test
  void shouldReserveNextSnapshotWhenOneSnapshotFails(
      @Mock final SnapshotReservation snapshotReservation) {
    // given
    final var oldValidSnapshot = snapshotWith(1L, 5L);
    final var latestValidSnapshot = snapshotWith(2L, 6L);

    onReserve(oldValidSnapshot, snapshotReservation);
    failOnReserve(latestValidSnapshot);

    final Set<PersistedSnapshot> snapshots = Set.of(oldValidSnapshot, latestValidSnapshot);
    setAvailableSnapshots(snapshots);

    // when
    final var backup = collectBackupContents();

    // then
    assertThat(backup.descriptor().snapshotId()).isEqualTo(oldValidSnapshot.getId());
    verify(oldValidSnapshot).reserve();
  }

  @Test
  void shouldReleaseReservationWhenClosed(@Mock final SnapshotReservation snapshotReservation) {
    // given
    final var validSnapshot = snapshotWith(1L, 5L);
    onReserve(validSnapshot, snapshotReservation);
    final Set<PersistedSnapshot> snapshots = Set.of(validSnapshot);
    setAvailableSnapshots(snapshots);

    inProgressBackup.findValidSnapshot().join();
    inProgressBackup.reserveSnapshot().join();

    // when
    inProgressBackup.close();

    // then
    verify(snapshotReservation).release();
  }

  @Test
  void shouldCollectSnapshotFilesWhenValidSnapshotIsReserved(
      @Mock final SnapshotReservation snapshotReservation) throws IOException {
    // given
    final var validSnapshot = snapshotWith(1L, 5L);
    onReserve(validSnapshot, snapshotReservation);
    setAvailableSnapshots(Set.of(validSnapshot));

    // create snapshot files
    final var file1 = Files.createFile(snapshotDir.resolve("file1"));
    final var file2 = Files.createFile(snapshotDir.resolve("file2"));

    // when
    final var backup = collectBackupContents();

    // then
    assertThat(backup.snapshot().namedFiles())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("file1", file1, "file2", file2, "checksum", CHECKSUM_PATH));
  }

  @Test
  void shouldHaveEmptySnapshotFilesWhenNoSnapshot() {
    // given
    setAvailableSnapshots(Set.of());

    // when
    final var backup = collectBackupContents();

    // then
    assertThat(backup.snapshot().namedFiles()).isEmpty();
  }

  private void setAvailableSnapshots(final Set<PersistedSnapshot> snapshots) {
    when(snapshotStore.getAvailableSnapshots())
        .thenReturn(TestActorFuture.completedFuture(snapshots));
  }

  private Backup collectBackupContents() {
    inProgressBackup.findValidSnapshot().join();
    inProgressBackup.reserveSnapshot().join();
    inProgressBackup.findSegmentFiles().join();
    inProgressBackup.findSnapshotFiles().join();
    return inProgressBackup.createBackup();
  }

  private PersistedSnapshot snapshotWith(
      final long processedPosition, final long followUpPosition) {
    final PersistedSnapshot snapshot = mock(PersistedSnapshot.class);
    final SnapshotMetadata snapshotMetadata = mock(SnapshotMetadata.class);
    when(snapshotMetadata.processedPosition()).thenReturn(processedPosition);
    lenient().when(snapshotMetadata.lastFollowupEventPosition()).thenReturn(followUpPosition);

    when(snapshot.getMetadata()).thenReturn(snapshotMetadata);
    lenient()
        .when(snapshot.getId())
        .thenReturn(String.format("%d-%d", processedPosition, followUpPosition));
    lenient().when(snapshot.getCompactionBound()).thenReturn(processedPosition);

    lenient().when(snapshot.getPath()).thenReturn(snapshotDir);

    lenient().when(snapshot.getChecksumPath()).thenReturn(CHECKSUM_PATH);
    return snapshot;
  }

  private void onReserve(
      final PersistedSnapshot snapshot, final SnapshotReservation snapshotReservation) {
    lenient()
        .when(snapshot.reserve())
        .thenReturn(TestActorFuture.completedFuture(snapshotReservation));
  }

  private void failOnReserve(final PersistedSnapshot snapshot) {
    lenient()
        .when(snapshot.reserve())
        .thenReturn(TestActorFuture.failedFuture(new RuntimeException("Reservation Failed")));
  }
}
