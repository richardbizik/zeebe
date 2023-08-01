/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

import {ReactComponent as BaseLogo} from 'modules/components/Icon/logo.svg';
import {Copyright as BasicCopyright} from 'modules/components/Copyright';
import {Input} from './Input';
import {styles} from '@carbon/elements';

const Container = styled.main`
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 128px;
  height: 100%;
  min-width: auto;
  min-height: auto;
`;

const LoginHeader = styled.div`
  align-self: center;
  display: flex;
  flex-direction: column;
  align-items: center;
`;

const Logo = styled(BaseLogo)`
  ${({theme}) => {
    return css`
      margin-bottom: 12px;
      width: 96px;
      height: 33px;
      color: ${theme.colors.text02};
    `;
  }}
`;

const LoginTitle = styled.span`
  ${({theme}) => {
    return css`
      ${styles.productiveHeading04};
      color: ${theme.colors.text02};
    `;
  }}
`;

const LoginForm = styled.form`
  display: flex;
  flex-direction: column;
  margin-top: 53px;
`;

const FormError = styled.div`
  ${({theme}) => {
    return css`
      ${styles.label02};
      color: ${theme.colors.incidentsAndErrors};
      margin-bottom: 10px;
      height: 15px;
    `;
  }}
`;

const Username = styled(Input)`
  margin-bottom: 16px;
`;

const Password = styled(Input)`
  margin-bottom: 32px;
`;

const Copyright = styled(BasicCopyright)`
  margin-top: auto;
  padding-bottom: 8px;
  padding-top: 70px;
  text-align: center;
`;

export {
  Container,
  LoginHeader,
  Logo,
  LoginTitle,
  LoginForm,
  FormError,
  Username,
  Password,
  Copyright,
};