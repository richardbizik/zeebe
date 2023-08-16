/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {To} from 'react-router-dom';
import {
  DecisionInstanceFilters,
  ProcessInstanceFilters,
} from 'modules/utils/filter';

type RouterState = {
  referrer?: string;
};

const LegacyPaths = {
  login() {
    return '/legacy/login';
  },
  dashboard() {
    return '/legacy/';
  },
  processes() {
    return '/legacy/processes';
  },
  processInstance(processInstanceId: string = ':processInstanceId') {
    return `/legacy/processes/${processInstanceId}`;
  },
  decisions() {
    return '/legacy/decisions';
  },
  decisionInstance(decisionInstanceId: string = ':decisionInstanceId') {
    return `/legacy/decisions/${decisionInstanceId}`;
  },
} as const;

const LegacyLocations = {
  processes(filters?: ProcessInstanceFilters): To {
    const params = new URLSearchParams();

    if (filters !== undefined) {
      Object.entries(filters).forEach(([key, value]) => {
        params.set(key, value as string);
      });
    } else {
      params.set('active', 'true');
      params.set('incidents', 'true');
    }

    return {
      pathname: LegacyPaths.processes(),
      search: params.toString(),
    };
  },
  decisions(filters?: DecisionInstanceFilters): To {
    const params = new URLSearchParams();

    if (filters !== undefined) {
      Object.entries(filters).forEach(([key, value]) => {
        params.set(key, value as string);
      });
    } else {
      params.set('evaluated', 'true');
      params.set('failed', 'true');
    }

    return {
      pathname: LegacyPaths.decisions(),
      search: params.toString(),
    };
  },
} as const;

export {LegacyPaths, LegacyLocations};
export type {RouterState};