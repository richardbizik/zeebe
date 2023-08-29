/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {makeObservable, action, observable, override, computed} from 'mobx';
import {DiagramModel} from 'bpmn-moddle';
import {
  fetchProcessInstancesStatistics,
  ProcessInstancesStatisticsDto,
} from 'modules/api/processInstances/fetchProcessInstancesStatistics';
import {getProcessInstancesRequestFilters} from 'modules/utils/filter';
import {logger} from 'modules/logger';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';
import {fetchProcessXML} from 'modules/api/processes/fetchProcessXML';
import {parseDiagramXML} from 'modules/utils/bpmn';
import {getFlowNodes} from 'modules/utils/flowNodes';
import {processInstancesStore} from './processInstances';
import {
  ACTIVE_BADGE,
  CANCELED_BADGE,
  COMPLETED_BADGE,
  INCIDENTS_BADGE,
} from 'modules/bpmn-js/badgePositions';

type State = {
  statistics: ProcessInstancesStatisticsDto[];
  diagramModel: DiagramModel | null;
  xml: string | null;
  status: 'initial' | 'first-fetch' | 'fetching' | 'fetched' | 'error';
};

const DEFAULT_STATE: State = {
  statistics: [],
  diagramModel: null,
  xml: null,
  status: 'initial',
};

const overlayPositions = {
  active: ACTIVE_BADGE,
  incidents: INCIDENTS_BADGE,
  canceled: CANCELED_BADGE,
  completed: COMPLETED_BADGE,
} as const;

class ProcessDiagram extends NetworkReconnectionHandler {
  state: State = {...DEFAULT_STATE};
  processId: ProcessInstanceEntity['processId'] | null = null;

  constructor() {
    super();
    makeObservable(this, {
      state: observable,
      reset: override,
      startFetching: action,
      handleFetchSuccess: action,
      handleFetchStatisticsSuccess: action,
      handleFetchError: action,
      selectableFlowNodes: computed,
      selectableIds: computed,
      flowNodeStates: computed,
      resetState: action,
      overlaysData: computed,
    });
  }

  init = () => {
    processInstancesStore.addCompletedOperationsHandler(() => {
      const filters = getProcessInstancesRequestFilters();
      const processIds = filters?.processIds ?? [];
      if (processIds.length > 0) {
        this.#fetchProcessStatistics();
      }
    });
  };

  fetchProcessDiagram: (
    processId: ProcessInstanceEntity['processId'],
  ) => Promise<void> = this.retryOnConnectionLost(
    async (processId: ProcessInstanceEntity['processId']) => {
      try {
        this.startFetching();

        if (this.processId !== processId) {
          this.processId = processId;
          await this.#fetchProcessXmlAndStatistics(processId);
        } else {
          await this.#fetchProcessStatistics();
        }
      } catch (error) {
        this.handleFetchError(error);
      }
    },
  );

  #fetchProcessStatistics = async () => {
    const response = await fetchProcessInstancesStatistics(
      getProcessInstancesRequestFilters(),
    );

    if (response.isSuccess) {
      this.handleFetchStatisticsSuccess(response.data);
    } else {
      this.handleFetchError();
    }
  };

  #fetchProcessXmlAndStatistics = async (
    processId: ProcessInstanceEntity['processId'],
  ) => {
    const [processXMLResponse, processInstancesStatisticsResponse] =
      await Promise.all([
        fetchProcessXML(processId),
        fetchProcessInstancesStatistics(getProcessInstancesRequestFilters()),
      ]);

    if (
      processXMLResponse.isSuccess &&
      processInstancesStatisticsResponse.isSuccess
    ) {
      const xml = processXMLResponse.data;
      const [diagramModel, statistics] = await Promise.all([
        parseDiagramXML(xml),
        processInstancesStatisticsResponse.data,
      ]);

      this.handleFetchSuccess(xml, diagramModel, statistics);
    } else {
      this.handleFetchError();
    }
  };

  get selectableFlowNodes() {
    return getFlowNodes(this.state.diagramModel?.elementsById);
  }

  get selectableIds() {
    return this.selectableFlowNodes.map(({id}) => id);
  }

  startFetching = () => {
    this.state.status = 'fetching';
  };

  handleFetchSuccess = (
    xml: string,
    diagramModel: DiagramModel,
    statistics: ProcessInstancesStatisticsDto[],
  ) => {
    this.state.xml = xml;
    this.state.diagramModel = diagramModel;
    this.state.statistics = statistics;
    this.state.status = 'fetched';
  };

  handleFetchStatisticsSuccess = (
    statistics: ProcessInstancesStatisticsDto[],
  ) => {
    this.state.statistics = statistics;
    this.state.status = 'fetched';
  };

  handleFetchError = (error?: unknown) => {
    this.state.xml = null;
    this.state.diagramModel = null;
    this.state.status = 'error';

    logger.error('Diagram failed to fetch');
    if (error !== undefined) {
      logger.error(error);
    }
  };

  get flowNodeFilterOptions() {
    return this.selectableFlowNodes
      .map(({id, name}) => ({
        value: id,
        label: name ?? id,
      }))
      .sort((node, nextNode) => {
        const label = node.label.toUpperCase();
        const nextLabel = nextNode.label.toUpperCase();

        if (label < nextLabel) {
          return -1;
        }
        if (label > nextLabel) {
          return 1;
        }

        return 0;
      });
  }

  get overlaysData() {
    return this.flowNodeStates.map(({flowNodeState, count, flowNodeId}) => ({
      payload: {flowNodeState, count},
      type: `statistics-${flowNodeState}`,
      flowNodeId,
      position: overlayPositions[flowNodeState],
    }));
  }

  get flowNodeStates() {
    return this.state.statistics.flatMap((statistics) => {
      const types = ['active', 'incidents', 'canceled', 'completed'] as const;
      return types.reduce<
        {
          flowNodeId: string;
          count: number;
          flowNodeState: FlowNodeState;
        }[]
      >((states, flowNodeState) => {
        const count = statistics[flowNodeState];

        if (count > 0) {
          return [
            ...states,
            {
              flowNodeId: statistics.activityId,
              count,
              flowNodeState,
            },
          ];
        } else {
          return states;
        }
      }, []);
    });
  }

  resetState = () => {
    this.state = {...DEFAULT_STATE};
  };

  reset() {
    this.processId = null;
    super.reset();
    this.resetState();
  }
}

export const processDiagramStore = new ProcessDiagram();