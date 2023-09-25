/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {FlowNodeInstance} from 'modules/stores/flowNodeInstance';

const mockStartNode: FlowNodeInstance = {
  type: 'START_EVENT',
  state: 'ACTIVE',
  id: '2251799813685264',
  flowNodeId: 'StartEvent_1',
  startDate: '2021-03-10T12:24:18.387+0000',
  endDate: '2021-03-10T12:24:18.399+0000',
  treePath: '',
  sortValues: [],
};

const mockStartEventBusinessObject = {
  id: 'StartEvent1',
  name: 'Start Event',
  $type: 'bpmn:StartEvent',
} as const;

export {mockStartNode, mockStartEventBusinessObject};