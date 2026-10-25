/** B4I: trusted source-only negative/positive controls, real Chromium pixels.
 *
 * This is NOT a general evaluator and MUST NOT load curator/model/customer HTML.
 * No model completions, applying patches, arbitrary paths, network, or releases.
 * The only input is four fixed, reviewed fixtures in this repository.
 */
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';

if (process.argv.length !== 2) throw Error('B4I_NO_EXTERNAL_FILE_ARGUMENTS');
const HERE = path.dirname(fileURLToPath(import.meta.url));
const FIX = path.join(HERE, 'e39g12b4i', 'fixtures');
const OUT = path.resolve(HERE, '../../../target/e3-factory/e39g12b4i-browser');
const CASES = Object.freeze(['focus', 'motion']);
const VARIANTS = Object.freeze(['negative', 'positive']);
const VIEWS = Object.freeze([
  {name:'mobile',width:390,height:844},
  {name:'desktop',width:1280,height:800},
]);
const sha256 = data => createHash('sha256').update(data).digest('hex');
const report = {
  evidenceClass:'E39G12B4I_TRUSTED_STATIC_CHROMIUM_POSITIVE_NEGATIVE_CONTROL',
  referenceIsTrustedFixtureOnly:true,
  independentCuratorVerified:false,
  realModelCalls:0,
  autonomousModelGeneratedCode:false,
  modelRepairSuccessRateAvailable:false,
  modelOutputExecuted:false,
  untrustedSiteServed:false,
  externalRequestsAllowed:false,
  customerWebsiteChanged:false,
  ownerHardwareBenchmarked:false,
  productionAuthorized:false,
  stage45Go:false,
  cases:[],
  checks:[],
  failure:null,
};
let browser;
try {
  await fs.mkdir(OUT,{recursive:true});
  browser = await chromium.launch({headless:true});
  for (const kind of CASES) {
    for (const view of VIEWS) {
      const context=await browser.newContext({
        viewport:{width:view.width,height:view.height},
        reducedMotion:'reduce',
        javaScriptEnabled:false,
        serviceWorkers:'block',
      });
      // Block all network requests even if source is changed accidentally.
      // No URL navigation or local file loading: setContent on about:blank only.
      let requests=0;
      await context.route('**/*', route => { requests += 1; return route.abort(); });
      const page=await context.newPage();
      const errors=[];
      page.on('pageerror', err=>errors.push(String(err.message).slice(0,120)));
      for (const variant of VARIANTS) {
        const name=kind+'-'+variant+'.html';
        const file=path.join(FIX,name);
        const html=await fs.readFile(file,'utf8');
        assert(html.startsWith('<!doctype html>'),'Not an approved static HTML fixture');
        assert(html.length<5000,'Unexpected oversized trusted fixture');
        await page.goto('about:blank');
        await page.setContent(html,{waitUntil:'load',timeout:15000});
        if(kind==='focus') await page.keyboard.press('Tab');
        const state=await page.evaluate((kind)=>{
          const html=document.documentElement;
          const overflow=html.scrollWidth>innerWidth+1;
          if(kind==='focus'){
            const selected=document.activeElement;
            const rect=selected?.getBoundingClientRect();
            return {
              passed:Boolean(selected?.classList.contains('skip') &&
                rect && rect.top>=0 && rect.bottom<=innerHeight &&
                rect.left>=0 && rect.right<=innerWidth),
              overflow,activeTag:selected?.tagName||'',
            };
          }
          const ticker=document.querySelector('#sample-ticker');
          return {
            passed:Boolean(matchMedia('(prefers-reduced-motion:reduce)').matches &&
              ticker && getComputedStyle(ticker).animationName==='none'),
            overflow,activeTag:'',
          };
        },kind);
        assert.equal(state.passed,variant==='positive',
          'Trusted fixture must establish a real '+variant+' browser '+kind+' control');
        assert.equal(state.overflow,false,'Responsive browser overflow');
        assert.equal(requests,0,'Unexpected network request from trusted fixture');
        assert.deepEqual(errors,[],'JavaScript error in static fixture');
        const screenshot=await page.screenshot({animations:'disabled'});
        const screenshotName=kind+'-'+variant+'-'+view.name+'.png';
        await fs.writeFile(path.join(OUT,screenshotName),screenshot,{flag:'wx'});
        report.cases.push({
          kind,variant,viewport:view.name,
          width:view.width,height:view.height,passed:state.passed,
          sourceSha256:sha256(html),screenshotSha256:sha256(screenshot),
          externalRequestCount:requests,pageErrors:errors.length,
          untrustedCodeExecuted:false,modelRepairCredited:false,
        });
      }
      await context.close();
    }
    report.checks.push('Actual '+kind+' Chromium broken control false; trusted fixed control true');
  }
  assert.equal(report.cases.length,8);
  assert(report.cases.every(r => r.externalRequestCount===0));
  console.log('B4I_REAL_CHROMIUM_TRUSTED_CONTROL',JSON.stringify({
    runs:report.cases.length,checks:report.checks,
    modelRepairCredited:false,productionAuthorized:false,
  }));
} catch (error) {
  report.failure=String(error?.stack||error).slice(0,4000);
  throw error;
} finally {
  await fs.mkdir(OUT,{recursive:true});
  await fs.writeFile(path.join(OUT,'report.json'),JSON.stringify(report,null,2)+'\n');
  if(browser) await browser.close();
}
