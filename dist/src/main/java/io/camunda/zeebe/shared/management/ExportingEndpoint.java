/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.shared.management;

import io.camunda.zeebe.gateway.admin.exporting.ExportingControlApi;
import java.util.concurrent.CompletionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.Selector.Match;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.stereotype.Component;

@Component
@WebEndpoint(id = "exporting")
public final class ExportingEndpoint {
  static final String PAUSE = "pause";
  static final String SOFT_PAUSE = "softPause";
  static final String RESUME = "resume";
  final ExportingControlApi exportingService;

  @Autowired
  public ExportingEndpoint(final ExportingControlApi exportingService) {
    this.exportingService = exportingService;
  }

  @WriteOperation
  public WebEndpointResponse<?> post(@Selector(match = Match.SINGLE) final String operationKey) {
    try {
      final var result =
          switch (operationKey) {
            case PAUSE -> exportingService.pauseExporting();
            case SOFT_PAUSE -> exportingService.softPauseExporting();
            case RESUME -> exportingService.resumeExporting();
            default -> throw new UnsupportedOperationException();
          };
      result.join();
      return new WebEndpointResponse<>(WebEndpointResponse.STATUS_NO_CONTENT);
    } catch (final CompletionException e) {
      return new WebEndpointResponse<>(
          e.getCause(), WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR);
    } catch (final Exception e) {
      return new WebEndpointResponse<>(e, WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR);
    }
  }
}
