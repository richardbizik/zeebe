import styled from 'styled-components';

import {Colors, themed, themeStyle} from 'modules/theme';
import {Logo as BaseLogo} from 'modules/components/Icon';
import TextInput from 'modules/components/TextInput';
import Button from 'modules/components/Button';

export const Login = styled.div`
  display: flex;
  flex-direction: column;
  margin: 128px auto 0 auto;
  width: 340px;
  font-family: IBMPlexSans;
`;

export const LoginHeader = themed(styled.div`
  align-self: center;
`);

export const Logo = themed(styled(BaseLogo)`
  margin-right: 8px;
  width: 32px;
  height: 32px;
  color: ${themeStyle({
    dark: 'rgba(255, 255, 255, 0.9)',
    light: 'rgba(98, 98, 110, 0.9)'
  })};
`);

export const LoginTitle = themed(styled.span`
  font-family: IBMPlexSans;
  font-size: 36px;
  font-weight: 500;
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiLight06
  })};
  opacity: 0.9;
`);

export const LoginForm = styled.form`
  display: flex;
  flex-direction: column;
  margin-top: 104px;
`;

export const FormError = styled.span`
  font-size: 15px;
  font-weight: 500;
  color: ${Colors.incidentsAndErrors};
  margin-bottom: 10px;
`;

const LoginInput = styled(TextInput)`
  height: 48px;
  padding-left: 8px;
  padding-right: 10px;
  padding-top: 12.6px;
  padding-bottom: 16.4px;

  font-size: 15px;
`;

export const UsernameInput = themed(LoginInput.extend`
  margin-bottom: 16px;
`);

export const PasswordInput = themed(LoginInput.extend`
  margin-bottom: 32px;
`);

export const SubmitButton = themed(styled(Button)`
  height: 48px;
  padding-left: 32px;
  padding-right: 32.2px;
  padding-top: 12px;
  padding-bottom: 13px;

  background-color: ${themeStyle({
    light: Colors.uiLight03
  })};

  font-size: 18px;
  font-weight: 600;
`);
