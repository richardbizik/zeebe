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
package io.camunda.operate.webapp.opensearch.writer;

import static io.camunda.operate.entities.OperationType.ADD_VARIABLE;
import static io.camunda.operate.entities.OperationType.UPDATE_VARIABLE;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.QueryType.ALL;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.QueryType.ONLY_RUNTIME;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.operate.util.CollectionUtil.getOrDefaultForNullValue;
import static io.camunda.operate.util.ConversionUtils.toLongOrNull;
import static io.camunda.operate.util.ExceptionHelper.withOperateRuntimeException;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationState;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.OperationTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.ListViewStore;
import io.camunda.operate.store.OperationStore;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.store.opensearch.dsl.RequestDSL;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.opensearch.OpenSearchQueryHelper;
import io.camunda.operate.webapp.reader.IncidentReader;
import io.camunda.operate.webapp.reader.OperationReader;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.operate.webapp.security.UserService;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import io.camunda.operate.webapp.writer.PersistOperationHelper;
import io.camunda.operate.webapp.writer.ProcessInstanceSource;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchBatchOperationWriter
    implements io.camunda.operate.webapp.writer.BatchOperationWriter {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(OpensearchBatchOperationWriter.class);

  @Autowired private IncidentReader incidentReader;

  @Autowired private OperateProperties operateProperties;

  @Autowired private RichOpenSearchClient richOpenSearchClient;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private OperationTemplate operationTemplate;

  @Autowired private OperationReader operationReader;

  @Autowired private ListViewTemplate listViewTemplate;

  @Autowired private BatchOperationTemplate batchOperationTemplate;

  @Autowired private UserService userService;

  @Autowired private ProcessInstanceReader processInstanceReader;

  @Autowired(required = false)
  private PermissionsService permissionsService;

  @Autowired private OperationStore operationStore;

  @Autowired private ListViewStore listViewStore;

  @Autowired private OpenSearchQueryHelper openSearchQueryHelper;

  @Autowired private PersistOperationHelper persistOperationHelper;

  /**
   * Finds operation, which are scheduled or locked with expired timeout, in the amount of
   * configured batch size, and locks them.
   *
   * @return list of locked operations
   * @throws PersistenceException
   */
  @Override
  public List<OperationEntity> lockBatch() throws PersistenceException {
    final String workerId = operateProperties.getOperationExecutor().getWorkerId();
    final long lockTimeout = operateProperties.getOperationExecutor().getLockTimeout();
    final int batchSize = operateProperties.getOperationExecutor().getBatchSize();

    // select process instances, which has scheduled operations, or locked with expired
    // lockExpirationTime
    final List<OperationEntity> operationEntities = operationReader.acquireOperations(batchSize);

    final BatchRequest batchRequest = operationStore.newBatchRequest();

    // lock the operations
    for (final OperationEntity operation : operationEntities) {
      // lock operation: update workerId, state, lockExpirationTime
      operation.setState(OperationState.LOCKED);
      operation.setLockOwner(workerId);
      operation.setLockExpirationTime(OffsetDateTime.now().plus(lockTimeout, ChronoUnit.MILLIS));

      // TODO decide with index refresh
      batchRequest.update(operationTemplate.getFullQualifiedName(), operation.getId(), operation);
    }
    // TODO decide with index refresh
    batchRequest.executeWithRefresh();
    LOGGER.debug("{} operations locked", operationEntities.size());
    return operationEntities;
  }

  @Override
  public void updateOperation(final OperationEntity operation) throws PersistenceException {
    operationStore.update(operation, true);
  }

  /**
   * Schedule operations based of process instance query.
   *
   * @param batchOperationRequest
   * @return
   */
  @Override
  public BatchOperationEntity scheduleBatchOperation(
      final CreateBatchOperationRequestDto batchOperationRequest) {
    LOGGER.debug("Creating batch operation: operationRequest [{}]", batchOperationRequest);
    try {
      // add batch operation with unique id
      final BatchOperationEntity batchOperation =
          createBatchOperationEntity(
              batchOperationRequest.getOperationType(), batchOperationRequest.getName());

      // Creates an OperationEntity object for each process instance that will be changed and
      // sends to the batch processor to be executed asynchronously
      final var operationsCount = addOperations(batchOperationRequest, batchOperation);

      // update counts
      batchOperation.setOperationsTotalCount(operationsCount);

      if (operationsCount == 0) {
        batchOperation.setEndDate(OffsetDateTime.now());
      }
      operationStore.add(batchOperation);
      return batchOperation;
    } catch (final InvalidRequestException ex) {
      throw ex;
    } catch (final Exception ex) {
      throw new OperateRuntimeException(
          String.format("Exception occurred, while scheduling operation: %s", ex.getMessage()), ex);
    }
  }

  /**
   * Schedule operation for single process instance.
   *
   * @param processInstanceKey
   * @param operationRequest
   * @return
   */
  @Override
  public BatchOperationEntity scheduleSingleOperation(
      final long processInstanceKey, final CreateOperationRequestDto operationRequest) {
    LOGGER.debug(
        "Creating operation: processInstanceKey [{}], operation type [{}]",
        processInstanceKey,
        operationRequest.getOperationType());
    try {
      // add batch operation with unique id
      final BatchOperationEntity batchOperation =
          createBatchOperationEntity(
              operationRequest.getOperationType(), operationRequest.getName());

      // add single operations
      final var batchRequest = operationStore.newBatchRequest();
      int operationsCount = 0;

      String noOperationsReason = null;

      final OperationType operationType = operationRequest.getOperationType();
      if (operationType.equals(OperationType.RESOLVE_INCIDENT)
          && operationRequest.getIncidentId() == null) {
        final List<IncidentEntity> allIncidents =
            incidentReader.getAllIncidentsByProcessInstanceKey(processInstanceKey);
        if (allIncidents.size() == 0) {
          // nothing to schedule
          // TODO delete batch operation entity
          batchOperation.setEndDate(OffsetDateTime.now());
          noOperationsReason = "No incidents found.";
        } else {
          for (final IncidentEntity incident : allIncidents) {
            final OperationEntity operationEntity =
                createOperationEntity(processInstanceKey, operationType, batchOperation.getId());
            operationEntity.setIncidentKey(incident.getKey());
            batchRequest.add(operationTemplate.getFullQualifiedName(), operationEntity);
            operationsCount++;
          }
        }
      } else if (Set.of(UPDATE_VARIABLE, ADD_VARIABLE).contains(operationType)) {
        final OperationEntity operationEntity =
            createOperationEntity(processInstanceKey, operationType, batchOperation.getId())
                .setScopeKey(toLongOrNull(operationRequest.getVariableScopeId()))
                .setVariableName(operationRequest.getVariableName())
                .setVariableValue(operationRequest.getVariableValue());
        batchRequest.add(operationTemplate.getFullQualifiedName(), operationEntity);
        operationsCount++;
      } else {
        final OperationEntity operationEntity =
            createOperationEntity(processInstanceKey, operationType, batchOperation.getId())
                .setIncidentKey(toLongOrNull(operationRequest.getIncidentId()));
        batchRequest.add(operationTemplate.getFullQualifiedName(), operationEntity);
        operationsCount++;
      }
      // update process instance
      final String processInstanceId = String.valueOf(processInstanceKey);
      final var processInstanceIdToIndexName =
          listViewStore.getListViewIndicesForProcessInstances(List.of(processInstanceKey));
      final String indexForProcessInstance =
          getOrDefaultForNullValue(
              processInstanceIdToIndexName,
              processInstanceKey,
              listViewTemplate.getFullQualifiedName());

      final String script =
          "if (ctx._source.batchOperationIds == null){"
              + "ctx._source.batchOperationIds = new String[]{params.batchOperationId};"
              + "} else {"
              + "ctx._source.batchOperationIds.add(params.batchOperationId);"
              + "}";
      batchRequest.updateWithScript(
          indexForProcessInstance,
          processInstanceId,
          script,
          Map.of("batchOperationId", batchOperation.getId()));

      // update instances_count and operations_count of batch operation
      batchOperation.setOperationsTotalCount(operationsCount);
      batchOperation.setInstancesCount(1);
      // persist batch operation
      batchRequest.add(batchOperationTemplate.getFullQualifiedName(), batchOperation);

      batchRequest.execute();
      return batchOperation;
    } catch (final io.camunda.operate.store.NotFoundException nfe) {
      throw new OperateRuntimeException(
          String.format("Exception occurred, while scheduling operation: %s", nfe.getMessage()),
          new NotFoundException(nfe.getMessage()));
    } catch (final Exception ex) {
      throw new OperateRuntimeException(
          String.format("Exception occurred, while scheduling operation: %s", ex.getMessage()), ex);
    }
  }

  @Override
  public BatchOperationEntity scheduleModifyProcessInstance(
      final ModifyProcessInstanceRequestDto modifyRequest) {
    LOGGER.debug(
        "Creating modify process instance operation: processInstanceKey [{}]",
        modifyRequest.getProcessInstanceKey());
    try {
      final int operationsCount = modifyRequest.getModifications().size();
      final Long processInstanceKey = Long.parseLong(modifyRequest.getProcessInstanceKey());
      final BatchOperationEntity batchOperation =
          createBatchOperationEntity(OperationType.MODIFY_PROCESS_INSTANCE, null)
              .setOperationsTotalCount(operationsCount)
              .setInstancesCount(1);

      final OperationEntity operationEntity =
          createOperationEntity(
                  processInstanceKey, OperationType.MODIFY_PROCESS_INSTANCE, batchOperation.getId())
              .setModifyInstructions(objectMapper.writeValueAsString(modifyRequest));

      final var batchRequest = operationStore.newBatchRequest();

      final var processInstanceIdToIndexName =
          listViewStore.getListViewIndicesForProcessInstances(List.of(processInstanceKey));
      final var processInstanceId = String.valueOf(processInstanceKey);
      final var indexForProcessInstance =
          getOrDefaultForNullValue(
              processInstanceIdToIndexName,
              processInstanceKey,
              listViewTemplate.getFullQualifiedName());
      final Map<String, Object> params = Map.of("batchOperationId", batchOperation.getId());
      final var script =
          "if (ctx._source.batchOperationIds == null){"
              + "ctx._source.batchOperationIds = new String[]{params.batchOperationId};"
              + "} else {"
              + "ctx._source.batchOperationIds.add(params.batchOperationId);"
              + "}";

      batchRequest
          .add(operationTemplate.getFullQualifiedName(), operationEntity)
          .updateWithScript(indexForProcessInstance, processInstanceId, script, params)
          .add(batchOperationTemplate.getFullQualifiedName(), batchOperation);

      batchRequest.execute();
      return batchOperation;
    } catch (final Exception ex) {
      throw new OperateRuntimeException(
          String.format(
              "Exception occurred, while scheduling 'modify process instance' operation: %s",
              ex.getMessage()),
          ex);
    }
  }

  @Override
  public BatchOperationEntity scheduleDeleteDecisionDefinition(
      final DecisionDefinitionEntity decisionDefinitionEntity) {
    final Long decisionDefinitionKey = decisionDefinitionEntity.getKey();
    final OperationType operationType = OperationType.DELETE_DECISION_DEFINITION;

    // Create batch operation
    final String batchOperationName =
        String.format(
            "%s - Version %s",
            decisionDefinitionEntity.getName(), decisionDefinitionEntity.getVersion());
    final BatchOperationEntity batchOperation =
        createBatchOperationEntity(operationType, batchOperationName)
            .setOperationsTotalCount(1)
            .setInstancesCount(0);

    // Create operation
    final OperationEntity operationEntity = new OperationEntity();
    operationEntity.generateId();
    operationEntity.setDecisionDefinitionKey(decisionDefinitionKey);
    operationEntity.setType(operationType);
    operationEntity.setState(OperationState.SCHEDULED);
    operationEntity.setBatchOperationId(batchOperation.getId());
    operationEntity.setUsername(userService.getCurrentUser().getUsername());

    // Create request
    try {
      final var batchRequest =
          operationStore
              .newBatchRequest()
              .add(operationTemplate.getFullQualifiedName(), operationEntity)
              .add(batchOperationTemplate.getFullQualifiedName(), batchOperation);
      batchRequest.execute();
      return batchOperation;
    } catch (final Exception ex) {
      throw new OperateRuntimeException(
          String.format(
              "Exception occurred, while scheduling 'delete decision definition' operation: %s",
              ex.getMessage()),
          ex);
    }
  }

  @Override
  public BatchOperationEntity scheduleDeleteProcessDefinition(final ProcessEntity processEntity) {
    final Long processDefinitionKey = processEntity.getKey();
    final OperationType operationType = OperationType.DELETE_PROCESS_DEFINITION;

    // Create batch operation
    final String batchOperationName =
        String.format("%s - Version %s", processEntity.getName(), processEntity.getVersion());
    final BatchOperationEntity batchOperation =
        createBatchOperationEntity(operationType, batchOperationName)
            .setOperationsTotalCount(1)
            .setInstancesCount(0);

    // Create operation
    final OperationEntity operationEntity = new OperationEntity();
    operationEntity.generateId();
    operationEntity.setProcessDefinitionKey(processDefinitionKey);
    operationEntity.setType(operationType);
    operationEntity.setState(OperationState.SCHEDULED);
    operationEntity.setBatchOperationId(batchOperation.getId());
    operationEntity.setUsername(userService.getCurrentUser().getUsername());

    // Create request
    try {
      final var batchRequest =
          operationStore
              .newBatchRequest()
              .add(operationTemplate.getFullQualifiedName(), operationEntity)
              .add(batchOperationTemplate.getFullQualifiedName(), batchOperation);
      batchRequest.execute();
      return batchOperation;
    } catch (final Exception ex) {
      throw new OperateRuntimeException(
          String.format(
              "Exception occurred, while scheduling 'delete process definition' operation: %s",
              ex.getMessage()),
          ex);
    }
  }

  private int addOperations(
      final CreateBatchOperationRequestDto batchOperationRequest,
      final BatchOperationEntity batchOperation)
      throws IOException {
    final int batchSize = operateProperties.getElasticsearch().getBatchSize();
    Query query =
        openSearchQueryHelper.createProcessInstancesQuery(batchOperationRequest.getQuery());
    if (permissionsService != null) {
      final IdentityPermission permission =
          batchOperationRequest.getOperationType() == OperationType.DELETE_PROCESS_INSTANCE
              ? IdentityPermission.DELETE_PROCESS_INSTANCE
              : IdentityPermission.UPDATE_PROCESS_INSTANCE;
      final var allowed = permissionsService.getProcessesWithPermission(permission);
      final var permissionQuery =
          allowed.isAll()
              ? matchAll()
              : stringTerms(ListViewTemplate.BPMN_PROCESS_ID, allowed.getIds());
      query = constantScore(withTenantCheck(and(query, permissionQuery)));
    }
    final RequestDSL.QueryType queryType =
        batchOperationRequest.getOperationType() == OperationType.DELETE_PROCESS_INSTANCE
            ? ALL
            : ONLY_RUNTIME;
    final var searchRequestBuilder =
        searchRequestBuilder(listViewTemplate, queryType)
            .query(query)
            .size(batchSize)
            .source(
                sourceInclude(
                    OperationTemplate.PROCESS_INSTANCE_KEY,
                    OperationTemplate.PROCESS_DEFINITION_KEY,
                    OperationTemplate.BPMN_PROCESS_ID));

    final AtomicInteger operationsCount = new AtomicInteger();

    final Consumer<List<Hit<ProcessInstanceSource>>> hitsConsumer =
        hits ->
            withOperateRuntimeException(
                () -> {
                  final List<ProcessInstanceSource> processInstanceSources =
                      hits.stream().map(Hit::source).toList();
                  return operationsCount.addAndGet(
                      persistOperationHelper.persistOperations(
                          processInstanceSources,
                          batchOperation.getId(),
                          batchOperationRequest,
                          null));
                });

    final Consumer<HitsMetadata<ProcessInstanceSource>> hitsMetadataConsumer =
        hitsMeta -> {
          validateTotalHits(hitsMeta);
          batchOperation.setInstancesCount((int) hitsMeta.total().value());
        };

    richOpenSearchClient
        .doc()
        .unsafeScrollWith(
            searchRequestBuilder,
            hitsConsumer,
            hitsMetadataConsumer,
            ProcessInstanceSource.class,
            false);

    return operationsCount.get();
  }

  private void validateTotalHits(final HitsMetadata<?> hitsMeta) {
    final long totalHits = hitsMeta.total().value();
    final Long maxSize = operateProperties.getBatchOperationMaxSize();
    if (maxSize != null && totalHits > operateProperties.getBatchOperationMaxSize()) {
      throw new InvalidRequestException(
          String.format(
              "Too many process instances are selected for batch operation. Maximum possible amount: %s",
              maxSize));
    }
  }

  private BatchOperationEntity createBatchOperationEntity(
      final OperationType operationType, final String name) {
    final BatchOperationEntity batchOperationEntity = new BatchOperationEntity();
    batchOperationEntity.generateId();
    batchOperationEntity.setType(operationType);
    batchOperationEntity.setName(name);
    batchOperationEntity.setStartDate(OffsetDateTime.now());
    batchOperationEntity.setUsername(userService.getCurrentUser().getUsername());
    return batchOperationEntity;
  }

  private OperationEntity createOperationEntity(
      final Long processInstanceKey,
      final OperationType operationType,
      final String batchOperationId) {
    final ProcessInstanceSource processInstanceSource =
        new ProcessInstanceSource().setProcessInstanceKey(processInstanceKey);
    final Optional<ProcessInstanceForListViewEntity> optionalProcessInstance =
        tryGetProcessInstance(processInstanceKey);
    optionalProcessInstance.ifPresent(
        processInstance ->
            processInstanceSource
                .setProcessDefinitionKey(processInstance.getProcessDefinitionKey())
                .setBpmnProcessId(processInstance.getBpmnProcessId()));

    return createOperationEntity(processInstanceSource, operationType, batchOperationId);
  }

  private OperationEntity createOperationEntity(
      final ProcessInstanceSource processInstanceSource,
      final OperationType operationType,
      final String batchOperationId) {

    final OperationEntity operationEntity = new OperationEntity();
    operationEntity.generateId();
    operationEntity.setProcessInstanceKey(processInstanceSource.getProcessInstanceKey());
    operationEntity.setProcessDefinitionKey(processInstanceSource.getProcessDefinitionKey());
    operationEntity.setBpmnProcessId(processInstanceSource.getBpmnProcessId());
    operationEntity.setType(operationType);
    operationEntity.setState(OperationState.SCHEDULED);
    operationEntity.setBatchOperationId(batchOperationId);
    operationEntity.setUsername(userService.getCurrentUser().getUsername());

    return operationEntity;
  }

  private Optional<ProcessInstanceForListViewEntity> tryGetProcessInstance(
      final Long processInstanceKey) {
    ProcessInstanceForListViewEntity processInstance = null;
    try {
      processInstance = processInstanceReader.getProcessInstanceByKey(processInstanceKey);
    } catch (final OperateRuntimeException ex) {
      LOGGER.error(
          String.format(
              "Failed to get process instance for key %s: %s",
              processInstanceKey, ex.getMessage()));
    }
    return Optional.ofNullable(processInstance);
  }
}
