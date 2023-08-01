/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {observer} from 'mobx-react';
import {StatusMessage} from 'modules/components/StatusMessage';
import {decisionInstanceDetailsStore} from 'modules/stores/decisionInstanceDetails';
import {JSONViewer} from './JSONViewer/index';
import {SpinnerSkeleton} from 'modules/components/SpinnerSkeleton';
import {Container} from './styled';

const Result: React.FC = observer(() => {
  const {
    state: {status, decisionInstance},
  } = decisionInstanceDetailsStore;

  return (
    <Container>
      {status === 'initial' && (
        <SpinnerSkeleton data-testid="result-loading-spinner" />
      )}
      {status === 'fetched' &&
        decisionInstance !== null &&
        decisionInstance.state !== 'FAILED' && (
          <JSONViewer
            data-testid="results-json-viewer"
            value={decisionInstance.result ?? '{}'}
          />
        )}
      {status === 'fetched' && decisionInstance?.state === 'FAILED' && (
        <StatusMessage variant="default">
          No result available because the evaluation failed
        </StatusMessage>
      )}
      {status === 'error' && (
        <StatusMessage variant="error">Data could not be fetched</StatusMessage>
      )}
    </Container>
  );
});

export {Result};