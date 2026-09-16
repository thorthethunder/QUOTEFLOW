/**
 * Same-origin /api/* reverse proxy to a fixed backend origin.
 * Not an open proxy: target host comes only from Pages env `BACKEND_ORIGIN`
 * (or the staging default below). Never from the request URL/query.
 *
 * Preserves method/body/query and auth-relevant headers (Cookie, Authorization,
 * X-XSRF-TOKEN, X-Correlation-Id). Forces private, no-store on responses.
 */

/** Staging default. Production Pages must set BACKEND_ORIGIN to the prod API host. */
const DEFAULT_BACKEND_ORIGIN = 'https://quoteflow-backend-staging.up.railway.app';

const STRIP_REQUEST_HEADERS = new Set([
	'host',
	'connection',
	'keep-alive',
	'proxy-authenticate',
	'proxy-authorization',
	'te',
	'trailers',
	'transfer-encoding',
	'upgrade',
	'content-length',
	'cf-connecting-ip',
	'cf-ipcountry',
	'cf-ray',
	'cf-visitor',
	'cf-ew-via',
	'cdn-loop',
	'x-forwarded-for',
	'x-forwarded-proto',
	'x-real-ip',
]);

type PagesContext = {
	request: Request;
	next: () => Promise<Response>;
	env: Record<string, unknown>;
};

function resolveBackendOrigin(env: Record<string, unknown>): string {
	const configured = typeof env.BACKEND_ORIGIN === 'string' ? env.BACKEND_ORIGIN.trim() : '';
	const origin = configured || DEFAULT_BACKEND_ORIGIN;
	if (!/^https:\/\/[a-z0-9.-]+(?::\d+)?$/i.test(origin)) {
		throw new Error('BACKEND_ORIGIN must be an https host origin without path');
	}
	return origin.replace(/\/$/, '');
}

export async function onRequest(context: PagesContext): Promise<Response> {
	const incoming = context.request;
	const url = new URL(incoming.url);

	if (!url.pathname.startsWith('/api/')) {
		return context.next();
	}

	const backendOrigin = resolveBackendOrigin(context.env);
	const targetUrl = `${backendOrigin}${url.pathname}${url.search}`;
	const headers = new Headers();
	for (const [key, value] of incoming.headers.entries()) {
		if (STRIP_REQUEST_HEADERS.has(key.toLowerCase())) {
			continue;
		}
		headers.set(key, value);
	}
	headers.set('X-Forwarded-Proto', 'https');
	headers.set('X-Forwarded-Host', url.host);

	const init: RequestInit = {
		method: incoming.method,
		headers,
		redirect: 'manual',
	};

	if (incoming.method !== 'GET' && incoming.method !== 'HEAD') {
		init.body = incoming.body;
		// Required for streaming request bodies in the Workers runtime.
		(init as RequestInit & { duplex?: string }).duplex = 'half';
	}

	const upstream = await fetch(targetUrl, init);
	const outHeaders = new Headers();
	upstream.headers.forEach((value, key) => {
		if (key.toLowerCase() === 'set-cookie') {
			return;
		}
		outHeaders.append(key, value);
	});
	// Headers() can drop/merge Set-Cookie; forward each cookie explicitly.
	const setCookies =
		typeof upstream.headers.getSetCookie === 'function'
			? upstream.headers.getSetCookie()
			: [];
	for (const cookie of setCookies) {
		outHeaders.append('Set-Cookie', cookie);
	}
	outHeaders.set('Cache-Control', 'private, no-store');

	return new Response(upstream.body, {
		status: upstream.status,
		statusText: upstream.statusText,
		headers: outHeaders,
	});
}
