import { environment } from '../../../environments/environment';

/**
 * Public runtime config loaded from `/config.json` (Cloudflare Pages can replace
 * this file per environment without rebuilding Angular TypeScript).
 * Falls back to compile-time `environment.apiBaseUrl`.
 */
export type PublicAppConfig = {
  apiBaseUrl: string;
};

let resolved: PublicAppConfig = {
  apiBaseUrl: environment.apiBaseUrl,
};

export function getAppConfig(): PublicAppConfig {
  return resolved;
}

export function applyPublicAppConfig(partial: Partial<PublicAppConfig> | null | undefined): void {
  if (!partial) {
    return;
  }
  const apiBaseUrl = partial.apiBaseUrl?.trim();
  if (apiBaseUrl) {
    resolved = { apiBaseUrl: apiBaseUrl.replace(/\/$/, '') };
    // Keep legacy environment reads in sync for services that capture at call time.
    (environment as { apiBaseUrl: string }).apiBaseUrl = resolved.apiBaseUrl;
  }
}

export async function loadPublicAppConfig(): Promise<PublicAppConfig> {
  try {
    const response = await fetch('/config.json', { cache: 'no-store' });
    if (response.ok) {
      const json = (await response.json()) as Partial<PublicAppConfig>;
      applyPublicAppConfig(json);
    }
  } catch {
    // Offline / missing file — keep compile-time defaults.
  }
  return resolved;
}
