/** E3.9G-12B3 independent real Chromium verifier. Reads no golden/code policy.
 * A model patch is counted only after a separate trusted scorer checks TWO
 * real after-browser viewports. Unsafe-source reference controls never served.
 */
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import {spawn} from 'node:child_process';
import {setTimeout as pause} from 'node:timers/promises';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import {chromium} from 'playwright';

const HERE=path.dirname(fileURLToPath(import.meta.url));
const ROOT=path.resolve(HERE,'../../../target/e3-factory/e39g12b3/repairs');
const PAIRS={
  'studynest--keyboard-focus-visibility':['studynest','keyboard-focus-visibility'],
  'studynest--responsive-mobile-overflow':['studynest','responsive-mobile-overflow'],
  'cityline--reduced-motion':['cityline','reduced-motion'],
  'cityline--safe-untrusted-text':['cityline','safe-untrusted-text']
};
const token=process.argv[2];
if(process.argv.length!==3 || !Object.hasOwn(PAIRS,token))
  throw Error('E39G12B3_FIXED_SITE_FAULT_ONLY');
const [site,fault]=PAIRS[token];
const folder=path.join(ROOT,token);
const html=await fs.readFile(path.join(folder,'index.html'));
if(html.length<1000||html.length>14000)throw Error('E39G12B3_INVALID_APPLIED_SITE');
const digest=x=>createHash('sha256').update(x).digest('hex');
const sourceSha256=digest(html);
const server=spawn('python3',['-m','http.server','18798','--bind','127.0.0.1',
                              '--directory',ROOT],{stdio:['ignore','pipe','pipe']});
let browser;
async function ready(){
 for(let i=0;i<45;i++){
  if(server.exitCode!==null)throw Error('E39G12B3_LOCAL_BROWSER_SERVER_EXIT');
  try{
   const r=await fetch('http://127.0.0.1:18798/'+token+'/index.html',
             {signal:AbortSignal.timeout(1800)});
   if(r.ok)return;
  }catch{}
  await pause(170);
 }
 throw Error('E39G12B3_TEST_SERVER_NOT_READY');
}
try{
 await ready();
 browser=await chromium.launch({headless:true});
 const inert='<b data-e39g12b-literal="true">USER_INPUT</b>';
 for(const [viewport,width,height] of [['mobile',390,844],['desktop',1280,800]]){
  const context=await browser.newContext({viewport:{width,height},reducedMotion:'reduce'});
  const page=await context.newPage();
  const errors=[];
  page.on('pageerror',err=>errors.push(String(err.message).slice(0,180)));
  const url='http://127.0.0.1:18798/'+token+'/index.html'
     +(site==='cityline'?'?q='+encodeURIComponent(inert):'');
  try{
   const response=await page.goto(url,{waitUntil:'load',timeout:15000});
   assert.equal(response?.status(),200);
   const checks={};
   if(site==='studynest'){
    await page.keyboard.press('Tab');
    Object.assign(checks,await page.evaluate(()=>{
     const el=document.activeElement,rc=el?.getBoundingClientRect();
     return {
      'keyboard-focus-visibility':Boolean(el?.matches('.skip')&&rc&&
       rc.top>=0&&rc.bottom<=innerHeight&&rc.left>=0&&rc.right<=innerWidth),
      'responsive-mobile-overflow':document.documentElement.scrollWidth<=innerWidth+1
     };
    }));
   }else{
    Object.assign(checks,await page.evaluate(inert=>{
     const ticker=document.querySelector('#service-ticker');
     const q=document.querySelector('#query-output');
     return {
      'reduced-motion':matchMedia('(prefers-reduced-motion:reduce)').matches&&
       getComputedStyle(ticker).animationName==='none',
      'safe-untrusted-text':!q?.querySelector('[data-e39g12b-literal]')&&
       q?.textContent?.includes(inert)===true
     };
    },inert));
   }
   assert.equal(typeof checks[fault],'boolean');
   const screenshot=await page.screenshot({path:path.join(folder,viewport+'-after.png'),
       fullPage:false,animations:'disabled'});
   const result={
    evidenceClass:'E39G12B3_INDEPENDENT_REAL_CHROMIUM_MODEL_AFTER',
    token,site,fault,viewport,width,height,sourceSha256,
    screenshotSha256:digest(screenshot),contracts:checks,
    targetContractPassed:checks[fault]===true,
    pageJavaScriptErrors:errors,
    modelRepairClaimed:false,productionPublished:false,ownerPcUsed:false,
    realBrowserRuns:1,actualModelCompletionSelfReportedOnly:true
   };
   await fs.writeFile(path.join(folder,viewport+'-after.json'),
                      JSON.stringify(result,null,2)+'\n',{flag:'wx'});
   console.log('E39G12B3_REAL_BROWSER_AFTER_MODEL',token,viewport,
               'targetPassed',result.targetContractPassed,'jsErrors',errors.length);
  }finally{await context.close();}
 }
}finally{
 if(browser)await browser.close();
 server.kill('SIGTERM');
}
