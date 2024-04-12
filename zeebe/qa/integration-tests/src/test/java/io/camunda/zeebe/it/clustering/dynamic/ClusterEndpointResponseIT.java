/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering.dynamic;

import static io.camunda.zeebe.test.util.JsonUtil.assertEquality;

import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class ClusterEndpointResponseIT {
  @TestZeebe static TestStandaloneBroker broker = new TestStandaloneBroker();

  @Test
  void shouldMatchExpectedSerialization() throws IOException, InterruptedException {
    final var uri = broker.actuatorUri("cluster");
    final var request = HttpRequest.newBuilder().uri(uri).build();
    try (final var httpClient = HttpClient.newHttpClient()) {
      final var response = httpClient.send(request, BodyHandlers.ofString());
      assertEquality(
          response.body(),
          """
                        {
                          "version": 1,
                          "brokers": [
                            {
                              "id": 0,
                              "state": "ACTIVE",
                              "version": 0,
                              "lastUpdatedAt": "0000-01-01T00:00:00Z",
                              "partitions": [
                                {
                                  "id": 1,
                                  "state": "ACTIVE",
                                  "priority": 1
                                }
                              ]
                            }
                          ]
                        }""");
    }
  }
}
