/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.elasticsearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.ErrorType;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.IncidentState;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.store.IncidentStore;
import io.camunda.operate.store.NotFoundException;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.IdsQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ONLY_RUNTIME;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.util.ElasticsearchUtil.scrollWith;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

@Profile("!opensearch")
@Component
public class ElasticsearchIncidentStore implements IncidentStore {

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchIncidentStore.class);
  public static QueryBuilder ACTIVE_INCIDENT_QUERY = termQuery(IncidentTemplate.STATE, IncidentState.ACTIVE);
  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private IncidentTemplate incidentTemplate;

  @Autowired
  private OperateProperties operateProperties;

  @Override
  public IncidentEntity getIncidentById(Long incidentKey) {
    final IdsQueryBuilder idsQ = idsQuery().addIds(incidentKey.toString());
    final ConstantScoreQueryBuilder query = constantScoreQuery(
        joinWithAnd(idsQ, ACTIVE_INCIDENT_QUERY));
    final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(incidentTemplate, ONLY_RUNTIME)
        .source(new SearchSourceBuilder().query(query));
    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      if (response.getHits().getTotalHits().value == 1) {
        return ElasticsearchUtil.fromSearchHit(response.getHits().getHits()[0].getSourceAsString(), objectMapper, IncidentEntity.class);
      } else if (response.getHits().getTotalHits().value > 1) {
        throw new NotFoundException(String.format("Could not find unique incident with key '%s'.", incidentKey));
      } else {
        throw new NotFoundException(String.format("Could not find incident with key '%s'.", incidentKey));
      }
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining incident: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public List<IncidentEntity> getIncidentsByProcessInstanceKey(Long processInstanceKey) {
    final TermQueryBuilder processInstanceKeyQuery = termQuery(IncidentTemplate.PROCESS_INSTANCE_KEY, processInstanceKey);
    final ConstantScoreQueryBuilder query = constantScoreQuery(
        joinWithAnd(processInstanceKeyQuery, ACTIVE_INCIDENT_QUERY));

    final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(incidentTemplate, ONLY_RUNTIME)
        .source(new SearchSourceBuilder().query(query).sort(IncidentTemplate.CREATION_TIME, SortOrder.ASC));

    try {
      return ElasticsearchUtil.scroll(searchRequest, IncidentEntity.class, objectMapper, esClient);
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining all incidents: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public Map<Long, List<Long>> getIncidentKeysPerProcessInstance(List<Long> processInstanceKeys) {
    final QueryBuilder processInstanceKeysQuery = constantScoreQuery(
        joinWithAnd(termsQuery(IncidentTemplate.PROCESS_INSTANCE_KEY, processInstanceKeys),
            ACTIVE_INCIDENT_QUERY));
    final int batchSize = operateProperties.getElasticsearch().getBatchSize();

    final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(incidentTemplate, ONLY_RUNTIME)
        .source(new SearchSourceBuilder()
            .query(processInstanceKeysQuery)
            .fetchSource(IncidentTemplate.PROCESS_INSTANCE_KEY, null)
            .size(batchSize));

    Map<Long, List<Long>> result = new HashMap<>();
    try {
      scrollWith(searchRequest, esClient, searchHits -> {
        for (SearchHit hit : searchHits.getHits()) {
          CollectionUtil.addToMap(result, Long.valueOf(hit.getSourceAsMap().get(IncidentTemplate.PROCESS_INSTANCE_KEY).toString()), Long.valueOf(hit.getId()));
        }
      }, null, null);
      return result;
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining all incidents: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public List<IncidentEntity> getIncidentsWithErrorTypesFor(String treePath, List<Map<ErrorType,Long>> errorTypes) {
    final TermQueryBuilder processInstanceQuery = termQuery(IncidentTemplate.TREE_PATH, treePath);

    final String errorTypesAggName = "errorTypesAgg";

    final TermsAggregationBuilder errorTypesAgg = terms(errorTypesAggName).field(IncidentTemplate.ERROR_TYPE).size(ErrorType.values().length).order(BucketOrder.key(true));

    final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(incidentTemplate, ONLY_RUNTIME).source(
        new SearchSourceBuilder().query(constantScoreQuery(joinWithAnd(processInstanceQuery, ACTIVE_INCIDENT_QUERY)))
            .aggregation(errorTypesAgg));

    try {
      return ElasticsearchUtil.scroll(searchRequest, IncidentEntity.class, objectMapper, esClient, null,
          aggs -> ((Terms) aggs.get(errorTypesAggName)).getBuckets().forEach(b -> {
            ErrorType errorType = ErrorType.valueOf(b.getKeyAsString());
            errorTypes.add(Map.of(errorType, b.getDocCount()));
          }));
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining incidents: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }
}