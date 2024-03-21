/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.operate.store;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.JacksonConfig;
import io.camunda.operate.Metrics;
import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.connect.ElasticsearchConnector;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.AbstractSchemaIT;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.elasticsearch.ElasticsearchSchemaManager;
import io.camunda.operate.schema.indices.ImportPositionIndex;
import io.camunda.operate.schema.util.ElasticsearchSchemaTestHelper;
import io.camunda.operate.schema.util.TestTemplate;
import io.camunda.operate.store.elasticsearch.ElasticsearchImportStore;
import io.camunda.operate.store.elasticsearch.ElasticsearchTaskStore;
import io.camunda.operate.store.elasticsearch.RetryElasticsearchClient;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(
    classes = {
        ImportPositionIndex.class,
        TestTemplate.class,
        ElasticsearchSchemaManager.class,
        ElasticsearchSchemaTestHelper.class,
        ElasticsearchImportStore.class,
        DatabaseInfo.class,
        OperateProperties.class,
        RetryElasticsearchClient.class,
        ElasticsearchConnector.class,
        ElasticsearchTaskStore.class,
        JacksonConfig.class,
        OperateDateTimeFormatter.class,
        Metrics.class
    })
public class ImportStoreIT extends AbstractSchemaIT {

  @Autowired
  public ImportStore importStore;

  @Autowired public SchemaManager schemaManager;

  @Autowired public ImportPositionIndex importPositionIndex;

  @Autowired public ElasticsearchSchemaTestHelper schemaHelper;

  @MockBean
  public MeterRegistry meterRegistry;

  @BeforeEach
  public void createDefault() {
    schemaManager.createDefaults();
  }

  @AfterEach
  public void dropSchema() {
    schemaHelper.dropSchema();
  }

  @Test
  public void setAndGetConcurrencyMode() {
    schemaManager.createIndex(
        importPositionIndex, "/schema/elasticsearch/create/index/operate-import-position.json");

    assertThat(importStore.getConcurrencyMode()).isFalse();

    importStore.setConcurrencyMode(true);
    assertThat(importStore.getConcurrencyMode()).isTrue();

    importStore.setConcurrencyMode(false);
    assertThat(importStore.getConcurrencyMode()).isFalse();
  }

}