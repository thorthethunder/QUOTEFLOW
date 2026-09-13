export type AuthStatus = 'INITIALIZING' | 'AUTHENTICATED' | 'UNAUTHENTICATED';

export type TenantRole = 'OWNER' | 'ADMIN' | 'STAFF';

export interface CurrentUser {
  userId: string;
  businessId: string;
  businessName: string;
  firstName: string;
  lastName: string;
  email: string;
  tenantRole: TenantRole;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  accessTokenExpiresAt: string;
  /** Omitted when HttpOnly cookie transport is used (Phase 4). */
  refreshToken?: string | null;
  user: CurrentUser;
}

export interface RegisterRequest {
  businessName: string;
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  timezone: string;
  currency: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface ApiErrorBody {
  timestamp?: string;
  status?: number;
  code?: string;
  message?: string;
  path?: string;
  correlationId?: string;
  validationErrors?: { field: string; message: string }[];
  details?: Record<string, unknown>;
}
