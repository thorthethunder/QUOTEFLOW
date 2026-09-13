/**
 * Restrict post-login redirects to same-app relative paths.
 * Rejects protocol-relative (//…), absolute URLs, and empty values.
 */
export function safeReturnUrl(candidate: string | null | undefined, fallback = '/app'): string {
  if (candidate == null) {
    return fallback;
  }
  const trimmed = candidate.trim();
  if (!trimmed.startsWith('/') || trimmed.startsWith('//') || trimmed.includes('://')) {
    return fallback;
  }
  return trimmed;
}
