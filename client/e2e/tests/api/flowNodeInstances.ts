/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import fetch from 'node-fetch';
import {ENDPOINTS} from './endpoints';
import {getCredentials} from './getCredentials';

async function getFlowNodeInstances({
  processInstanceId,
}: {
  processInstanceId: string;
}) {
  const credentials = await getCredentials();

  return await fetch(ENDPOINTS.getFlowNodeInstances(), {
    method: 'POST',
    body: JSON.stringify({
      queries: [{processInstanceId, treePath: processInstanceId}],
    }),
    headers: {
      'Content-Type': 'application/json',
      ...credentials,
    },
  }).then((response) => response.json());
}

export {getFlowNodeInstances};