/** Real Playwright Chromium contract test for the generated E3.0 CI-only site.
 * Records browser observations, not measured Core Web Vitals or production SLOs.
 */
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import {spawn} from 'node:child_process';
import {setTimeout as pause} from 'node:timers/promises';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoDir = path.resolve(scriptDir, '../../..');
const output = path.join(repoDir, 'target', 'e3-factory');
const site = path.join(output, 'site');
const evidence = path.join(output, 'browser-evidence');
const host = 'http://127.0.0.1:18735';
await fs.mkdir(evidence, {recursive: true});
const server = spawn('python3', ['-m', 'http.server', '18735', '--bind', '127.0.0.1', '--directory', site],
  {stdio: ['ignore', 'pipe', 'pipe']});

async function ready() {
  for (let n = 0; n < 35; n++) {
    if (server.exitCode !== null) throw Error('HTTP fixture exited early');
    try {
      const r = await fetch(host + '/index.html', {signal: AbortSignal.timeout(2000)});
      if (r.ok) return;
    } catch { /* bounded readiness poll */ }
    await pause(200);
  }
  throw Error('CI localhost server not ready');
}
function motionProbe(page) {
  return page.evaluate(() => ({
    progress: Number(document.querySelector('#stage').dataset.progress || -1),
    transform: document.querySelector('#device').style.transform,
    motion: document.querySelector('#stage').dataset.motion,
    ariaValue: document.querySelector('#scroll-progress').getAttribute('aria-valuenow'),
    width: document.documentElement.scrollWidth,
    viewport: window.innerWidth,
    caption: document.querySelector('#stage-caption').textContent
  }));
}
async function jumpInto(page, ratio) {
  await page.evaluate(ratio => {
    const section = document.querySelector('#experience');
    window.scrollTo({top: section.offsetTop +
      Math.floor((section.offsetHeight - window.innerHeight) * ratio),
      behavior: 'instant'});
  }, ratio);
  await page.waitForTimeout(220);
}

let browser;
const issues = [];
const checks = [];
try {
  await ready();
  browser = await chromium.launch({headless: true, args: ['--no-sandbox']});
  const desktop = await browser.newContext({viewport: {width: 1440, height: 900},
    reducedMotion: 'no-preference'});
  const page = await desktop.newPage();
  page.on('pageerror', e => issues.push('desktop pageerror: ' + e.message));
  page.on('console', m => {if (m.type() === 'error') issues.push('desktop console: ' + m.text())});
  const response = await page.goto(host + '/index.html', {waitUntil: 'networkidle'});
  assert.equal(response.status(), 200);
  assert.match(await page.title(), /The Shape of Motion/);
  assert.equal(await page.locator('#scroll-progress').getAttribute('role'), 'progressbar');
  checks.push('Desktop site loads, has a title and accessible progress element');
  await page.screenshot({path: path.join(evidence, 'desktop-hero.png'), fullPage: false});
  await jumpInto(page, 0.12);
  const early = await motionProbe(page);
  await jumpInto(page, 0.86);
  const late = await motionProbe(page);
  assert(early.progress < 30, 'Early progress not near start: ' + early.progress);
  assert(late.progress > 70, 'Late progress not near end: ' + late.progress);
  assert.notEqual(early.transform, late.transform);
  assert.equal(late.motion, 'enabled');
  assert.equal(String(late.progress), late.ariaValue);
  await page.screenshot({path: path.join(evidence, 'desktop-reveal.png')});
  await jumpInto(page, 0.10);
  const reversed = await motionProbe(page);
  assert(reversed.progress < late.progress - 50);
  assert.notEqual(reversed.transform, late.transform);
  assert.equal(reversed.motion, 'enabled');
  checks.push('True browser scroll advances and reverses CSS 3D orientation; ARIA value follows');

  const mobile = await browser.newContext({viewport: {width: 390, height: 844},
    isMobile: true, hasTouch: true, deviceScaleFactor: 1, reducedMotion: 'no-preference'});
  const mobilePage = await mobile.newPage();
  mobilePage.on('pageerror', e => issues.push('mobile pageerror: ' + e.message));
  await mobilePage.goto(host + '/index.html', {waitUntil: 'networkidle'});
  await jumpInto(mobilePage, 0.75);
  const mobileMotion = await motionProbe(mobilePage);
  assert(mobileMotion.progress > 65);
  assert(mobileMotion.width <= mobileMotion.viewport + 1,
    'Mobile page overflows horizontally: ' + JSON.stringify(mobileMotion));
  await mobilePage.screenshot({path: path.join(evidence, 'mobile-reveal.png')});
  checks.push('390px mobile viewport scroll interaction and no horizontal overflow');

  const accessible = await browser.newContext({viewport: {width: 1440, height: 900},
    reducedMotion: 'reduce'});
  const accessiblePage = await accessible.newPage();
  accessiblePage.on('pageerror', e => issues.push('reduced-motion pageerror: ' + e.message));
  await accessiblePage.goto(host + '/index.html', {waitUntil: 'networkidle'});
  await jumpInto(accessiblePage, 0.78);
  const reduced = await motionProbe(accessiblePage);
  assert.equal(reduced.motion, 'reduced');
  assert.equal(reduced.transform, '');
  assert.match(reduced.caption, /REDUCED MOTION/);
  const skip = accessiblePage.locator('.skip-link');
  assert.equal(await skip.getAttribute('href'), '#main');
  checks.push('prefers-reduced-motion disables dynamic transform; keyboard skip link exists');
  assert.deepEqual(issues, []);

  const report = {
    evidenceClass: 'REAL_CHROMIUM_QA_CI_ONLY_NOT_PRODUCTION',
    browser: await browser.version(),
    checked: checks,
    desktopEarlyProgress: early.progress,
    desktopLateProgress: late.progress,
    desktopReversedProgress: reversed.progress,
    mobileProgress: mobileMotion.progress,
    reducedMotionObserved: reduced.motion,
    errors: issues,
    ownerPcTested: false,
    webglCertified: false,
    coreWebVitalsMeasured: false,
    deployed: false,
    passed: true
  };
  await fs.writeFile(path.join(evidence, 'qa-report.json'), JSON.stringify(report, null, 2) + '\n');
  console.log('E3_REAL_BROWSER_QA_PASSED', JSON.stringify(report));
} catch (err) {
  const report = {
    evidenceClass: 'REAL_CHROMIUM_QA_CI_ONLY_NOT_PRODUCTION',
    passed: false, checks, errors: issues, failure: String(err),
    ownerPcTested: false, deployed: false
  };
  await fs.writeFile(path.join(evidence, 'qa-report.json'), JSON.stringify(report, null, 2) + '\n');
  throw err;
} finally {
  if (browser) await browser.close();
  server.kill('SIGTERM');
}
