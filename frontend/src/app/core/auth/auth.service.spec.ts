import { TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { AuthService } from './auth.service';
import { authInterceptor } from './auth.interceptor';
import { AuthResponse, CurrentUser } from './auth.models';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  const user: CurrentUser = {
    userId: 'u1',
    businessId: 'b1',
    businessName: 'Acme',
    firstName: 'Ada',
    lastName: 'Lovelace',
    email: 'ada@example.com',
    tenantRole: 'OWNER',
  };

  const authResponse: AuthResponse = {
    accessToken: 'access-token-1',
    tokenType: 'Bearer',
    expiresIn: 900,
    accessTokenExpiresAt: new Date().toISOString(),
    user,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('logs in and stores access token in memory', () => {
    let result: CurrentUser | undefined;
    service.login({ email: 'ada@example.com', password: 'passphrase-long' }).subscribe((u) => {
      result = u;
    });

    http.expectOne('/api/v1/auth/csrf').flush({ token: 'x' });
    http.expectOne('/api/v1/auth/login').flush(authResponse);
    http.expectOne('/api/v1/me').flush(user);

    expect(result).toEqual(user);
    expect(service.getAccessToken()).toBe('access-token-1');
    expect(service.status()).toBe('AUTHENTICATED');
  });

  it('registers a business owner', () => {
    service
      .register({
        businessName: 'Acme',
        firstName: 'Ada',
        lastName: 'Lovelace',
        email: 'ada@example.com',
        password: 'passphrase-long',
        timezone: 'Asia/Kolkata',
        currency: 'INR',
      })
      .subscribe();

    http.expectOne('/api/v1/auth/csrf').flush({});
    http.expectOne('/api/v1/auth/register').flush(authResponse);
    http.expectOne('/api/v1/me').flush(user);
    expect(service.isAuthenticated()).toBeTrue();
  });

  it('restores session via refresh cookie', () => {
    let ok = false;
    service.initializeSession().subscribe((v) => (ok = v));

    http.expectOne('/api/v1/auth/csrf').flush({});
    http.expectOne('/api/v1/auth/refresh').flush(authResponse);
    http.expectOne('/api/v1/me').flush(user);

    expect(ok).toBeTrue();
    expect(service.status()).toBe('AUTHENTICATED');
  });

  it('marks unauthenticated when refresh fails during init', () => {
    let ok = true;
    service.initializeSession().subscribe((v) => (ok = v));

    http.expectOne('/api/v1/auth/csrf').flush({});
    http.expectOne('/api/v1/auth/refresh').flush({ message: 'no' }, { status: 401, statusText: 'Unauthorized' });

    expect(ok).toBeFalse();
    expect(service.status()).toBe('UNAUTHENTICATED');
    expect(service.getAccessToken()).toBeNull();
  });

  it('clears local state on logout even if network fails', () => {
    service.login({ email: 'ada@example.com', password: 'passphrase-long' }).subscribe();
    http.expectOne('/api/v1/auth/csrf').flush({});
    http.expectOne('/api/v1/auth/login').flush(authResponse);
    http.expectOne('/api/v1/me').flush(user);

    service.logout().subscribe();
    http.expectOne('/api/v1/auth/csrf').flush({});
    http.expectOne('/api/v1/auth/logout').flush({}, { status: 0, statusText: 'Unknown' });

    expect(service.getAccessToken()).toBeNull();
    expect(service.status()).toBe('UNAUTHENTICATED');
  });
});

describe('authInterceptor', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('attaches bearer token and refreshes once on 401', () => {
    (service as unknown as { accessToken: string }).accessToken = 'old-token';

    let body: unknown;
    service.loadMe().subscribe((u) => (body = u));

    const first = http.expectOne('/api/v1/me');
    expect(first.request.headers.get('Authorization')).toBe('Bearer old-token');
    first.flush({ message: 'expired' }, { status: 401, statusText: 'Unauthorized' });

    http.expectOne('/api/v1/auth/csrf').flush({});
    http.expectOne('/api/v1/auth/refresh').flush({
      accessToken: 'new-token',
      tokenType: 'Bearer',
      expiresIn: 900,
      accessTokenExpiresAt: new Date().toISOString(),
      user: {
        userId: 'u1',
        businessId: 'b1',
        businessName: 'Acme',
        firstName: 'Ada',
        lastName: 'Lovelace',
        email: 'ada@example.com',
        tenantRole: 'OWNER',
      },
    });
    const meFromRefresh = http.expectOne('/api/v1/me');
    expect(meFromRefresh.request.headers.get('Authorization')).toBe('Bearer new-token');
    meFromRefresh.flush({
      userId: 'u1',
      businessId: 'b1',
      businessName: 'Acme',
      firstName: 'Ada',
      lastName: 'Lovelace',
      email: 'ada@example.com',
      tenantRole: 'OWNER',
    });

    const retry = http.expectOne('/api/v1/me');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer new-token');
    expect(retry.request.headers.get('X-Auth-Retry')).toBe('1');
    retry.flush({
      userId: 'u1',
      businessId: 'b1',
      businessName: 'Acme',
      firstName: 'Ada',
      lastName: 'Lovelace',
      email: 'ada@example.com',
      tenantRole: 'OWNER',
    });

    expect(body).toBeTruthy();
  });
});
