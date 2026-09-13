import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CustomerApiService } from './customer-api.service';

describe('CustomerApiService', () => {
  let api: CustomerApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(CustomerApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists customers with pagination and search params', () => {
    api.list({ q: 'ada', page: 1, size: 10, status: 'ACTIVE' }).subscribe((page) => {
      expect(page.totalElements).toBe(1);
    });

    const req = http.expectOne(
      (r) =>
        r.url.endsWith('/customers') &&
        r.params.get('q') === 'ada' &&
        r.params.get('page') === '1' &&
        r.params.get('size') === '10' &&
        r.params.get('status') === 'ACTIVE',
    );
    expect(req.request.method).toBe('GET');
    req.flush({
      content: [],
      page: 1,
      size: 10,
      totalElements: 1,
      totalPages: 1,
    });
  });

  it('creates a customer without businessId', () => {
    api.create({ displayName: 'Ada' }).subscribe((c) => {
      expect(c.displayName).toBe('Ada');
    });
    const req = http.expectOne((r) => r.url.endsWith('/customers') && r.method === 'POST');
    expect(req.request.body.businessId).toBeUndefined();
    req.flush({
      id: '11111111-1111-1111-1111-111111111111',
      displayName: 'Ada',
      email: null,
      phone: null,
      companyName: null,
      addressLine1: null,
      addressLine2: null,
      city: null,
      stateRegion: null,
      postalCode: null,
      countryCode: null,
      taxId: null,
      notes: null,
      status: 'ACTIVE',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    });
  });
});
