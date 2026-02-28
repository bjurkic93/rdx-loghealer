export const environment = {
  production: false,
  apiUrl: '/api/v1',
  oauth2: {
    authorizationServer: 'https://auth.reddia-x.com',
    loginUrl: 'https://login.reddia-x.com',
    clientId: 'rdx-loghealer',
    redirectUri: 'http://localhost:4206/auth/callback',
    scopes: 'openid profile email'
  }
};
