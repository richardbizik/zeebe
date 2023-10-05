/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.webapp.api.v1.dao.ProcessDefinitionDao;
import io.camunda.operate.webapp.api.v1.entities.ProcessDefinition;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchProcessDefinitionDao implements ProcessDefinitionDao {
  @Override
  public void buildPaging(Query<ProcessDefinition> query, SearchSourceBuilder searchSourceBuilder) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ProcessDefinition byKey(Long key) throws APIException {
    throw new UnsupportedOperationException();
  }

  @Override
  public String xmlByKey(Long key) throws APIException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Results<ProcessDefinition> search(Query<ProcessDefinition> query) throws APIException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void buildSorting(Query<ProcessDefinition> query, String uniqueKey, SearchSourceBuilder searchSourceBuilder) {
    throw new UnsupportedOperationException();
  }
}