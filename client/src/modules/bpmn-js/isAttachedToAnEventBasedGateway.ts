/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';

const isAttachedToAnEventBasedGateway = (node: BusinessObject) => {
  const incomingSequenceFlows = node.incoming ?? [];

  return incomingSequenceFlows.some((sequenceFlow) => {
    return sequenceFlow.sourceRef?.$type === 'bpmn:EventBasedGateway';
  });
};

export {isAttachedToAnEventBasedGateway};
