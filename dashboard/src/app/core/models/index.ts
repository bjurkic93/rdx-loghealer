export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  roles: string[];
}

export interface TokenResponse {
  access_token: string;
  refresh_token?: string;
  token_type: string;
  expires_in: number;
}

export function isSuperAdmin(user: User): boolean {
  return user.roles.includes('SUPER_ADMIN');
}

export * from './codefix.model';
