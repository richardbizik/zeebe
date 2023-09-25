/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {makeObservable, action, observable, computed, override} from 'mobx';

import {
  fetchGroupedProcesses,
  ProcessDto,
  ProcessVersionDto,
} from 'modules/api/processes/fetchGroupedProcesses';
import {getProcessInstanceFilters} from 'modules/utils/filter';
import {getSearchString} from 'modules/utils/getSearchString';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';
import {sortOptions} from 'modules/utils/sortOptions';
import {DEFAULT_TENANT, PERMISSIONS} from 'modules/constants';

type Process = ProcessDto & {key: string};
type State = {
  processes: Process[];
  status: 'initial' | 'first-fetch' | 'fetching' | 'fetched' | 'fetch-error';
};

const INITIAL_STATE: State = {
  processes: [],
  status: 'initial',
};

const generateProcessKey = (bpmnProcessId: string, tenantId?: string) => {
  return `{${bpmnProcessId}}-{${tenantId ?? DEFAULT_TENANT}}`;
};

class Processes extends NetworkReconnectionHandler {
  state: State = INITIAL_STATE;
  retryCount: number = 0;
  retryProcessesFetchTimeout: NodeJS.Timeout | null = null;

  constructor() {
    super();
    makeObservable(this, {
      state: observable,
      startFetching: action,
      handleFetchError: action,
      handleFetchSuccess: action,
      processes: computed,
      versionsByProcessAndTenant: computed,
      reset: override,
      isInitialLoadComplete: computed,
    });
  }

  fetchProcesses = this.retryOnConnectionLost(async (tenantId?: string) => {
    this.startFetching();

    const {process, tenant: tenantFromURL} = getProcessInstanceFilters(
      getSearchString(),
    );

    const tenant = tenantId ?? tenantFromURL;

    const response = await fetchGroupedProcesses(
      tenant === 'all' ? undefined : tenant,
    );

    if (response.isSuccess) {
      const processes = response.data;

      if (
        process !== undefined &&
        processes.filter((item) => item.bpmnProcessId === process).length === 0
      ) {
        this.handleRefetch(processes);
      } else {
        this.resetRetryProcessesFetch();
        this.handleFetchSuccess(processes);
      }
    } else {
      this.handleFetchError();
    }
  });

  startFetching = () => {
    if (this.state.status === 'initial') {
      this.state.status = 'first-fetch';
    } else {
      this.state.status = 'fetching';
    }
  };

  handleFetchError = () => {
    this.state.status = 'fetch-error';
  };

  get isInitialLoadComplete() {
    if (['initial', 'first-fetch'].includes(this.state.status)) {
      return false;
    }

    return this.state.status !== 'fetching' || this.retryCount === 0;
  }

  handleFetchSuccess = (processes: ProcessDto[]) => {
    this.state.processes = processes.map((process) => {
      return {
        key: generateProcessKey(process.bpmnProcessId, process.tenantId),
        ...process,
      };
    });
    this.state.status = 'fetched';
  };

  handleRefetch = (processes: ProcessDto[]) => {
    if (this.retryCount < 3) {
      this.retryCount += 1;

      this.retryProcessesFetchTimeout = setTimeout(() => {
        this.fetchProcesses();
      }, 5000);
    } else {
      this.resetRetryProcessesFetch();
      this.handleFetchSuccess(processes);
    }
  };

  get processes() {
    return this.state.processes
      .map(({key, tenantId, bpmnProcessId, name}) => ({
        id: key,
        label: name ?? bpmnProcessId,
        bpmnProcessId,
        tenantId,
      }))
      .sort(sortOptions);
  }

  get versionsByProcessAndTenant(): {
    [key: string]: ProcessVersionDto[];
  } {
    return this.state.processes.reduce<{
      [key: string]: ProcessVersionDto[];
    }>(
      (versionsByProcessAndTenant, {key, processes}) => ({
        ...versionsByProcessAndTenant,
        [key]: processes
          .slice()
          .sort(
            (process, nextProcess) => process.version - nextProcess.version,
          ),
      }),
      {},
    );
  }

  getProcessId = ({
    process,
    tenant,
    version,
  }: {
    process?: string;
    tenant?: string;
    version?: string;
  } = {}) => {
    if (process === undefined || version === undefined || version === 'all') {
      return undefined;
    }

    const processVersions =
      this.versionsByProcessAndTenant[generateProcessKey(process, tenant)] ??
      [];

    return processVersions.find(
      (processVersion) => processVersion.version === parseInt(version),
    )?.id;
  };

  resetRetryProcessesFetch = () => {
    if (this.retryProcessesFetchTimeout !== null) {
      clearTimeout(this.retryProcessesFetchTimeout);
    }

    this.retryCount = 0;
  };

  getProcess = ({
    bpmnProcessId,
    tenantId,
  }: {
    bpmnProcessId?: string;
    tenantId?: string;
  }) => {
    if (bpmnProcessId === undefined) {
      return undefined;
    }

    return this.state.processes.find(
      (process) =>
        process.bpmnProcessId === bpmnProcessId &&
        process.tenantId === (tenantId ?? DEFAULT_TENANT),
    );
  };

  getPermissions = (processId?: string, tenantId?: string) => {
    if (!window.clientConfig?.resourcePermissionsEnabled) {
      return PERMISSIONS;
    }

    if (processId === undefined) {
      return [];
    }

    return this.getProcess({bpmnProcessId: processId, tenantId})?.permissions;
  };

  reset() {
    super.reset();
    this.state = INITIAL_STATE;
    this.resetRetryProcessesFetch();
  }
}

const processesStore = new Processes();

export {processesStore, generateProcessKey};