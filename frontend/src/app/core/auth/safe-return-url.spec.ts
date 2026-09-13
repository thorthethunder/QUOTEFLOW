import { safeReturnUrl } from './safe-return-url';

describe('safeReturnUrl', () => {
  it('allows in-app relative paths', () => {
    expect(safeReturnUrl('/app/customers')).toBe('/app/customers');
    expect(safeReturnUrl('/app/invoices/abc?tab=1')).toBe('/app/invoices/abc?tab=1');
  });

  it('rejects open-redirect candidates', () => {
    expect(safeReturnUrl('https://evil.example/phish')).toBe('/app');
    expect(safeReturnUrl('//evil.example/phish')).toBe('/app');
    expect(safeReturnUrl('http://evil.example')).toBe('/app');
    expect(safeReturnUrl('javascript:alert(1)')).toBe('/app');
    expect(safeReturnUrl('')).toBe('/app');
    expect(safeReturnUrl(null)).toBe('/app');
  });
});
