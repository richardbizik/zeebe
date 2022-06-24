/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup;

import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.PersistedSnapshotStore;
import io.camunda.zeebe.util.sched.Actor;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackupActor extends Actor {

  private static final Logger LOG = LoggerFactory.getLogger("BACKUP");
  private final BackupStore backupStore;

  private final PersistedSnapshotStore snapshotStore;
  private final LogCompactor logCompactor;

  private final Path raftStorageDirectory;

  public BackupActor(
      final LocalFileSystemBackupStore backupStore,
      final PersistedSnapshotStore snapshotStore,
      final LogCompactor logCompactor,
      final Path raftStorageDirectory) {
    this.backupStore = backupStore;
    this.snapshotStore = snapshotStore;
    this.logCompactor = logCompactor;
    this.raftStorageDirectory = raftStorageDirectory;
  }

  public void takeBackup(final long checkpointId, final long checkpointPosition) {
    actor.run(
        () -> {
          LOG.info("Received backup command {} {}", checkpointId, checkpointPosition);

          // if there are concurrent backups.
          logCompactor.disableCompaction();
          final var snapshotFuture = snapshotStore.lockLatestSnapshot();
          actor.runOnCompletion(
              snapshotFuture,
              (snapshot, error) -> {
                if (error == null) {
                  // TODO: Handle case when snapshot does not exists
                  if (snapshot.getSnapshotId().getProcessedPosition() < checkpointPosition) {
                    final List<Path> segmentFiles =
                        Arrays.stream(raftStorageDirectory.toFile().listFiles())
                            .map(File::toPath)
                            .filter(p -> p.getFileName().toString().endsWith(".log"))
                            .toList();

                    startBackup(checkpointId, checkpointPosition, snapshot, segmentFiles);
                  } else {
                    // TODO: log error
                    // mark backup as failed
                    snapshotStore.unlockSnapshot(snapshot);
                    logCompactor.enableCompaction();
                  }
                }
              });
        });
  }

  private void startBackup(
      final long checkpointId,
      final long checkpointPosition,
      final PersistedSnapshot snapshot,
      final List<Path> segmentFiles) {

    final BackupMetaData backupMetadata = new BackupMetaData(checkpointId, checkpointPosition);
    final Backup backup;
    try {
      backup = backupStore.newBackup(backupMetadata);

      final var snapshotBackedUp = backup.backupSnapshot(snapshot);
      final var segmentsBackedUp = backup.backupSegments(segmentFiles);
      actor.runOnCompletion(
          List.of(snapshotBackedUp, segmentsBackedUp),
          error -> {
            if (error != null) {
              onBackupFailed(backup, error);
            } else {
              onBackupCompleted(backup);
            }
          });

    } catch (final IOException e) {
      // TODO: log
    }
  }

  private void onBackupCompleted(final Backup localFileSystemBackup) {

    try {
      logCompactor.enableCompaction();
      localFileSystemBackup.markAsCompleted();
    } catch (final IOException e) {
      // TODO
    }
  }

  private void onBackupFailed(final Backup localFileSystemBackup, final Throwable error) {
    try {
      logCompactor.enableCompaction();
      localFileSystemBackup.markAsFailed();
    } catch (final IOException e) {
      // TODO:
    }
  }
}
