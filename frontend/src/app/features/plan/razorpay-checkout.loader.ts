import { Injectable } from '@angular/core';

export interface RazorpayCheckoutSuccess {
  razorpay_payment_id: string;
  razorpay_subscription_id: string;
  razorpay_signature: string;
}

interface RazorpayCheckoutOptions {
  key: string;
  subscription_id: string;
  name?: string;
  description?: string;
  handler: (response: RazorpayCheckoutSuccess) => void;
  modal?: { ondismiss?: () => void };
}

declare global {
  interface Window {
    Razorpay?: new (options: RazorpayCheckoutOptions) => { open: () => void };
  }
}

/**
 * Lazily loads Razorpay Checkout only when billing flow needs it.
 */
@Injectable({ providedIn: 'root' })
export class RazorpayCheckoutLoader {
  private loading: Promise<void> | null = null;

  load(): Promise<void> {
    if (typeof window === 'undefined') {
      return Promise.reject(new Error('Checkout unavailable'));
    }
    if (window.Razorpay) {
      return Promise.resolve();
    }
    if (this.loading) {
      return this.loading;
    }
    this.loading = new Promise<void>((resolve, reject) => {
      const script = document.createElement('script');
      script.src = 'https://checkout.razorpay.com/v1/checkout.js';
      script.async = true;
      script.onload = () => resolve();
      script.onerror = () => {
        this.loading = null;
        reject(new Error('Unable to load Razorpay Checkout'));
      };
      document.body.appendChild(script);
    });
    return this.loading;
  }

  open(options: {
    keyId: string;
    subscriptionId: string;
    description: string;
  }): Promise<RazorpayCheckoutSuccess | 'dismissed'> {
    return this.load().then(
      () =>
        new Promise((resolve, reject) => {
          if (!window.Razorpay) {
            reject(new Error('Razorpay Checkout unavailable'));
            return;
          }
          const rzp = new window.Razorpay({
            key: options.keyId,
            subscription_id: options.subscriptionId,
            name: 'QuoteFlow',
            description: options.description,
            handler: (response) => resolve(response),
            modal: {
              ondismiss: () => resolve('dismissed'),
            },
          });
          rzp.open();
        }),
    );
  }
}
