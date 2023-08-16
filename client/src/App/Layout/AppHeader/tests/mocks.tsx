/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {MemoryRouter} from 'react-router-dom';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {LocationLog} from 'modules/utils/LocationLog';
import {LegacyPaths} from 'modules/legacyRoutes';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => (
  <ThemeProvider>
    <MemoryRouter initialEntries={[LegacyPaths.dashboard()]}>
      {children}
      <LocationLog />
    </MemoryRouter>
  </ThemeProvider>
);

export {Wrapper};