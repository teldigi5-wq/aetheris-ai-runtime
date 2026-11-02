/** W5-G. Real Chromium keyboard focus proof with JavaScript DISABLED.
 * 3 human-written fictional sites x 3 viewport widths x 2 local routes.
 * Does NOT attest models, native screen readers, WCAG certification or release.
 */
import assert from 'node:assert/strict';
import {readFile,mkdir,writeFile} from 'node:fs/promises';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {setTimeout as pause} from 'node:timers/promises';
import {dirname,join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';
import {compileAuthoredSite} from './w5c_restricted_css_compiler.mjs';
import {syntheticDesign} from './w5b_simulated_provider.mjs';
import {EXAMPLE_CODES} from './w5c_test_doubles.mjs';
import {checkSkipTargetMarkup,checkNoJsFocusProof} from './w5g_skip_focus_policy.mjs';
const here=dirname(fileURLToPath(import.meta.url));
const cases=JSON.parse(await readFile(join(here,'w5b_heldout_briefs.json'),'utf8')).briefs;
const css=await readFile(join(here,'w5a_base.css'),'utf8');
const target=join(here,'target/e6-w5g-nojs-focus-mocks');
const servers=[];let browser;
async function serverFor(root,index){
 const port=19410+index,origin='http://127.0.0.1:'+port;
 const p=spawn('python3',['-m','http.server',String(port),'--bind','127.0.0.1',
  '--directory',root],{stdio:'ignore'});
 servers.push(p);
 for(let i=0;i<80;i++){
  if(p.exitCode!==null)throw Error('E6_W5G_LOCAL_SERVER_EXIT');
  try{if((await fetch(origin+'/',{signal:AbortSignal.timeout(1000)})).ok)return origin;}catch{}
  await pause(100);
 }
 throw Error('E6_W5G_LOCAL_SERVER_TIMEOUT');
}
try{
 await mkdir(target,{recursive:false});
 for(let i=0;i<cases.length;i++){
  const out=compileAuthoredSite(cases[i],syntheticDesign(cases[i],i),EXAMPLE_CODES[i],css);
  const root=join(target,String(i));await mkdir(root);
  for(const [path,body] of out.files){
   const file=join(root,path);await mkdir(dirname(file),{recursive:true});
   await writeFile(file,body,{flag:'wx'});
   if(path.endsWith('.html'))checkSkipTargetMarkup(body);
  }
 }
 browser=await chromium.launch({headless:true});
 const rows=[];
 for(let i=0;i<cases.length;i++){
  const origin=await serverFor(join(target,String(i)),i);
  for(const [name,width,height]of[
    ['compact',320,700],['mobile',390,844],['desktop',1440,900]]){
   for(const path of ['/','/approach/']){
    const context=await browser.newContext({javaScriptEnabled:false,
      viewport:{width,height},reducedMotion:'reduce',serviceWorkers:'block'});
    const page=await context.newPage(),external=[],errors=[];
    try{
      await page.route('**/*',route=>{
       const u=route.request().url();
       if(!u.startsWith(origin+'/')){external.push(u);return route.abort();}
       return route.continue();
      });
      page.on('pageerror',e=>errors.push(String(e)));
      assert.equal((await page.goto(origin+path,{waitUntil:'load'}))?.status(),200);
      const initial=await page.evaluate(()=>({
       main:document.querySelectorAll('main#main').length,
       skip:document.querySelectorAll('a.skip[href="#main"]').length,
       overflow:Math.max(0,document.documentElement.scrollWidth-innerWidth)
      }));
      await page.keyboard.press('Tab');
      const first=await page.evaluate(()=>{
       const active=document.activeElement,rect=active?.getBoundingClientRect();
       const s=active&&getComputedStyle(active);
       return {
        skip:active?.matches?.('a.skip[href="#main"]')===true,
        focusVisible:Boolean(rect&&rect.width>0&&rect.height>0&&rect.top>=0&&
         s.outlineStyle!=='none'&&parseFloat(s.outlineWidth)>=2)
       };
      });
      await page.keyboard.press('Enter');
      await page.waitForURL(origin+path+'#main',{timeout:4000});
      const focused=await page.waitForFunction(()=>
       document.activeElement===document.querySelector('main#main'),null,
       {timeout:2000}).then(()=>true,()=>false);
      const mainTabIndex=await page.locator('main#main').getAttribute('tabindex');
      await page.keyboard.press('Tab');
      const moved=await page.evaluate(()=>document.activeElement!==document.querySelector('main#main'));
      const row={
       projectSlug:cases[i].projectSlug,viewport:name,path,
       browserJavaScriptEnabled:false,hasExactlyOneMain:initial.main===1,
       hasExactlyOneSkipLink:initial.skip===1,
       skipWasFirstTabStop:first.skip,
       skipFocusIndicatorVisible:first.focusVisible,
       fragmentNavigatedToMain:new URL(page.url()).hash==='#main',
       activeElementIsMain:focused,
       mainHasTabindexMinusOne:mainTabIndex==='-1',
       tabMovedOffMainWithoutTrap:moved,
       horizontalOverflowPx:initial.overflow,outsideRequests:external.length,
       browserErrors:errors.length,ownerDeviceMeasured:false,
       realModelUsed:false,wcagCertified:false,releaseGo:false
      };
      if(!focused)console.error('E6_W5G_FOCUS_DIAGNOSTIC '+JSON.stringify(row));
      checkNoJsFocusProof(row);
      rows.push(row);
      console.log('E6_W5G_NO_JS_FOCUS_PASS '+JSON.stringify({
       projectSlug:row.projectSlug,viewport:name,path,
       skipFocusOnMain:true,noAuthorJavascript:true,releaseGo:false
      }));
    }finally{await context.close();}
   }
  }
 }
 assert.equal(rows.length,18);
 await writeFile(join(target,'focus-evidence.json'),
  JSON.stringify({kind:'E6_W5G_NO_JS_FOCUS_TEST_DOUBLES_V1',
   journeys:rows,modelCalled:false,ownerApproved:false,releaseGo:false},null,2)+'\n',
  {flag:'wx'});
}finally{
 if(browser)await browser.close();
 for(const p of servers)if(p.exitCode===null&&p.signalCode===null){
  p.kill('SIGTERM');await once(p,'exit');
 }
}
