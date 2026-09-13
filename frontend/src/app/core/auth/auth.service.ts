import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import {
  Observable,
  catchError,
  finalize,
  map,
  of,
  shareReplay,
  switchMap,
  tap,
  throwError,
} from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AuthResponse,
  AuthStatus,
  CurrentUser,
  LoginRequest,
  RegisterRequest,
  ApiErrorBody,
} from './auth.models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  /** Short-lived access JWT — memory only (not localStorage). */
  private accessToken: string | null = null;

  private readonly statusSignal = signal<AuthStatus>('INITIALIZING');
  private readonly userSignal = signal<CurrentUser | null>(null);

  readonly status = this.statusSignal.asReadonly();
  readonly currentUser = this.userSignal.asReadonly();
  readonly isAuthenticated = computed(() => this.statusSignal() === 'AUTHENTICATED');
  readonly isInitializing = computed(() => this.statusSignal() === 'INITIALIZING');

  private refreshInFlight$: Observable<string> | null = null;
  private initInFlight$: Observable<boolean> | null = null;

  getAccessToken(): string | null {
    return this.accessToken;
  }

  initializeSession(): Observable<boolean> {
    if (this.statusSignal() !== 'INITIALIZING' && this.initInFlight$ == null) {
      return of(this.statusSignal() === 'AUTHENTICATED');
    }
    if (this.initInFlight$) {
      return this.initInFlight$;
    }
    this.initInFlight$ = this.refreshSession().pipe(
      map(() => true),
      catchError(() => {
        this.clearLocalSession();
        this.statusSignal.set('UNAUTHENTICATED');
        return of(false);
      }),
      finalize(() => {
        this.initInFlight$ = null;
      }),
      shareReplay(1),
    );
    return this.initInFlight$;
  }

  register(request: RegisterRequest): Observable<CurrentUser> {
    return this.ensureCsrf().pipe(
      switchMap(() =>
        this.http.post<AuthResponse>(`${this.base}/auth/register`, request, {
          withCredentials: true,
        }),
      ),
      tap((res) => this.applyAuthResponse(res)),
      switchMap(() => this.loadMe()),
      map((user) => user),
    );
  }

  login(request: LoginRequest): Observable<CurrentUser> {
    return this.ensureCsrf().pipe(
      switchMap(() =>
        this.http.post<AuthResponse>(`${this.base}/auth/login`, request, {
          withCredentials: true,
        }),
      ),
      tap((res) => this.applyAuthResponse(res)),
      switchMap(() => this.loadMe()),
    );
  }

  /**
   * Single-flight refresh using HttpOnly cookie (Angular never reads the raw token).
   */
  refreshSession(): Observable<string> {
    if (this.refreshInFlight$) {
      return this.refreshInFlight$;
    }
    this.refreshInFlight$ = this.ensureCsrf().pipe(
      switchMap(() =>
        this.http.post<AuthResponse>(
          `${this.base}/auth/refresh`,
          {},
          { withCredentials: true },
        ),
      ),
      tap((res) => this.applyAuthResponse(res)),
      switchMap((res) =>
        this.loadMe().pipe(map(() => res.accessToken)),
      ),
      finalize(() => {
        this.refreshInFlight$ = null;
      }),
      shareReplay(1),
    );
    return this.refreshInFlight$;
  }

  loadMe(): Observable<CurrentUser> {
    return this.http
      .get<CurrentUser>(`${this.base}/me`, { withCredentials: true })
      .pipe(
        tap((user) => {
          this.userSignal.set(user);
          this.statusSignal.set('AUTHENTICATED');
        }),
      );
  }

  logout(): Observable<void> {
    return this.ensureCsrf().pipe(
      switchMap(() =>
        this.http.post<void>(`${this.base}/auth/logout`, {}, { withCredentials: true }),
      ),
      catchError(() => of(void 0)),
      tap(() => {
        this.clearLocalSession();
        this.statusSignal.set('UNAUTHENTICATED');
      }),
      map(() => void 0),
    );
  }

  clearLocalSession(): void {
    this.accessToken = null;
    this.userSignal.set(null);
  }

  handleRefreshFailure(): void {
    this.clearLocalSession();
    this.statusSignal.set('UNAUTHENTICATED');
  }

  toUserMessage(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      if (err.status === 0) {
        return 'Unable to reach the server. Check your connection and try again.';
      }
      if (err.status === 429) {
        return 'Too many attempts. Please try again later.';
      }
      const body = err.error as ApiErrorBody | undefined;
      if (body?.message) {
        return body.message;
      }
      if (err.status === 401) {
        return 'Invalid email or password.';
      }
      if (err.status === 409) {
        return 'Unable to complete registration with the provided email.';
      }
    }
    return 'Something went wrong. Please try again.';
  }

  private applyAuthResponse(res: AuthResponse): void {
    this.accessToken = res.accessToken;
    if (res.user) {
      this.userSignal.set(res.user);
      this.statusSignal.set('AUTHENTICATED');
    }
  }

  private ensureCsrf(): Observable<unknown> {
    return this.http
      .get(`${this.base}/auth/csrf`, { withCredentials: true })
      .pipe(catchError(() => of(null)));
  }
}
