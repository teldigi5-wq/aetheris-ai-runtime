/** E3.2 independent real-Chromium contract checks for three frozen faults.
 * A failure is valid only if the specified browser observation is violated.
 * Generated evidence is test-only and NOT cryptographically authenticated.
 */
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import {spawn} from 'node:child_process';
import {setTimeout as pause} from 'node:timers/promises';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';

const cases = {
  scroll: 'E32_SCROLL_PROGRESS_STUCK',
  mobile: 'E32_MOBILE_HORIZONTAL_OVERFLOW',
  reduced: 'E32_REDUCED_MOTION_DISABLED'
};
const name = process.argv[2];
if (!Object.hasOwn(cases, name) || process.argv.length !== 3) {
  throw Error('E32_BROWSER_CASE_NOT_ALLOWLISTED');
}
const dir = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(dir, '../../..');
const site = path.join(repo, 'target/e3-factory/site');
const evidence = path.join(repo, 'target/e3-factory/e32', name);
const port = 18736;
const address = 'http://127.0.0.1:' + port;
const server = spawn('python3', ['-m', 'http.server', String(port),
  '--bind', '127.0.0.1', '--directory', site], {stdio: ['ignore', 'pipe', 'pipe']});
const faults = [];
let browser;
let page;
let result = {evidenceClass: 'E32_REAL_CHROMIUM_FAULT_CONTRACT',
  caseId: name, expectedFailureCode: cases[name], passed: false,
  failureCode: 'E32_TEST_DID_NOT_COMPLETE', browserObserved: {},
  physicalOwnerPCUsed: false, deployed: false, ownerApprovalGranted: false};
async function ready() {
  for (let i = 0; i < 40; i++) {
    if (server.exitCode !== null) throw Error('E32_HTTP_FIXTURE_EXITED');
    try {
      const res = await fetch(address + '/index.html', {signal: AbortSignal.timeout(1500)});
      if (res.status === 200) return;
    } catch { /* wait for fixed localhost fixture */ }
    await pause(150);
  }
  throw Error('E32_HTTP_FIXTURE_NOT_READY');
}
async function scrollTo(page, progress) {
  await page.evaluate(p => {
    const section = document.querySelector('#experience');
    if (!section) throw Error('E32_EXPERIENCE_SECTION_ABSENT');
    window.scrollTo({top: section.offsetTop +
      Math.floor((section.offsetHeight - window.innerHeight) * p), behavior: 'instant'});
  }, progress);
  await page.waitForTimeout(230);
}
try {
  await ready();
  browser = await chromium.launch({headless: true, args: ['--no-sandbox']});
  const options = name === 'mobile'
    ? {viewport: {width: 390, height: 844}, isMobile: true, hasTouch: true}
    : {viewport: {width: 1440, height: 900},
        reducedMotion: name === 'reduced' ? 'reduce' : 'no-preference'};
  const context = await browser.newContext(options);
  page = await context.newPage();
  page.on('pageerror', err => faults.push(err.message));
  const response = await page.goto(address + '/index.html', {waitUntil: 'networkidle'});
  assert.equal(response.status(), 200, 'E32_HTTP_BAD_STATUS');
  await scrollTo(page, name === 'scroll' ? 0.82 : 0.75);
  const observed = await page.evaluate(() => ({
    progress: Number(document.querySelector('#stage').dataset.progress),
    motion: document.querySelector('#stage').dataset.motion,
    css3dTransform: document.querySelector('#device').style.transform,
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth
  }));
  result.browserObserved = observed;
  if (faults.length) {
    result.failureCode = 'E32_UNEXPECTED_JS_EXCEPTION';
  } else if (name === 'scroll' && !(observed.progress > 70 && observed.css3dTransform.length > 20)) {
    result.failureCode = 'E32_SCROLL_PROGRESS_STUCK';
  } else if (name === 'mobile' && (observed.scrollWidth > 391 || observed.innerWidth > 391)) {
    result.failureCode = 'E32_MOBILE_HORIZONTAL_OVERFLOW';
  } else if (name === 'reduced' && observed.motion !== 'reduced') {
    result.failureCode = 'E32_REDUCED_MOTION_DISABLED';
  } else {
    result.failureCode = null;
    result.passed = true;
  }
  await page.screenshot({path: path.join(evidence,
      result.passed ? 'after-browser.png' : 'before-browser.png'), animations: 'disabled'});
} catch (error) {
  result.failureCode = 'E32_BROWSER_HARNESS_FAILED';
  result.harnessError = String(error);
} finally {
  if (browser) await browser.close();
  server.kill('SIGTERM');
  await fs.writeFile(path.join(evidence, 'diagnosis.json'), JSON.stringify(result, null, 2) + '\n');
}
console.log('E32_CHROMIUM_CONTRACT', JSON.stringify(result));
if (!result.passed) process.exitCode = 1;
