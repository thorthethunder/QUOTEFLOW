import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  BillingInterval,
  CheckoutResponse,
  PlanId,
  VerifyCheckoutResponse,
} from './plan.models';

@Injectable({ providedIn: 'root' })
export class BillingApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}`;

  createCheckout(plan: PlanId, billingInterval: BillingInterval): Observable<CheckoutResponse> {
    return this.http.post<CheckoutResponse>(`${this.base}/billing/checkout`, {
      plan,
      billingInterval,
    });
  }

  verifyCheckout(payload: {
    razorpayPaymentId: string;
    razorpaySubscriptionId: string;
    razorpaySignature: string;
  }): Observable<VerifyCheckoutResponse> {
    return this.http.post<VerifyCheckoutResponse>(`${this.base}/billing/checkout/verify`, payload);
  }

  cancelSubscription(): Observable<VerifyCheckoutResponse> {
    return this.http.post<VerifyCheckoutResponse>(`${this.base}/billing/subscription/cancel`, {});
  }
}
