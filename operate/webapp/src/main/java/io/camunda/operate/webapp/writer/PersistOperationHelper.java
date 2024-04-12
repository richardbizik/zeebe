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
package io.camunda.operate.webapp.writer;

import static io.camunda.operate.util.CollectionUtil.getOrDefaultForNullValue;
import static io.camunda.operate.util.ConversionUtils.toLongOrNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationState;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.OperationTemplate;
import io.camunda.operate.store.ListViewStore;
import io.camunda.operate.store.OperationStore;
import io.camunda.operate.webapp.reader.IncidentReader;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.operate.webapp.security.UserService;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Helper class that creates the individual OperationEntity objects from a batch request or
 * operation modify request. A single request might contain multiple modifications or have a query
 * for multiple process instances. An OperationEntity object is created for each process instance to
 * be modified and all the modifications are serialized into a single field.
 */
@Component
public class PersistOperationHelper {
  private final OperationStore operationStore;
  private final IncidentReader incidentReader;
  private final ListViewStore listViewStore;
  private final OperationTemplate operationTemplate;
  private final ObjectMapper objectMapper;
  private final ListViewTemplate listViewTemplate;
  private final UserService userService;

  public PersistOperationHelper(
      final OperationStore operationStore,
      final ListViewStore listViewStore,
      final OperationTemplate operationTemplate,
      final ListViewTemplate listViewTemplate,
      final IncidentReader incidentReader,
      final UserService userService,
      final ObjectMapper objectMapper) {
    this.operationStore = operationStore;
    this.incidentReader = incidentReader;
    this.listViewStore = listViewStore;
    this.operationTemplate = operationTemplate;
    this.objectMapper = objectMapper;
    this.listViewTemplate = listViewTemplate;
    this.userService = userService;
  }

  public int persistOperations(
      final List<ProcessInstanceSource> processInstanceSources,
      final String batchOperationId,
      final CreateBatchOperationRequestDto batchOperationRequest,
      final String incidentId)
      throws PersistenceException {
    final var batchRequest = operationStore.newBatchRequest();
    int operationsCount = 0;
    final OperationType operationType = batchOperationRequest.getOperationType();

    final List<Long> processInstanceKeys =
        processInstanceSources.stream()
            .map(ProcessInstanceSource::getProcessInstanceKey)
            .collect(Collectors.toList());
    Map<Long, List<Long>> incidentKeys = new HashMap<>();
    // prepare map of incident ids per process instance id
    if (operationType.equals(OperationType.RESOLVE_INCIDENT) && incidentId == null) {
      incidentKeys = incidentReader.getIncidentKeysPerProcessInstance(processInstanceKeys);
    }
    final Map<Long, String> processInstanceIdToIndexName;
    try {
      processInstanceIdToIndexName =
          listViewStore.getListViewIndicesForProcessInstances(processInstanceKeys);
    } catch (final IOException e) {
      throw new NotFoundException("Couldn't find index names for process instances.", e);
    }
    for (final ProcessInstanceSource processInstanceSource : processInstanceSources) {
      // Create each entity object and set the appropriate fields based on the operation type
      final Long processInstanceKey = processInstanceSource.getProcessInstanceKey();
      if (operationType.equals(OperationType.RESOLVE_INCIDENT) && incidentId == null) {
        final List<Long> allIncidentKeys = incidentKeys.get(processInstanceKey);
        if (allIncidentKeys != null && !allIncidentKeys.isEmpty()) {
          for (final Long incidentKey : allIncidentKeys) {
            final OperationEntity operationEntity =
                createOperationEntity(processInstanceSource, operationType, batchOperationId)
                    .setIncidentKey(incidentKey);
            batchRequest.add(operationTemplate.getFullQualifiedName(), operationEntity);
            operationsCount++;
          }
        }
      } else {
        final OperationEntity operationEntity =
            createOperationEntity(processInstanceSource, operationType, batchOperationId)
                .setIncidentKey(toLongOrNull(incidentId));
        if (operationType == OperationType.MIGRATE_PROCESS_INSTANCE) {
          try {
            operationEntity.setMigrationPlan(
                objectMapper.writeValueAsString(batchOperationRequest.getMigrationPlan()));
          } catch (final IOException e) {
            throw new PersistenceException(e);
          }
        } else if (operationType == OperationType.MODIFY_PROCESS_INSTANCE) {
          try {
            final ModifyProcessInstanceRequestDto modOp =
                new ModifyProcessInstanceRequestDto()
                    .setProcessInstanceKey(
                        String.valueOf(processInstanceSource.getProcessInstanceKey()))
                    .setModifications(batchOperationRequest.getModifications());
            operationEntity.setModifyInstructions(objectMapper.writeValueAsString(modOp));
          } catch (final IOException e) {
            throw new PersistenceException(e);
          }
        }
        batchRequest.add(operationTemplate.getFullQualifiedName(), operationEntity);
        operationsCount++;
      }

      // Place the update script in the batch request
      final String processInstanceId = String.valueOf(processInstanceKey);
      final String indexForProcessInstance =
          getOrDefaultForNullValue(
              processInstanceIdToIndexName,
              processInstanceKey,
              listViewTemplate.getFullQualifiedName());
      final Map<String, Object> params = Map.of("batchOperationId", batchOperationId);
      final String script =
          "if (ctx._source.batchOperationIds == null){"
              + "ctx._source.batchOperationIds = new String[]{params.batchOperationId};"
              + "} else {"
              + "ctx._source.batchOperationIds.add(params.batchOperationId);"
              + "}";
      batchRequest.updateWithScript(indexForProcessInstance, processInstanceId, script, params);
    }

    batchRequest.execute();
    return operationsCount;
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
}
