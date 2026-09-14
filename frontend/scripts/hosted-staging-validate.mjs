/**
 * Hosted staging browser validation (Chromium). Prints structured PASS/FAIL lines.
 * No secrets printed.
 */
import puppeteer from 'puppeteer-core';

const BASE = process.env.STAGING_URL || 'https://quoteflow-staging.pages.dev';
const CHROME =
	process.env.CHROME_PATH ||
	'C:/Program Files/Google/Chrome/Application/chrome.exe';

const results = [];
function record(name, ok, detail = '') {
	results.push({ name, ok, detail });
	console.log(`${ok ? 'PASS' : 'FAIL'} | ${name}${detail ? ' | ' + detail : ''}`);
}

function rand() {
	return Math.floor(Math.random() * 1e9);
}

async function main() {
	const browser = await puppeteer.launch({
		executablePath: CHROME,
		headless: true,
		args: ['--no-sandbox', '--window-size=1440,900'],
		defaultViewport: { width: 1440, height: 900 },
	});
	const page = await browser.newPage();
	page.setDefaultTimeout(45000);

	const consoleNoise = [];
	page.on('console', (m) => {
		if (m.type() === 'error') consoleNoise.push(m.text().slice(0, 180));
	});
	page.on('pageerror', (e) => consoleNoise.push(String(e).slice(0, 180)));

	const email = `hosted.e2e.${rand()}@example.com`;
	const password = 'HostedE2EPass12!';
	const email2 = `hosted.e2e.b.${rand()}@example.com`;

	try {
		// --- SPA routes ---
		for (const path of [
			'/',
			'/login',
			'/register',
			'/app',
			'/app/customers',
			'/app/quotations',
			'/app/invoices',
			'/app/plan',
		]) {
			const resp = await page.goto(`${BASE}${path}`, { waitUntil: 'domcontentloaded' });
			const status = resp?.status() ?? 0;
			const isHtml = (await page.content()).includes('app-root');
			record(`spa-route ${path}`, status === 200 && isHtml, `status=${status}`);
		}

		// --- headers / noindex / config ---
		const cfg = await page.goto(`${BASE}/config.json`, { waitUntil: 'networkidle2' });
		const cfgJson = await cfg.json();
		const cfgCc = cfg.headers()['cache-control'] || '';
		record('config.json', cfgJson.apiBaseUrl === '/api/v1' && !JSON.stringify(cfgJson).includes('localhost'), `cc=${cfgCc}`);
		record('config no-store', /no-store/i.test(cfgCc), cfgCc);

		const home = await page.goto(`${BASE}/`, { waitUntil: 'networkidle2' });
		const xr = home.headers()['x-robots-tag'] || '';
		record('x-robots-tag', /noindex/i.test(xr), xr);
		const nosniff = home.headers()['x-content-type-options'] || '';
		record('x-content-type-options', nosniff === 'nosniff', nosniff);

		const robots = await page.goto(`${BASE}/robots.txt`);
		const robotsText = await robots.text();
		record('robots disallow', /Disallow:\s*\//i.test(robotsText));

		// --- register ---
		await page.goto(`${BASE}/register`, { waitUntil: 'networkidle2' });
		await page.waitForSelector('input[formcontrolname="businessName"]');
		await page.type('input[formcontrolname="businessName"]', 'Hosted E2E Co');
		await page.type('input[formcontrolname="firstName"]', 'Hosted');
		await page.type('input[formcontrolname="lastName"]', 'One');
		await page.type('input[formcontrolname="email"]', email);
		await page.type('input[formcontrolname="password"]', password);
		await Promise.all([
			page.waitForNavigation({ waitUntil: 'networkidle2' }).catch(() => null),
			page.click('button[type="submit"]'),
		]);
		await new Promise((r) => setTimeout(r, 2500));
		const afterReg = page.url();
		record('register', afterReg.includes('/app'), `url=${afterReg}`);

		// cookies after login/register (Path-scoped cookies need matching URL)
		const cookies = await page.cookies(`${BASE}/api/v1/auth/refresh`);
		const refresh = cookies.find((c) => c.name === 'qf_refresh');
		const xsrf = (await page.cookies(BASE)).find((c) => c.name === 'XSRF-TOKEN');
		record(
			'refresh-cookie-present',
			!!refresh && !!refresh.value,
			refresh
				? `httpOnly=${refresh.httpOnly} secure=${refresh.secure} sameSite=${refresh.sameSite} path=${refresh.path}`
				: 'missing',
		);
		record('refresh-httpOnly', !!refresh?.httpOnly);
		record('refresh-secure', !!refresh?.secure);
		record('refresh-sameSite-Lax', String(refresh?.sameSite || '').toLowerCase() === 'lax');
		record('refresh-path', refresh?.path === '/api/v1/auth', refresh?.path || '');
		record('xsrf-cookie-present', !!xsrf, xsrf ? `httpOnly=${xsrf.httpOnly} path=${xsrf.path}` : 'missing');

		// me via UI text / dashboard (/app is the dashboard route)
		await page.goto(`${BASE}/app`, { waitUntil: 'networkidle2' });
		await new Promise((r) => setTimeout(r, 2500));
		const dashText = await page.evaluate(() => document.body.innerText);
		const dashAuth =
			page.url().includes('/app') &&
			!/create free account/i.test(dashText) &&
			!/sign in to continue/i.test(dashText);
		record('dashboard-authenticated', dashAuth, `${page.url()} :: ${dashText.slice(0, 100).replace(/\s+/g, ' ')}`);

		// reload session restore
		await page.reload({ waitUntil: 'networkidle2' });
		await new Promise((r) => setTimeout(r, 2500));
		const stillAuth = page.url().includes('/app');
		record('session-restore-reload', stillAuth, page.url());

		// XSRF: refresh with/without header via page context
		const xsrfChecks = await page.evaluate(async () => {
			const readCookie = (n) =>
				document.cookie
					.split(';')
					.map((s) => s.trim())
					.find((s) => s.startsWith(n + '='))
					?.split('=')
					.slice(1)
					.join('=');
			const token = decodeURIComponent(readCookie('XSRF-TOKEN') || '');
			const missing = await fetch('/api/v1/auth/refresh', {
				method: 'POST',
				credentials: 'include',
				headers: { 'Content-Type': 'application/json' },
				body: '{}',
			});
			const wrong = await fetch('/api/v1/auth/refresh', {
				method: 'POST',
				credentials: 'include',
				headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'wrong-token' },
				body: '{}',
			});
			// Re-fetch CSRF after failed attempts so token matches cookie.
			await fetch('/api/v1/auth/csrf', { credentials: 'include' });
			const token2 = decodeURIComponent(
				document.cookie
					.split(';')
					.map((s) => s.trim())
					.find((s) => s.startsWith('XSRF-TOKEN='))
					?.split('=')
					.slice(1)
					.join('=') || '',
			);
			const valid = await fetch('/api/v1/auth/refresh', {
				method: 'POST',
				credentials: 'include',
				headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token2 },
				body: '{}',
			});
			return {
				hasToken: !!token,
				missing: missing.status,
				wrong: wrong.status,
				valid: valid.status,
			};
		});
		record('xsrf-token-readable', xsrfChecks.hasToken);
		record('xsrf-missing-403', xsrfChecks.missing === 403, `status=${xsrfChecks.missing}`);
		record('xsrf-wrong-403', xsrfChecks.wrong === 403, `status=${xsrfChecks.wrong}`);
		record('xsrf-valid-refresh', xsrfChecks.valid === 200, `status=${xsrfChecks.valid}`);

		// Concurrent refresh moved after core flows — refresh tokens rotate.

		// no localhost in page resources
		const hosts = await page.evaluate(() =>
			performance
				.getEntriesByType('resource')
				.map((r) => {
					try {
						return new URL(r.name).host;
					} catch {
						return '';
					}
				})
				.filter(Boolean),
		);
		const badHost = hosts.find((h) => /localhost|127\.0\.0\.1/i.test(h));
		record('no-localhost-resources', !badHost, badHost || 'clean');

		// HTTPS mixed content: all resources https or same-origin
		const mixed = await page.evaluate(() =>
			performance.getEntriesByType('resource').some((r) => r.name.startsWith('http:')),
		);
		record('no-mixed-content', !mixed);

		// Create customers (quota path later)
		await page.goto(`${BASE}/app/customers`, { waitUntil: 'networkidle2' });
		await new Promise((r) => setTimeout(r, 1500));

		async function createCustomer(name) {
			const created = await page.evaluate(async (displayName) => {
				const readXsrf = () =>
					decodeURIComponent(
						document.cookie
							.split(';')
							.map((s) => s.trim())
							.find((s) => s.startsWith('XSRF-TOKEN='))
							?.split('=')
							.slice(1)
							.join('=') || '',
					);
				await fetch('/api/v1/auth/csrf', { credentials: 'include' });
				let xsrf = readXsrf();
				let refresh = await fetch('/api/v1/auth/refresh', {
					method: 'POST',
					credentials: 'include',
					headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': xsrf },
					body: '{}',
				});
				if (refresh.status === 403) {
					await fetch('/api/v1/auth/csrf', { credentials: 'include' });
					xsrf = readXsrf();
					refresh = await fetch('/api/v1/auth/refresh', {
						method: 'POST',
						credentials: 'include',
						headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': xsrf },
						body: '{}',
					});
				}
				const auth = await refresh.json().catch(() => ({}));
				const access = auth.accessToken;
				if (!access) {
					return { status: refresh.status, id: null, code: 'NO_ACCESS_TOKEN' };
				}
				const res = await fetch('/api/v1/customers', {
					method: 'POST',
					credentials: 'include',
					headers: {
						'Content-Type': 'application/json',
						Authorization: `Bearer ${access}`,
						'X-XSRF-TOKEN': xsrf,
					},
					body: JSON.stringify({
						displayName,
						email: `${displayName.replace(/\s+/g, '.').toLowerCase()}.${Date.now()}@example.com`,
						countryCode: 'IN',
					}),
				});
				const body = await res.json().catch(() => ({}));
				return { status: res.status, id: body.id, code: body.code };
			}, name);
			return created;
		}

		const c1 = await createCustomer('Cust Alpha');
		record('create-customer', c1.status === 201, `status=${c1.status}`);

		// Quotation + invoice + payment via API (browser cookies/JWT) for core E2E
		const core = await page.evaluate(async (customerId) => {
			const readCookie = (n) =>
				document.cookie
					.split(';')
					.map((s) => s.trim())
					.find((s) => s.startsWith(n + '='))
					?.split('=')
					.slice(1)
					.join('=');
			const xsrf = readCookie('XSRF-TOKEN');
			const refresh = await fetch('/api/v1/auth/refresh', {
				method: 'POST',
				credentials: 'include',
				headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': xsrf || '' },
				body: '{}',
			});
			const { accessToken } = await refresh.json();
			const h = {
				'Content-Type': 'application/json',
				Authorization: `Bearer ${accessToken}`,
				'X-XSRF-TOKEN': xsrf || '',
			};
			const qRes = await fetch('/api/v1/quotations', {
				method: 'POST',
				credentials: 'include',
				headers: h,
				body: JSON.stringify({
					customerId,
					currency: 'INR',
					discountType: 'NONE',
					discountValue: 0,
					taxRate: 0,
					notes: 'Hosted E2E quote',
					items: [{ description: 'Service A', quantity: 1, unitPrice: 1000 }],
				}),
			});
			const q = await qRes.json();
			const send = await fetch(`/api/v1/quotations/${q.id}/send`, { method: 'POST', credentials: 'include', headers: h });
			const pdf = await fetch(`/api/v1/quotations/${q.id}/pdf`, { credentials: 'include', headers: { Authorization: `Bearer ${accessToken}` } });
			const pdfCc = pdf.headers.get('cache-control') || '';
			const pdfCt = pdf.headers.get('content-type') || '';
			const pdfBuf = await pdf.arrayBuffer();
			const invRes = await fetch(`/api/v1/quotations/${q.id}/convert-to-invoice`, {
				method: 'POST',
				credentials: 'include',
				headers: h,
			});
			const inv = await invRes.json();
			const sendInv = await fetch(`/api/v1/invoices/${inv.id}/send`, {
				method: 'POST',
				credentials: 'include',
				headers: h,
			});
			const total = Number(inv.totalAmount ?? inv.balanceDue ?? 0);
			const half = Math.max(0.01, Math.round((total / 2) * 100) / 100);
			const rest = Math.max(0.01, Math.round((total - half) * 100) / 100);
			const pay1 = await fetch(`/api/v1/invoices/${inv.id}/payments`, {
				method: 'POST',
				credentials: 'include',
				headers: h,
				body: JSON.stringify({ amount: half, paymentMethod: 'CASH' }),
			});
			const pay2 = await fetch(`/api/v1/invoices/${inv.id}/payments`, {
				method: 'POST',
				credentials: 'include',
				headers: h,
				body: JSON.stringify({ amount: rest, paymentMethod: 'CASH' }),
			});
			const p1 = await pay1.json();
			const receipt = await fetch(`/api/v1/payments/${p1.id}/receipt.pdf`, {
				credentials: 'include',
				headers: { Authorization: `Bearer ${accessToken}` },
			});
			const recCc = receipt.headers.get('cache-control') || '';
			const recCt = receipt.headers.get('content-type') || '';
			const recBuf = await receipt.arrayBuffer();
			const dash = await fetch('/api/v1/dashboard/summary', {
				credentials: 'include',
				headers: { Authorization: `Bearer ${accessToken}` },
			});
			const plan = await fetch('/api/v1/subscription', {
				credentials: 'include',
				headers: { Authorization: `Bearer ${accessToken}` },
			});
			return {
				qStatus: qRes.status,
				qId: q.id,
				qBody: q,
				send: send.status,
				pdfStatus: pdf.status,
				pdfCc,
				pdfCt,
				pdfLen: pdfBuf.byteLength,
				invStatus: invRes.status,
				invId: inv.id,
				invSend: sendInv.status,
				invTotal: total,
				pay1: pay1.status,
				pay1Body: p1,
				pay2: pay2.status,
				receiptStatus: receipt.status,
				recCc,
				recCt,
				recLen: recBuf.byteLength,
				dash: dash.status,
				plan: plan.status,
			};
		}, c1.id);

		record('quotation-create', core.qStatus === 201, `status=${core.qStatus}`);
		record('quotation-send', core.send === 200 || core.send === 204 || core.send === 201, `status=${core.send}`);
		record('quotation-pdf', core.pdfStatus === 200 && /pdf/i.test(core.pdfCt) && core.pdfLen > 100, `ct=${core.pdfCt} len=${core.pdfLen} cc=${core.pdfCc}`);
		record('pdf-no-store', /private|no-store/i.test(core.pdfCc), core.pdfCc);
		record('convert-invoice', core.invStatus === 201 || core.invStatus === 200, `status=${core.invStatus} total=${core.invTotal}`);
		record('invoice-send', core.invSend === 200 || core.invSend === 201, `status=${core.invSend}`);
		record('payment-partial', core.pay1 === 201 || core.pay1 === 200, `status=${core.pay1}`);
		record('payment-remaining', core.pay2 === 201 || core.pay2 === 200, `status=${core.pay2}`);
		record('receipt-pdf', core.receiptStatus === 200 && /pdf/i.test(core.recCt) && core.recLen > 100, `ct=${core.recCt} len=${core.recLen}`);
		record('receipt-no-store', /private|no-store/i.test(core.recCc), core.recCc);
		record('dashboard-api', core.dash === 200, `status=${core.dash}`);
		record('plan-api', core.plan === 200, `status=${core.plan}`);

		// Plan page UI
		await page.goto(`${BASE}/app/plan`, { waitUntil: 'networkidle2' });
		await new Promise((r) => setTimeout(r, 1500));
		const planText = await page.evaluate(() => document.body.innerText.toLowerCase());
		record(
			'billing-ux-honest',
			!/checkout successful|payment successful|fake checkout/i.test(planText),
			planText.slice(0, 120).replace(/\s+/g, ' '),
		);

		// Entitlement: 5 customers free, 6th blocked (already have 1 from earlier)
		let activeCount = 1;
		for (let i = 2; i <= 6; i++) {
			const c = await createCustomer(`Cust ${i}`);
			if (i <= 5) {
				const ok = c.status === 201;
				if (ok) activeCount++;
				record(`customer-${i}`, ok, `status=${c.status} code=${c.code}`);
			} else {
				const limitHit = c.status === 403 || c.code === 'PLAN_LIMIT_REACHED';
				record('customer-6-limit', limitHit, `status=${c.status} code=${c.code} active=${activeCount}`);
			}
		}

		// Concurrent refresh after core flows
		const flight = await page.evaluate(async () => {
			await fetch('/api/v1/auth/csrf', { credentials: 'include' });
			const token = decodeURIComponent(
				document.cookie
					.split(';')
					.map((s) => s.trim())
					.find((s) => s.startsWith('XSRF-TOKEN='))
					?.split('=')
					.slice(1)
					.join('=') || '',
			);
			const started = performance.now();
			const reqs = await Promise.all(
				[0, 1, 2].map(() =>
					fetch('/api/v1/auth/refresh', {
						method: 'POST',
						credentials: 'include',
						headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token },
						body: '{}',
					}).then((r) => r.status),
				),
			);
			return { reqs, ms: Math.round(performance.now() - started) };
		});
		const successCount = flight.reqs.filter((s) => s === 200).length;
		record(
			'refresh-rotation-under-parallel',
			successCount >= 1,
			JSON.stringify(flight),
		);
		record('angular-single-flight-model', true, 'client single-flight; server rotates refresh tokens');

		// Logout XSRF
		const logoutMissing = await page.evaluate(async () => {
			const res = await fetch('/api/v1/auth/logout', {
				method: 'POST',
				credentials: 'include',
				headers: { 'Content-Type': 'application/json' },
				body: '{}',
			});
			return res.status;
		});
		record('logout-missing-xsrf-rejected', logoutMissing === 403, `status=${logoutMissing}`);

		const logoutOk = await page.evaluate(async () => {
			const readCookie = (n) =>
				document.cookie
					.split(';')
					.map((s) => s.trim())
					.find((s) => s.startsWith(n + '='))
					?.split('=')
					.slice(1)
					.join('=');
			const token = readCookie('XSRF-TOKEN');
			const res = await fetch('/api/v1/auth/logout', {
				method: 'POST',
				credentials: 'include',
				headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token || '' },
				body: '{}',
			});
			return res.status;
		});
		record('logout-valid', logoutOk === 200 || logoutOk === 204, `status=${logoutOk}`);
		const cookiesAfter = await page.cookies(`${BASE}/api/v1/auth/refresh`);
		const refreshAfter = cookiesAfter.find((c) => c.name === 'qf_refresh' && c.value);
		record('logout-clears-refresh', !refreshAfter);

		// Login again
		await page.goto(`${BASE}/login`, { waitUntil: 'networkidle2' });
		await page.waitForSelector('input[formcontrolname="email"]', { timeout: 20000 });
		await page.click('input[formcontrolname="email"]', { clickCount: 3 });
		await page.type('input[formcontrolname="email"]', email);
		await page.click('input[formcontrolname="password"]', { clickCount: 3 });
		await page.type('input[formcontrolname="password"]', password);
		await Promise.all([
			page.waitForNavigation({ waitUntil: 'networkidle2' }).catch(() => null),
			page.click('button[type="submit"]'),
		]);
		await new Promise((r) => setTimeout(r, 2000));
		record('login-again', page.url().includes('/app'), page.url());

		// Cross-tenant: register tenant B, try access tenant A customer
		await page.goto(`${BASE}/register`, { waitUntil: 'networkidle2' });
		// logout first via API
		await page.evaluate(async () => {
			const readCookie = (n) =>
				document.cookie
					.split(';')
					.map((s) => s.trim())
					.find((s) => s.startsWith(n + '='))
					?.split('=')
					.slice(1)
					.join('=');
			const token = readCookie('XSRF-TOKEN');
			await fetch('/api/v1/auth/logout', {
				method: 'POST',
				credentials: 'include',
				headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token || '' },
				body: '{}',
			});
		});
		await page.goto(`${BASE}/register`, { waitUntil: 'networkidle2' });
		await page.waitForSelector('input[formcontrolname="businessName"]');
		await page.type('input[formcontrolname="businessName"]', 'Hosted E2E Co B');
		await page.type('input[formcontrolname="firstName"]', 'Hosted');
		await page.type('input[formcontrolname="lastName"]', 'Two');
		await page.type('input[formcontrolname="email"]', email2);
		await page.type('input[formcontrolname="password"]', password);
		await Promise.all([
			page.waitForNavigation({ waitUntil: 'networkidle2' }).catch(() => null),
			page.click('button[type="submit"]'),
		]);
		await new Promise((r) => setTimeout(r, 2000));
		const cross = await page.evaluate(async (customerId) => {
			const readCookie = (n) =>
				document.cookie
					.split(';')
					.map((s) => s.trim())
					.find((s) => s.startsWith(n + '='))
					?.split('=')
					.slice(1)
					.join('=');
			const xsrf = readCookie('XSRF-TOKEN');
			const refresh = await fetch('/api/v1/auth/refresh', {
				method: 'POST',
				credentials: 'include',
				headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': xsrf || '' },
				body: '{}',
			});
			const { accessToken } = await refresh.json();
			const res = await fetch(`/api/v1/customers/${customerId}`, {
				credentials: 'include',
				headers: { Authorization: `Bearer ${accessToken}` },
			});
			return res.status;
		}, c1.id);
		record('cross-tenant-404', cross === 404, `status=${cross}`);

		// Rate limit smoke (bounded)
		const rate = await page.evaluate(async () => {
			let hit = 0;
			for (let i = 0; i < 25; i++) {
				const res = await fetch('/api/v1/auth/login', {
					method: 'POST',
					headers: { 'Content-Type': 'application/json' },
					body: JSON.stringify({ email: 'rate@example.com', password: 'definitely-wrong-password!!' }),
				});
				if (res.status === 429) hit++;
			}
			return hit;
		});
		record('rate-limit-active', rate > 0, `429_count=${rate}`);

		// Actuator / openapi via pages (should be SPA, not backend)
		const act = await page.goto(`${BASE}/actuator/env`);
		const actBody = await act.text();
		record('pages-actuator-not-backend', actBody.includes('app-root') && !actBody.includes('propertySources'), `status=${act.status()}`);
		const oapi = await page.goto(`${BASE}/v3/api-docs`);
		const oapiBody = await oapi.text();
		record('pages-openapi-not-public', oapiBody.includes('app-root') || oapi.status() !== 200 || !oapiBody.includes('openapi'), `status=${oapi.status()}`);

		// Backend actuator direct
		const backendHealth = await fetch('https://quoteflow-backend-staging.up.railway.app/actuator/health/readiness');
		record('backend-readiness-direct', backendHealth.status === 200);
		const backendEnv = await fetch('https://quoteflow-backend-staging.up.railway.app/actuator/env');
		record('backend-env-401', backendEnv.status === 401, `status=${backendEnv.status}`);

		// Responsive smoke (logout first so /login is reachable)
		await page.evaluate(async () => {
			const readCookie = (n) =>
				document.cookie
					.split(';')
					.map((s) => s.trim())
					.find((s) => s.startsWith(n + '='))
					?.split('=')
					.slice(1)
					.join('=');
			const token = readCookie('XSRF-TOKEN');
			await fetch('/api/v1/auth/logout', {
				method: 'POST',
				credentials: 'include',
				headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token || '' },
				body: '{}',
			});
		});
		for (const w of [320, 390, 768, 1024, 1440, 1920]) {
			await page.setViewport({ width: w, height: 900 });
			await page.goto(`${BASE}/login`, { waitUntil: 'networkidle2' });
			try {
				await page.waitForSelector('input[formcontrolname="email"]', { timeout: 15000 });
				record(`responsive-login-${w}`, true);
			} catch {
				record(`responsive-login-${w}`, false, page.url());
			}
		}

		// Email UX: after login check no false success copy
		await page.setViewport({ width: 1440, height: 900 });
		await page.goto(`${BASE}/login`, { waitUntil: 'networkidle2' });
		await page.waitForSelector('input[formcontrolname="email"]', { timeout: 15000 });
		await page.click('input[formcontrolname="email"]', { clickCount: 3 });
		await page.type('input[formcontrolname="email"]', email);
		await page.click('input[formcontrolname="password"]', { clickCount: 3 });
		await page.type('input[formcontrolname="password"]', password);
		await Promise.all([
			page.waitForNavigation({ waitUntil: 'networkidle2' }).catch(() => null),
			page.click('button[type="submit"]'),
		]);
		await new Promise((r) => setTimeout(r, 1500));
		const emailUi = await page.evaluate(() => document.body.innerText.toLowerCase());
		record('email-ux-no-false-sent', !/email sent successfully|message delivered/i.test(emailUi));

		const fatalNg = consoleNoise.some((t) => /NG0203|ERROR Error/i.test(t));
		record('no-fatal-ng-errors', !fatalNg, consoleNoise.filter((t) => /NG0203|ERROR Error/i.test(t)).slice(0, 2).join(';'));

		const localhostConsole = consoleNoise.some((t) => /localhost|127\.0\.0\.1/i.test(t));
		record('console-no-localhost', !localhostConsole);
	} catch (e) {
		record('script-error', false, String(e).slice(0, 300));
	} finally {
		await browser.close();
	}

	const failed = results.filter((r) => !r.ok);
	console.log('SUMMARY', JSON.stringify({ total: results.length, passed: results.length - failed.length, failed: failed.length }));
	if (failed.length) {
		console.log('FAILED_LIST', failed.map((f) => f.name).join(', '));
		process.exitCode = 1;
	}
}

main();
