import { applyPublicAppConfig, getAppConfig, loadPublicAppConfig } from './app-config';

describe('app-config', () => {
  it('applies public apiBaseUrl without trailing slash', () => {
    applyPublicAppConfig({ apiBaseUrl: 'https://api-staging.example.com/api/v1/' });
    expect(getAppConfig().apiBaseUrl).toBe('https://api-staging.example.com/api/v1');
  });

  it('loadPublicAppConfig falls back when fetch fails', async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = jasmine.createSpy('fetch').and.rejectWith(new Error('offline'));
    try {
      applyPublicAppConfig({ apiBaseUrl: '/api/v1' });
      const cfg = await loadPublicAppConfig();
      expect(cfg.apiBaseUrl).toBe('/api/v1');
    } finally {
      globalThis.fetch = originalFetch;
    }
  });
});
