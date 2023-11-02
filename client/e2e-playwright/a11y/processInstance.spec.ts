/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {test} from '../test-fixtures';
import {
  instanceWithIncident,
  mockResponses,
  runningInstance,
} from '../mocks/processInstance.mocks';
import {validateResults} from './validateResults';

test.describe('process detail', () => {
  for (const theme of ['light', 'dark']) {
    test(`have no violations for running instance in ${theme} theme`, async ({
      page,
      commonPage,
      processInstancePage,
      makeAxeBuilder,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          processInstanceDetail: runningInstance.detail,
          flowNodeInstances: runningInstance.flowNodeInstances,
          statistics: runningInstance.statistics,
          sequenceFlows: runningInstance.sequenceFlows,
          variables: runningInstance.variables,
          xml: runningInstance.xml,
          metaData: runningInstance.metaData,
        }),
      );

      await processInstancePage.navigateToProcessInstance({
        id: '1',
        options: {
          waitUntil: 'networkidle',
        },
      });

      // TODO: Enable 'aria-required-parent' and 'list' rules when https://github.com/carbon-design-system/carbon/issues/14944 is implemented and necessary changes are made in our code base.
      const results = await makeAxeBuilder()
        .disableRules(['aria-required-parent', 'list'])
        .analyze();

      validateResults(results);

      await processInstancePage.diagram.getByText(/signal user task/i).click();

      const resultsWithMetadataPopover = await makeAxeBuilder()
        .disableRules(['aria-required-parent', 'list'])
        .analyze();

      validateResults(resultsWithMetadataPopover);
    });

    test(`have no violations for instance with incident in ${theme} theme`, async ({
      page,
      commonPage,
      processInstancePage,
      makeAxeBuilder,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          processInstanceDetail: instanceWithIncident.detail,
          flowNodeInstances: instanceWithIncident.flowNodeInstances,
          statistics: instanceWithIncident.statistics,
          sequenceFlows: instanceWithIncident.sequenceFlows,
          variables: instanceWithIncident.variables,
          xml: instanceWithIncident.xml,
          incidents: instanceWithIncident.incidents,
          metaData: instanceWithIncident.metaData,
        }),
      );

      await processInstancePage.navigateToProcessInstance({
        id: '1',
        options: {
          waitUntil: 'networkidle',
        },
      });

      // TODO: Enable 'aria-required-parent' and 'list' rules when https://github.com/carbon-design-system/carbon/issues/14944 is implemented and necessary changes are made in our code base.
      const results = await makeAxeBuilder()
        .disableRules(['aria-required-parent', 'list'])
        .analyze();

      validateResults(results);

      // edit variable state
      const resultsWithEditVariableState = await makeAxeBuilder()
        .disableRules(['aria-required-parent', 'list'])
        .analyze();

      await page
        .getByRole('button', {name: /edit variable loopCounter/i})
        .click();

      validateResults(resultsWithEditVariableState);
      await page.getByRole('button', {name: /exit edit mode/i}).click();

      // add variable state
      const resultsWithAddVariableState = await makeAxeBuilder()
        .disableRules(['aria-required-parent', 'list'])
        .analyze();

      await processInstancePage.addVariableButton.click();
      validateResults(resultsWithAddVariableState);

      // meta data popover visible
      await processInstancePage.diagram.getByText(/check payment/i).click();

      const resultsWithMetadataPopover = await makeAxeBuilder()
        .disableRules(['aria-required-parent', 'list'])
        .analyze();

      validateResults(resultsWithMetadataPopover);
    });

    test(`have no violations in modification mode in ${theme} theme`, async ({
      page,
      commonPage,
      processInstancePage,
      makeAxeBuilder,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          processInstanceDetail: runningInstance.detail,
          flowNodeInstances: runningInstance.flowNodeInstances,
          statistics: runningInstance.statistics,
          sequenceFlows: runningInstance.sequenceFlows,
          variables: runningInstance.variables,
          xml: runningInstance.xml,
          metaData: runningInstance.metaData,
        }),
      );

      await processInstancePage.navigateToProcessInstance({
        id: '1',
        options: {
          waitUntil: 'networkidle',
        },
      });

      await page.getByRole('button', {name: /modify instance/i}).click();
      const results = await makeAxeBuilder()
        .disableRules(['aria-required-parent', 'list'])
        .analyze();

      validateResults(results);

      await page.getByRole('button', {name: /continue/i}).click();

      const modificationModeResults = await makeAxeBuilder()
        .disableRules(['aria-required-parent', 'list'])
        .analyze();

      await processInstancePage.addVariableButton.click();

      await validateResults(modificationModeResults);
    });
  }
});