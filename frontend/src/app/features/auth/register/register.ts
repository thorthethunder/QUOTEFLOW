import { Component, inject, signal } from '@angular/core';
import {
  FormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { environment } from '../../../../environments/environment';

const CURRENCIES = [
  { code: 'INR', label: 'INR — Indian Rupee' },
  { code: 'USD', label: 'USD — US Dollar' },
  { code: 'EUR', label: 'EUR — Euro' },
  { code: 'GBP', label: 'GBP — British Pound' },
  { code: 'AUD', label: 'AUD — Australian Dollar' },
  { code: 'CAD', label: 'CAD — Canadian Dollar' },
  { code: 'SGD', label: 'SGD — Singapore Dollar' },
  { code: 'AED', label: 'AED — UAE Dirham' },
] as const;

@Component({
  selector: 'app-register',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './register.html',
  styleUrl: './register.scss',
})
export class RegisterComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly appName = environment.appName;
  readonly currencies = CURRENCIES;
  readonly timezones = Intl.supportedValuesOf('timeZone');
  readonly hidePassword = signal(true);
  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  private readonly browserTz = (() => {
    const raw = Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Kolkata';
    // Windows/ICU may report the deprecated alias; prefer the canonical IANA id.
    return raw === 'Asia/Calcutta' ? 'Asia/Kolkata' : raw;
  })();

  readonly form = this.fb.nonNullable.group({
    businessName: ['', [Validators.required, Validators.maxLength(200)]],
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(320)]],
    password: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(128)]],
    currency: ['INR', [Validators.required, Validators.pattern(/^[A-Z]{3}$/)]],
    timezone: [
      this.timezones.includes(this.browserTz) ? this.browserTz : 'Asia/Kolkata',
      [Validators.required, Validators.maxLength(64)],
    ],
  });

  togglePassword(): void {
    this.hidePassword.update((v) => !v);
  }

  submit(): void {
    this.errorMessage.set(null);
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.auth
      .register(this.form.getRawValue())
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => void this.router.navigateByUrl('/app'),
        error: (err) => this.errorMessage.set(this.auth.toUserMessage(err)),
      });
  }
}
