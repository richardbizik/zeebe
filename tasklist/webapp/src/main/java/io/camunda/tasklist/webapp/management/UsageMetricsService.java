/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.management;

import io.camunda.tasklist.store.TaskMetricsStore;
import io.camunda.tasklist.webapp.management.dto.UsageMetricDTO;
import io.camunda.tasklist.webapp.management.dto.UsageMetricQueryDTO;
import io.camunda.tasklist.webapp.rest.InternalAPIErrorController;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;

@Component
@RestControllerEndpoint(id = "usage-metrics")
public class UsageMetricsService extends InternalAPIErrorController {

  @Autowired private TaskMetricsStore taskMetricsStore;

  /**
   * Retrieve list of unique assigned users in a given period
   *
   * <p>Sample Usage:
   * <HOST>:<PORT>/actuator/usage-metrics/assignees?startTime=2012-12-19T06:01:17.171Z&endTime=2012-12-29T06:01:17.171Z
   *
   * <p>TODO: Return UsageMetricDTO as a response - For now this is just the initial setup
   */
  @GetMapping(
      value = "/assignees",
      produces = {MediaType.APPLICATION_JSON_VALUE})
  public UsageMetricDTO retrieveUniqueAssignedUsers(UsageMetricQueryDTO query) {
    final List<String> assignees =
        taskMetricsStore.retrieveDistinctAssigneesBetweenDates(
            query.getStartTime(), query.getEndTime());
    return new UsageMetricDTO(assignees);
  }
}
