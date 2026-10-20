/** E3.9A real Chromium keyboard accessibility proof; no screenshots-only metrics. */
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import {spawn} from 'node:child_process';
import {setTimeout as wait} from 'node:timers/promises';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';

const mode = process.argv[2];
if (!['before','after'].includes(mode) || process.argv.length !== 3) {
  throw Error('E39_BROWSER_ONLY_BEFORE_OR_AFTER');
}
const folder = path.dirname(fileURLToPath(import.meta.url));
const site = path.resolve(folder, '../../../target/e3-factory/site');
const evidence = path.resolve(folder, '../../../target/e3-factory/e39');
const endpoint = 'http://127.0.0.1:18739';
const server = spawn('python3', ['-m','http.server','18739',
  '--bind','127.0.0.1','--directory',site],
  {stdio: ['ignore','pipe','pipe']});
let browser;
let result = {evidenceClass:'E39_CHROMIUM_KEYBOARD_ACCESSIBILITY',
  caseId:'keyboard-skip-link',passed:false,
  failureCode:'E39_NOT_RUN',focusReached:false,
  visibleWhenFocused:false,geometry:{},
  userPcUsed:false,ownerApprovalGranted:false,deployed:false};
try {
  let serverReady = false;
  for (let i=0;i<35;i++) {
    if (server.exitCode !== null) throw Error('E39_SERVER_EARLY_EXIT');
    try {
      const response = await fetch(endpoint + '/index.html',
        {signal:AbortSignal.timeout(1500)});
      if (response.ok) {serverReady=true;break;}
    } catch { /* fixed loopback server not ready yet */ }
    await wait(180);
  }
  if (!serverReady) throw Error('E39_SERVER_NOT_READY');
  browser = await chromium.launch({headless:true,args:['--no-sandbox']});
  const context = await browser.newContext({viewport:{width:390,height:844},
    reducedMotion:'reduce',hasTouch:false});
  const page = await context.newPage();
  const errors=[];
  page.on('pageerror',err=>errors.push(String(err.message)));
  const response=await page.goto(endpoint + '/index.html',{waitUntil:'networkidle'});
  assert.equal(response.status(),200);
  await page.keyboard.press('Tab');
  // Chromium may report activeElement before the corresponding :focus style
  // has painted. Two animation frames ensure measured geometry reflects CSS,
  // as confirmed by the genuine before/after screenshots in the first run.
  await page.evaluate(() => new Promise(resolve =>
    requestAnimationFrame(() => requestAnimationFrame(resolve))));
  const observed=await page.evaluate(()=>{
    const item=document.querySelector('.skip-link');
    if(!item)return {focusReached:false};
    const rect=item.getBoundingClientRect();
    return {focusReached:document.activeElement===item,
      text:item.textContent?.trim(),href:item.getAttribute('href'),
      top:Math.round(rect.top),bottom:Math.round(rect.bottom),
      left:Math.round(rect.left),right:Math.round(rect.right),
      viewportW:window.innerWidth,viewportH:window.innerHeight};
  });
  result.focusReached=observed.focusReached===true;
  result.geometry={top:observed.top,bottom:observed.bottom,
    left:observed.left,right:observed.right,
    viewportH:observed.viewportH,viewportW:observed.viewportW};
  result.visibleWhenFocused=result.focusReached &&
    observed.top>=0 && observed.bottom<=observed.viewportH &&
    observed.left>=0 && observed.right<=observed.viewportW;
  const sound=result.focusReached && observed.href==='#main' &&
    observed.text?.length>0 && errors.length===0;
  if(!sound)result.failureCode='E39_KEYBOARD_HARNESS_OR_FOCUS_UNEXPECTED';
  else if(!result.visibleWhenFocused)result.failureCode='E39_FOCUSED_SKIP_LINK_OFFSCREEN';
  else {result.failureCode=null;result.passed=true;}
  result.pageJavaScriptErrors=errors;
  await page.screenshot({path:path.join(evidence,'keyboard-'+mode+'.png'),
    animations:'disabled'});
} catch(error) {
  result.failureCode='E39_BROWSER_HARNESS_FAILED';
  result.harnessError=String(error);
} finally {
  if(browser)await browser.close();
  server.kill('SIGTERM');
  await fs.writeFile(path.join(evidence,'browser-'+mode+'.json'),
    JSON.stringify(result,null,2)+'\n');
}
console.log('E39_CHROMIUM_KEYBOARD_RESULT',JSON.stringify(result));
if(!result.passed)process.exitCode=1;
