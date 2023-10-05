/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store;

import org.elasticsearch.client.tasks.GetTaskResponse;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface TaskStore {
  List<String> getRunningReindexTasksIdsFor(final String fromIndex,final String toIndex) throws IOException;
}