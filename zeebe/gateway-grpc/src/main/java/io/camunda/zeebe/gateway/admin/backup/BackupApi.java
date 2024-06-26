/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.admin.backup;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface BackupApi {

  /**
   * Triggers backup on all partitions. Returned future is completed successfully after all
   * partitions have processed the request. Returned future fails if the request was not processed
   * by at least one partition.
   *
   * <p>TODO: check if it makes more sense to return a {@link java.util.concurrent.Future} if we're
   * always blocking on the result and never combining.
   *
   * @param backupId the id of the backup to be taken
   * @return the backupId
   */
  CompletionStage<Long> takeBackup(long backupId);

  /**
   * Returns the status of the backup. The future fails if the request was not processed by at least
   * one partition.
   *
   * <p>TODO: check if it makes more sense to return a {@link java.util.concurrent.Future} if we're
   * always blocking on the result and never combining.
   *
   * @return the status of the backup
   */
  CompletionStage<BackupStatus> getStatus(long backupId);

  /**
   * @return a list of available backups
   */
  CompletionStage<List<BackupStatus>> listBackups();

  /**
   * Deletes the backup with the given id
   *
   * @param backupId id of the backup to delete
   */
  CompletionStage<Void> deleteBackup(long backupId);
}
