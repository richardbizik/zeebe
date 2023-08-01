/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.management;

import io.camunda.operate.es.RetryElasticsearchClient;
import io.camunda.operate.schema.IndexSchemaValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ElsIndicesCheck {

  @Autowired
  private IndexSchemaValidator indexSchemaValidator;

  @Autowired
  private RetryElasticsearchClient retryElasticsearchClient;

  public boolean indicesArePresent() {
    return indexSchemaValidator.schemaExists();
  }

  public boolean isHealthy(){
      return retryElasticsearchClient.isHealthy();
  }
}