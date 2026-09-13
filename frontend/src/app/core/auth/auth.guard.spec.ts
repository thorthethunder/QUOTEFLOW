import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { authGuard } from './auth.guard';
import { guestGuard } from './guest.guard';
import { AuthService } from './auth.service';

describe('auth guards', () => {
  it('authGuard allows authenticated sessions', () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: { initializeSession: () => of(true) } },
      ],
    });
    const result = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
    (result as ReturnType<typeof of>).subscribe((v) => expect(v).toBeTrue());
  });

  it('guestGuard redirects when authenticated', () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'app', children: [] }]),
        { provide: AuthService, useValue: { initializeSession: () => of(true) } },
      ],
    });
    const router = TestBed.inject(Router);
    const result = TestBed.runInInjectionContext(() => guestGuard({} as never, {} as never));
    (result as ReturnType<typeof of>).subscribe((v) => {
      expect(String(v)).toContain('/app');
      expect(v).not.toBeTrue();
    });
    expect(router).toBeTruthy();
  });
});
