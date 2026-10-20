/** E3.9G-9 independently measures three PREEXISTING synthetic functional faults.
 * No model output, correctness oracle, or golden source is imported.
 * Tests real mobile/desktop Chromium and captures screenshot/source digests.
 */
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import {spawn} from 'node:child_process';
import {setTimeout as sleep} from 'node:timers/promises';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';

const cases = {
  'mobile-940': {type:'mobile', file:'styles.css'},
  'reduced-boolean': {type:'reduced', file:'main.js'},
  'scroll-24': {type:'scroll', file:'main.js'}
};
const [phase,caseId,slug,arm] = process.argv.slice(2);
const allowedSlug = new Set(['granite4-3b','hermes3-3b']);
const allowedArm = new Set(['targeted','semantic']);
if (!Object.hasOwn(cases,caseId) || phase!=='after' || process.argv.length!==6 ||
    !allowedSlug.has(slug) || !allowedArm.has(arm)) {
  throw Error('E39G11_FIXED_BROWSER_CASE_PHASE_ARM_REQUIRED');
}
const base=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../../../target/e3-factory');
const site=path.join(base,'site');
const output=path.join(base,'e39g11','models',slug,caseId,arm);
const url='http://127.0.0.1:18795/index.html';
const server=spawn('python3',['-m','http.server','18795','--bind',
   '127.0.0.1','--directory',site],{stdio:['ignore','pipe','pipe']});
let browser=null,err=null;
const digest = bytes=>createHash('sha256').update(bytes).digest('hex');
try {
  for(let tries=0,ready=false;!ready;tries++){
    if(tries>=40 || server.exitCode!==null)throw Error('E39G11_LOOPBACK_FIXTURE_NOT_READY');
    try {const r=await fetch(url,{signal:AbortSignal.timeout(1000)});ready=r.status===200;}
    catch {await sleep(130);}
  }
  browser=await chromium.launch({headless:true,args:['--no-sandbox']});
  const sourceSha256=digest(await fs.readFile(path.join(site,cases[caseId].file)));
  for(const [viewport,w,h] of [['mobile',390,844],['desktop',1280,800]]) {
    const context=await browser.newContext({viewport:{width:w,height:h},
      reducedMotion:cases[caseId].type==='reduced'?'reduce':'no-preference',
      hasTouch:false,isMobile:false});
    const page=await context.newPage();
    const pageErrors=[];
    page.on('pageerror',error=>pageErrors.push(String(error.message)));
    const report={evidenceClass:'E39G11_INDEPENDENT_REAL_CHROMIUM_FUNCTIONAL',
      phase,caseId,viewport,modelSlug:phase==='after'?slug:null,
      arm:phase==='after'?arm:null,sourceSha256,
      passed:false,failureCode:'E39G11_TEST_NOT_RUN',pageJavaScriptErrors:pageErrors,
      observed:{},screenshotSha256:null,ownerPcUsed:false,modelWasNotCalledByBrowser:true};
    try {
      const response=await page.goto(url,{waitUntil:'networkidle'});
      assert.equal(response.status(),200);
      const obs=await page.evaluate(async()=>{
        const section=document.querySelector('#experience');
        if(!section)throw Error('E39G11_MISSING_EXPERIENCE');
        window.scrollTo({top: section.offsetTop +
           Math.floor(Math.max(0,section.offsetHeight-window.innerHeight)*.82),
           behavior:'instant'});
        await new Promise(done=>setTimeout(done,200));
        return {
          progress:Number(document.querySelector('#stage')?.dataset.progress),
          motion:document.querySelector('#stage')?.dataset.motion,
          deviceTransform:document.querySelector('#device')?.style.transform ?? '',
          scrollWidth:document.documentElement.scrollWidth,
          innerWidth:innerWidth
        };
      });
      report.observed=obs;
      let passes;
      if(cases[caseId].type==='mobile'){
        passes=obs.scrollWidth<=obs.innerWidth+1 &&
          obs.innerWidth===w;
      } else if(cases[caseId].type==='reduced'){
        passes=obs.motion==='reduced' && obs.deviceTransform==='';
      } else {
        passes=obs.progress>70 && obs.deviceTransform.length>20;
      }
      report.passed=Boolean(passes && pageErrors.length===0);
      report.failureCode=pageErrors.length ? 'E39G11_PAGE_ERROR'
        : report.passed?null:cases[caseId].type==='mobile'?
          'E39G11_MOBILE_REFLOW_OVERFLOW':cases[caseId].type==='reduced'?
          'E39G11_REDUCED_MOTION_IGNORED':'E39G11_SCROLL_PROGRESS_STUCK';
      const screenshot=await page.screenshot({animations:'disabled'});
      await fs.writeFile(path.join(output,viewport+'-'+phase+'.png'),screenshot);
      report.screenshotSha256=digest(screenshot);
    } catch(e) {
      report.failureCode='E39G11_CHROMIUM_HARNESS_FAILURE';
      report.harnessError=String(e);
      err=err||e;
    }
    await fs.writeFile(path.join(output,viewport+'-'+phase+'.json'),
      JSON.stringify(report,null,2)+'\n');
    console.log('E39G11_REAL_CHROMIUM_BROWSER_OBSERVATION',
      caseId,phase,viewport,report.passed,report.failureCode,
      JSON.stringify(report.observed));
    await context.close();
    // After repair failure is a MEASURED model outcome, never a runner failure.
  }
} finally {
  if(browser)await browser.close();
  server.kill('SIGTERM');
}
if(err)throw err;
