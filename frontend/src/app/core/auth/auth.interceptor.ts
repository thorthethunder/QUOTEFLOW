import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

const AUTH_PATHS_SKIP_REFRESH = [
  '/auth/login',
  '/auth/register',
  '/auth/refresh',
  '/auth/logout',
  '/auth/csrf',
];

function isAuthBootstrapUrl(url: string): boolean {
  return AUTH_PATHS_SKIP_REFRESH.some((p) => url.includes(p));
}

/**
 * Attaches Bearer access token; on 401 performs a single-flight refresh and retries once.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.getAccessToken();

  let outbound = req;
  if (token && !isAuthBootstrapUrl(req.url) && !req.headers.has('Authorization')) {
    outbound = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` },
      withCredentials: true,
    });
  } else if (!req.withCredentials) {
    outbound = req.clone({ withCredentials: true });
  }

  return next(outbound).pipe(
    catchError((err: unknown) => {
      if (!(err instanceof HttpErrorResponse) || err.status !== 401) {
        return throwError(() => err);
      }
      if (isAuthBootstrapUrl(outbound.url)) {
        return throwError(() => err);
      }
      if (outbound.headers.has('X-Auth-Retry')) {
        auth.handleRefreshFailure();
        return throwError(() => err);
      }
      return auth.refreshSession().pipe(
        switchMap(() => {
          const nextToken = auth.getAccessToken();
          const retry = outbound.clone({
            setHeaders: {
              Authorization: nextToken ? `Bearer ${nextToken}` : '',
              'X-Auth-Retry': '1',
            },
            withCredentials: true,
          });
          return next(retry);
        }),
        catchError((refreshErr) => {
          auth.handleRefreshFailure();
          return throwError(() => refreshErr);
        }),
      );
    }),
  );
};
