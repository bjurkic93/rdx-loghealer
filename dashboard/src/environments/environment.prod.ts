export const environment = {
  production: true,
  apiUrl: '/api/v1',
  oauth2: {
    authorizationServer: 'https://auth.reddia-x.com',
    loginUrl: 'https://login.reddia-x.com',
    clientId: 'rdx-loghealer',
    redirectUri: 'https://loghealer.reddia-x.com/auth/callback',
    scopes: 'openid profile email'
  }
};
