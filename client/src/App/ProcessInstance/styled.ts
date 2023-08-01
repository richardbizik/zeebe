/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css, ThemedInterpolationFunction} from 'styled-components';
import {CmButton} from '@camunda-cloud/common-ui-react';
import {styles} from '@carbon/elements';
import {zModificationFrame} from 'modules/constants/componentHierarchy';
import {MODIFICATION_HEADER_HEIGHT} from 'modules/constants';

type ContainerProps = {
  $isModificationOutlineVisible: boolean;
  $isBreadcrumbVisible: boolean;
};

const frameStyles: ThemedInterpolationFunction = ({theme}) => {
  return css`
    content: '';
    position: absolute;
    height: 100%;
    width: 4px;
    background-color: ${theme.colors.primaryButton04};
    z-index: ${zModificationFrame};
  `;
};

const Container = styled.main<ContainerProps>`
  ${({theme, $isModificationOutlineVisible, $isBreadcrumbVisible}) => {
    return css`
      display: grid;
      grid-template-rows: ${`${
        $isModificationOutlineVisible ? `${MODIFICATION_HEADER_HEIGHT}px` : ''
      } ${$isBreadcrumbVisible ? '30px' : ''} 56px 1fr`};
      height: 100%;
      position: relative;

      ${$isModificationOutlineVisible &&
      css`
        border: 4px solid ${theme.colors.primaryButton04};
        border-right: none;
        border-left: none;
        &:before {
          ${frameStyles}
          left: 0;
        }
        &:after {
          ${frameStyles}
          right: 0;
        }
      `}
    `;
  }}
`;

const PanelContainer = styled.div`
  overflow: hidden;
  height: 100%;
  display: flex;
  flex-direction: column;
`;

const BottomPanel = styled.div`
  display: flex;
  flex-direction: row;
  height: 100%;
`;

const ModificationHeader = styled.div`
  ${({theme}) => {
    return css`
      background-color: ${theme.colors.primaryButton04};
      color: ${theme.colors.white};
      padding: 5px 0px 9px 16px;
      ${styles.heading01}
    `;
  }}
`;

const ModificationFooter = styled.div`
  ${({theme}) => {
    const colors = theme.colors.processInstance.modifications.footer;
    return css`
      display: flex;
      justify-content: space-between;
      background-color: ${colors.backgroundColor};
      padding: 8px 24px 7px;
      border-top: 1px solid ${theme.colors.borderColor};
      box-shadow: ${theme.shadows.modificationMode.footer};
    `;
  }}
`;

const Button = styled(CmButton)`
  margin-left: 15px;
`;

const Buttons = styled.div`
  display: flex;
  justify-content: flex-end;
  flex: 1;
`;

export {
  Container,
  PanelContainer,
  BottomPanel,
  ModificationHeader,
  ModificationFooter,
  Button,
  Buttons,
};