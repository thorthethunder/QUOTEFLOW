/**
 * After Angular production build, apply staging-only static overlays
 * (noindex robots) without changing the production-intended robots template.
 */
import { copyFileSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const dist = join(root, 'dist', 'frontend', 'browser');
const stagingRobots = join(root, 'public', 'robots.staging.txt');
const targetRobots = join(dist, 'robots.txt');

if (!existsSync(dist)) {
	console.error('Missing build output:', dist);
	process.exit(1);
}
if (!existsSync(stagingRobots)) {
	console.error('Missing staging robots:', stagingRobots);
	process.exit(1);
}

copyFileSync(stagingRobots, targetRobots);
console.log('Applied staging robots.txt (Disallow: /)');
