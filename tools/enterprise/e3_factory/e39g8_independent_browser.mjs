/** E3.9G-8: independent Chromium AFTER check of policy-gated real-model CSS.
 * The browser harness knows NO expected fix bytes. It only checks accessibility
 * semantics and DOM layout after actual browser painting. No model is run.
 */
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {spawn} from 'node:child_process';
import {setTimeout as wait} from 'node:timers/promises';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';

const cases = new Set([
  'top-40','top-120','left-120','left-240','opacity-zero',
  'visibility-hidden','transform-up','transform-left'
]);
const [token] = process.argv.slice(2);
const match = /^(granite4-3b|hermes3-3b)--(top-120|opacity-zero)--(baseline|skill-context)$/.exec(token || '');
if(process.argv.length !== 3 || !match) throw Error('E39G8_FIXED_MODEL_CASE_ARM_ONLY');
const [,modelSlug,caseId,arm] = match;
if(!cases.has(caseId)) throw Error('E39G8_UNAPPROVED_CASE');
const phase='after';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)),
                          '../../../target/e3-factory');
const site = path.join(root,'site');
const folder = path.join(root,'e39g8',modelSlug,caseId,arm);
const url = 'http://127.0.0.1:18743/index.html';
const server = spawn('python3',['-m','http.server','18743','--bind',
                                '127.0.0.1','--directory',site],
                     {stdio:['ignore','pipe','pipe']});
let browser = null;
let error = null;
try {
  let ready=false;
  for (let retry=0;retry<40;retry++) {
    if (server.exitCode !== null) throw Error('E39D_LOCAL_HTTP_SERVER_DIED');
    try {
      const response=await fetch(url,{signal:AbortSignal.timeout(1000)});
      if(response.ok){ready=true;break;}
    } catch { /* localhost readiness only */ }
    await wait(120);
  }
  if(!ready)throw Error('E39D_LOCAL_HTTP_NOT_READY');
  browser=await chromium.launch({headless:true,args:['--no-sandbox']});
  const sourceSha256 = createHash('sha256').update(await fs.readFile(path.join(site,'styles.css'))).digest('hex');
  for(const [viewport,width,height] of [
    ['mobile',390,844],['desktop',1280,800]
  ]) {
    const context=await browser.newContext({
      viewport:{width,height},reducedMotion:'reduce',hasTouch:false
    });
    const page=await context.newPage();
    const pageErrors=[];
    page.on('pageerror',e=>pageErrors.push(String(e.message)));
    const result={evidenceClass:'E39G8_INDEPENDENT_REAL_CHROMIUM_AFTER_MODEL_PATCH',
      arm,modelSlug,sourceSha256,
      caseId,phase,viewport,passed:false,failureCode:'E39D_NOT_EVALUATED',
      focusReached:false,visibleWhenFocused:false,
      pageJavaScriptErrors:pageErrors,geometry:{},
      browserIndependentlyVerifiedModelCall:false,ownerPcUsed:false};
    try {
      const response=await page.goto(url,{waitUntil:'networkidle'});
      assert.equal(response.status(),200);
      await page.keyboard.press('Tab');
      // Styles may paint after activeElement changes; synchronize geometry.
      await page.evaluate(()=>new Promise(done=>
        requestAnimationFrame(()=>requestAnimationFrame(done))));
      const obs=await page.evaluate(()=>{
        const node=document.querySelector('.skip-link');
        if(!node)return {missing:true};
        const box=node.getBoundingClientRect();
        const computed=getComputedStyle(node);
        return {
          focusReached:document.activeElement===node,
          href:node.getAttribute('href'),text:node.textContent?.trim(),
          top:Math.round(box.top),bottom:Math.round(box.bottom),
          left:Math.round(box.left),right:Math.round(box.right),
          width:Math.round(box.width),height:Math.round(box.height),
          viewportW:innerWidth,viewportH:innerHeight,
          opacity:computed.opacity,visibility:computed.visibility,
          display:computed.display,
        };
      });
      result.focusReached=obs.focusReached===true;
      result.geometry={
        top:obs.top,bottom:obs.bottom,left:obs.left,right:obs.right,
        width:obs.width,height:obs.height,
        viewportW:obs.viewportW,viewportH:obs.viewportH
      };
      result.computed={
        opacity:obs.opacity,visibility:obs.visibility,display:obs.display
      };
      const rightElement=obs.href==='#main' && Boolean(obs.text?.length);
      const onScreen=obs.top>=0 && obs.left>=0 &&
        obs.bottom<=obs.viewportH && obs.right<=obs.viewportW &&
        obs.width>0 && obs.height>0;
      const visibleStyle=obs.display!=='none' &&
        obs.visibility==='visible' && Number(obs.opacity)>=0.85;
      result.visibleWhenFocused=Boolean(
        result.focusReached && rightElement && onScreen && visibleStyle);
      if(!rightElement || pageErrors.length) {
        result.failureCode='E39D_BROWSER_HARNESS_OR_ANCHOR_INVALID';
      } else if(!result.focusReached) {
        result.failureCode='E39D_FOCUS_NOT_REACHABLE';
      } else if(!visibleStyle) {
        result.failureCode=Number(obs.opacity)<0.85
          ? 'E39D_FOCUS_TRANSPARENT' : 'E39D_FOCUS_NOT_REACHABLE';
      } else if(!onScreen) {
        result.failureCode='E39D_FOCUS_OFFSCREEN';
      } else {
        result.failureCode=null;result.passed=true;
      }
      const screenshot=await page.screenshot({
        path:path.join(folder,viewport+'-'+phase+'.png'),
        animations:'disabled'
      });
      result.screenshotSha256=createHash('sha256').update(screenshot).digest('hex');
    } catch(e) {
      result.failureCode='E39D_BROWSER_HARNESS_EXCEPTION';
      result.harnessError=String(e);
    }
    await fs.writeFile(path.join(folder,viewport+'-'+phase+'.json'),
                       JSON.stringify(result,null,2)+'\n');
    console.log('E39G8_INDEPENDENT_REAL_CHROMIUM_MODEL_PATCH_VERDICT',
                JSON.stringify({caseId,phase,viewport,
                  passed:result.passed,code:result.failureCode,
                  geometry:result.geometry,computed:result.computed}));
    await context.close();
    // A failed proposed patch is a measured negative result, NOT a CI bypass.
    // The scorer independently checks the real per-viewport receipt.
  }
} catch(e) {
  error=error||e;
} finally {
  if(browser)await browser.close();
  server.kill('SIGTERM');
}
if(error)throw error;
