/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.store.elasticsearch;

import static io.camunda.operate.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.ElasticsearchUtil;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticsearchCondition.class)
@Scope(SCOPE_PROTOTYPE)
public class ElasticsearchBatchRequest implements BatchRequest {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchBatchRequest.class);

  private final BulkRequest bulkRequest = new BulkRequest();

  @Autowired private ObjectMapper objectMapper;

  @Autowired private OperateProperties operateProperties;

  @Autowired private RestHighLevelClient esClient;

  @Override
  public BatchRequest add(final String index, final OperateEntity entity)
      throws PersistenceException {
    return addWithId(index, entity.getId(), entity);
  }

  @Override
  public BatchRequest addWithId(final String index, final String id, final OperateEntity entity)
      throws PersistenceException {
    LOGGER.debug("Add index request for index {} id {} and entity {} ", index, id, entity);
    try {
      bulkRequest.add(
          new IndexRequest(index)
              .id(id)
              .source(objectMapper.writeValueAsString(entity), XContentType.JSON));
    } catch (final JsonProcessingException e) {
      throw new PersistenceException(
          String.format(
              "Error preparing the query to index [%s] of entity type [%s] ",
              entity.getClass().getName(), entity),
          e);
    }
    return this;
  }

  @Override
  public BatchRequest addWithRouting(
      final String index, final OperateEntity entity, final String routing)
      throws PersistenceException {
    LOGGER.debug(
        "Add index request with routing {} for index {} and entity {} ", routing, index, entity);
    try {
      bulkRequest.add(
          new IndexRequest(index)
              .id(entity.getId())
              .source(objectMapper.writeValueAsString(entity), XContentType.JSON)
              .routing(routing));
    } catch (final JsonProcessingException e) {
      throw new PersistenceException(
          String.format(
              "Error preparing the query to index [%s] of entity type [%s] with routing",
              entity.getClass().getName(), entity),
          e);
    }
    return this;
  }

  @Override
  public BatchRequest upsert(
      final String index,
      final String id,
      final OperateEntity entity,
      final Map<String, Object> updateFields)
      throws PersistenceException {
    LOGGER.debug(
        "Add upsert request for index {} id {} entity {} and update fields {}",
        index,
        id,
        entity,
        updateFields);
    try {
      bulkRequest.add(
          new UpdateRequest()
              .index(index)
              .id(id)
              .doc(
                  objectMapper.readValue(
                      objectMapper.writeValueAsString(updateFields), HashMap.class)) // empty
              .upsert(objectMapper.writeValueAsString(entity), XContentType.JSON));
    } catch (final JsonProcessingException e) {
      throw new PersistenceException(
          String.format(
              "Error preparing the query to upsert [%s] of entity type [%s]",
              entity.getClass().getName(), entity),
          e);
    }
    return this;
  }

  @Override
  public BatchRequest upsertWithRouting(
      final String index,
      final String id,
      final OperateEntity entity,
      final Map<String, Object> updateFields,
      final String routing)
      throws PersistenceException {
    LOGGER.debug(
        "Add upsert request with routing {} for index {} id {} entity {} and update fields {}",
        routing,
        index,
        id,
        entity,
        updateFields);
    try {
      bulkRequest.add(
          new UpdateRequest()
              .index(index)
              .id(id)
              .doc(
                  objectMapper.readValue(
                      objectMapper.writeValueAsString(updateFields), HashMap.class))
              .upsert(objectMapper.writeValueAsString(entity), XContentType.JSON)
              .routing(routing)
              .retryOnConflict(UPDATE_RETRY_COUNT));
    } catch (final JsonProcessingException e) {
      throw new PersistenceException(
          String.format(
              "Error preparing the query to upsert [%s] of entity type [%s] with routing",
              entity.getClass().getName(), entity),
          e);
    }
    return this;
  }

  @Override
  public BatchRequest upsertWithScriptAndRouting(
      final String index,
      final String id,
      final OperateEntity entity,
      final String script,
      final Map<String, Object> parameters,
      final String routing)
      throws PersistenceException {
    LOGGER.debug(
        "Add upsert request with routing {} for index {} id {} entity {} and script {} with parameters {} ",
        routing,
        index,
        id,
        entity,
        script,
        parameters);
    try {
      bulkRequest.add(
          new UpdateRequest()
              .index(index)
              .id(id)
              .script(getScriptWithParameters(script, parameters))
              .upsert(objectMapper.writeValueAsString(entity), XContentType.JSON)
              .routing(routing)
              .retryOnConflict(UPDATE_RETRY_COUNT));
    } catch (final JsonProcessingException e) {
      throw new PersistenceException(
          String.format(
              "Error preparing the query to upsert [%s] of entity type [%s] with script and routing",
              entity.getClass().getName(), entity),
          e);
    }
    return this;
  }

  @Override
  public BatchRequest update(
      final String index, final String id, final Map<String, Object> updateFields)
      throws PersistenceException {
    LOGGER.debug(
        "Add update request for index {} id {} and update fields {}", index, id, updateFields);
    try {
      bulkRequest.add(
          new UpdateRequest()
              .index(index)
              .id(id)
              .doc(
                  objectMapper.readValue(
                      objectMapper.writeValueAsString(updateFields), HashMap.class))
              .retryOnConflict(UPDATE_RETRY_COUNT));
    } catch (final JsonProcessingException e) {
      throw new PersistenceException(
          String.format(
              "Error preparing the query to update index [%s] document with id [%s]", index, id),
          e);
    }
    return this;
  }

  @Override
  public BatchRequest update(final String index, final String id, final OperateEntity entity)
      throws PersistenceException {
    try {
      return update(
          index,
          id,
          objectMapper.readValue(objectMapper.writeValueAsString(entity), HashMap.class));
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public BatchRequest updateWithScript(
      final String index,
      final String id,
      final String script,
      final Map<String, Object> parameters)
      throws PersistenceException {
    LOGGER.debug("Add update with script request for index {} id {} ", index, id);
    final UpdateRequest updateRequest =
        new UpdateRequest()
            .index(index)
            .id(id)
            .script(getScriptWithParameters(script, parameters))
            .retryOnConflict(UPDATE_RETRY_COUNT);
    bulkRequest.add(updateRequest);
    return this;
  }

  @Override
  public void execute() throws PersistenceException {
    LOGGER.debug("Execute batchRequest with {} requests", bulkRequest.requests().size());
    ElasticsearchUtil.processBulkRequest(
        esClient, bulkRequest, operateProperties.getElasticsearch().getBulkRequestMaxSizeInBytes());
  }

  @Override
  public void executeWithRefresh() throws PersistenceException {
    LOGGER.debug(
        "Execute batchRequest with {} requests and refresh", bulkRequest.requests().size());
    ElasticsearchUtil.processBulkRequest(
        esClient,
        bulkRequest,
        true,
        operateProperties.getElasticsearch().getBulkRequestMaxSizeInBytes());
  }

  private Script getScriptWithParameters(final String script, final Map<String, Object> parameters)
      throws PersistenceException {
    try {
      return new Script(
          ScriptType.INLINE,
          Script.DEFAULT_SCRIPT_LANG,
          script,
          objectMapper.readValue(objectMapper.writeValueAsString(parameters), HashMap.class));
    } catch (final IOException e) {
      throw new PersistenceException(e);
    }
  }
}
