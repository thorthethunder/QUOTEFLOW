import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatSidenavModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
  ],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.scss',
})
export class AppShellComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly breakpoints = inject(BreakpointObserver);
  private readonly destroyRef = inject(DestroyRef);

  readonly appName = environment.appName;
  readonly user = this.auth.currentUser;
  readonly isHandset = signal(false);
  readonly sidenavOpened = signal(true);

  readonly navItems = [
    { label: 'Dashboard', path: '/app', icon: 'dashboard', exact: true },
    { label: 'Customers', path: '/app/customers', icon: 'groups', exact: false },
    { label: 'Quotations', path: '/app/quotations', icon: 'request_quote', exact: false },
    { label: 'Invoices', path: '/app/invoices', icon: 'receipt_long', exact: false },
    { label: 'Business Copilot', path: '/app/copilot', icon: 'smart_toy', exact: false },
    { label: 'Plan & usage', path: '/app/plan', icon: 'workspace_premium', exact: false },
  ] as const;

  constructor() {
    this.breakpoints
      .observe('(max-width: 959.98px)')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((state) => {
        this.isHandset.set(state.matches);
        this.sidenavOpened.set(!state.matches);
      });
  }

  toggleNav(): void {
    this.sidenavOpened.update((v) => !v);
  }

  onNavClick(): void {
    if (this.isHandset()) {
      this.sidenavOpened.set(false);
    }
  }

  logout(): void {
    this.auth.logout().subscribe({
      next: () => void this.router.navigateByUrl('/login'),
      error: () => void this.router.navigateByUrl('/login'),
    });
  }
}
