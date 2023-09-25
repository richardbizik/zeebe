/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.opensearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.ListViewStore;
import io.camunda.operate.store.NotFoundException;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.store.opensearch.dsl.RequestDSL;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.and;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.constantScore;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.exists;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.ids;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.not;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.sourceInclude;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.operate.util.CollectionUtil.map;
import static io.camunda.operate.util.CollectionUtil.toSafeListOfStrings;
import static io.camunda.operate.util.ExceptionHelper.withIOException;


@Conditional(OpensearchCondition.class)
@Component
public class OpensearchListViewStore implements ListViewStore {

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Autowired
  private RichOpenSearchClient richOpenSearchClient;

  @Autowired
  private OperateProperties operateProperties;

  @Override
  public Map<Long, String> getListViewIndicesForProcessInstances(List<Long> processInstanceIds) throws IOException {
    var searchRequestBuilder = searchRequestBuilder(listViewTemplate, RequestDSL.QueryType.ALL)
      .query(ids(toSafeListOfStrings(map(processInstanceIds, Object::toString))));

    final Map<Long, String> processInstanceId2IndexName = withIOException( () ->
      richOpenSearchClient.doc().searchHits(searchRequestBuilder, Void.class)
        .values()
        .stream()
        .collect(Collectors.toMap(
          hit -> Long.valueOf(hit.id()),
          Hit::index
        ))
    );

    if(processInstanceId2IndexName.isEmpty()){
      throw new NotFoundException(String.format("Process instances %s doesn't exists.", processInstanceIds));
    }

    return processInstanceId2IndexName;
  }

  @Override
  public String findProcessInstanceTreePathFor(long processInstanceKey) {
    record Result(String treePath){}
    final RequestDSL.QueryType queryType = operateProperties.getImporter().isReadArchivedParents() ?
      RequestDSL.QueryType.ALL :
      RequestDSL.QueryType.ONLY_RUNTIME;
    var searchRequestBuilder = searchRequestBuilder(listViewTemplate, queryType)
      .query(term(ListViewTemplate.KEY, processInstanceKey))
      .source(sourceInclude(ListViewTemplate.TREE_PATH));

    List<Hit<Result>> hits = richOpenSearchClient.doc().search(searchRequestBuilder, Result.class).hits().hits();

    if (hits.size() > 0) {
      return hits.get(0).source().treePath();
    }
    return null;
  }

  @Override
  public List<Long> getProcessInstanceKeysWithEmptyProcessVersionFor(Long processDefinitionKey) {
    var searchRequestBuilder = searchRequestBuilder(listViewTemplate.getAlias())
      .query(
        constantScore(
          and(
            term(ListViewTemplate.PROCESS_KEY, processDefinitionKey),
            not(exists(ListViewTemplate.PROCESS_VERSION))
          )
        )
      )
      .source(s -> s.fetch(false));

    return richOpenSearchClient.doc().searchHits(searchRequestBuilder, Void.class)
      .values()
      .stream()
      .map(hit -> Long.valueOf(hit.id()))
      .toList();
  }
}