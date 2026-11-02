/**
 * E6 W5-F: audit REAL browser semantics on HUMAN-AUTHORED mock sites.
 * An injected axe scan is automation, NOT a WCAG 2.2 AA certification.
 * CSS zoom is a controlled proxy, NOT the physical browser zoom UI.
 * Never loads models, private inputs, GPU assets or outside URLs.
 */
import assert from 'node:assert/strict';
import {readFile,mkdir,writeFile} from 'node:fs/promises';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {setTimeout as delay} from 'node:timers/promises';
import {createHash} from 'node:crypto';
import {dirname,join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';
import AxeBuilder from '@axe-core/playwright';
import {compileAuthoredSite} from './w5c_restricted_css_compiler.mjs';
import {syntheticDesign} from './w5b_simulated_provider.mjs';
import {EXAMPLE_CODES} from './w5c_test_doubles.mjs';
import {auditedRuleTags,checkAxeResult,checkPageJourney,
 checkKeyboardJourney,checkReflowProxy,summary,proofSha256} from './w5f_accessibility_policy.mjs';
const here=dirname(fileURLToPath(import.meta.url));
const batch=JSON.parse(await readFile(join(here,'w5b_heldout_briefs.json'),'utf8'));
const trustedCss=await readFile(join(here,'w5a_base.css'),'utf8');
const target=join(here,'target/e6-w5f-mock-accessibility');
const servers=[];let browser;
const sha=b=>createHash('sha256').update(b).digest('hex');
async function serve(root,index){
  const port=19370+index,origin='http://127.0.0.1:'+port;
  const p=spawn('python3',['-m','http.server',String(port),'--bind','127.0.0.1',
    '--directory',root],{stdio:'ignore'});
  servers.push(p);
  for(let attempt=0;attempt<70;attempt++){
    if(p.exitCode!==null)throw Error('E6_W5F_MOCK_SERVER_EXIT');
    try{
      if((await fetch(origin+'/',{signal:AbortSignal.timeout(1200)})).status===200)return origin;
    }catch{}
    await delay(120);
  }
  throw Error('E6_W5F_MOCK_SERVER_TIMEOUT');
}
async function keyboardAudit(page,viewport,path,origin){
  await page.goto(origin+path,{waitUntil:'load'});
  await page.keyboard.press('Tab');
  const skip=await page.evaluate(()=>{
    const active=document.activeElement;
    const box=active?.getBoundingClientRect();
    const style=active&&getComputedStyle(active);
    return {
      isSkip:active?.matches?.('a.skip[href="#main"]')===true,
      visible:Boolean(box&&box.width>0&&box.height>0&&box.top>=0),
      hasOutline:Boolean(style&&style.outlineStyle!=='none'&&parseFloat(style.outlineWidth)>=2)
    };
  });
  const linkNames=await page.locator('nav a').allTextContents();
  await page.keyboard.press('Enter');
  const entersMain=new URL(page.url()).hash==='#main';
  await page.goto(origin+path,{waitUntil:'load'});
  const dest=path==='/'?'/approach/':'/';
  let found=false,passed=false;
  for(let i=0;i<9;i++){
    await page.keyboard.press('Tab');
    found=await page.evaluate(to=>
      document.activeElement?.getAttribute('href')===to,dest);
    if(found){
      // Keyboard-driven navigation is asynchronous: observe the actual
      // destination commit/load rather than reading the old URL immediately.
      try{
        await Promise.all([
          page.waitForURL(origin+dest,{timeout:5000,waitUntil:'load'}),
          page.keyboard.press('Enter')
        ]);
        passed=new URL(page.url()).pathname===dest;
      }catch{
        passed=false;
      }
      break;
    }
  }
  return {viewport,path,skipLinkFirstFocusable:skip.isSkip,
    skipLinkVisiblyRevealed:skip.visible,
    skipLinkEnteredMainContent:entersMain,
    focusRingVisible:skip.hasOutline,
    navigateByKeyboardSucceeded:found&&passed,
    navigationTextIsNonempty:linkNames.length===2&&
      linkNames.every(name=>name.trim().length>0),
    keyboardTrapObserved:!found,screenReaderVerified:false};
}
async function checkOne(browser,origin,brief,viewport,width,height,path,rows){
  const ctx=await browser.newContext({
    viewport:{width,height},javaScriptEnabled:true,
    reducedMotion:'reduce',serviceWorkers:'block'
  });
  const page=await ctx.newPage(),remote=[],exceptions=[];
  try{
    await page.route('**/*',route=>{
      const url=route.request().url();
      if(!url.startsWith(origin+'/')){
        remote.push(url);return route.abort();
      }
      return route.continue();
    });
    page.on('pageerror',e=>exceptions.push(String(e)));
    const response=await page.goto(origin+path,{waitUntil:'load'});
    const status=response?.status();
    const facts=await page.evaluate(()=>({
      main:document.querySelectorAll('main').length,
      h1:document.querySelectorAll('h1').length,
      skip:Boolean(document.querySelector('a.skip[href="#main"]')),
      nav:Boolean(document.querySelector('nav[aria-label="Primary"]')),
      scripts:document.querySelectorAll('script').length,
      noIndex:Boolean(document.querySelector('meta[name="robots"]')?.content.includes('noindex')),
      overflow:Math.max(0,document.documentElement.scrollWidth-innerWidth)
    }));
    const scan=await new AxeBuilder({page}).withTags(auditedRuleTags()).analyze();
    const axe=checkAxeResult(scan);
    const body={
      projectSlug:brief.projectSlug,viewport,path,homeOrApproachStatus:status,
      hasExactlyOneMain:facts.main===1,hasExactlyOneH1:facts.h1===1,
      hasSkipLink:facts.skip,hasPrimaryNavigation:facts.nav,
      hasNoAuthorScripts:facts.scripts===0,pageIsNoIndex:facts.noIndex,
      externalRequests:remote.length,browserErrors:exceptions.length,
      horizontalOverflowPx:facts.overflow,axe
    };
    checkPageJourney(body);
    const screenshot=await page.screenshot({animations:'disabled',fullPage:false});
    const name=brief.projectSlug+'-'+viewport+'-'+(path==='/'?'home':'approach')+'.png';
    await writeFile(join(target,name),screenshot,{flag:'wx'});
    const hash=proofSha256(sha(screenshot));
    const keyboard=await keyboardAudit(page,viewport,path,origin);
    if(!keyboard.skipLinkFirstFocusable||!keyboard.skipLinkVisiblyRevealed||
       !keyboard.skipLinkEnteredMainContent||!keyboard.focusRingVisible||
       !keyboard.navigateByKeyboardSucceeded||!keyboard.navigationTextIsNonempty||
       keyboard.keyboardTrapObserved)
      console.error('E6_W5F_KEYBOARD_DIAGNOSTIC '+JSON.stringify({slug:brief.projectSlug,keyboard}));
    checkKeyboardJourney(keyboard);
    await page.goto(origin+path,{waitUntil:'load'});
    await page.evaluate(()=>{document.documentElement.style.zoom='2';});
    const zoom=await page.evaluate(()=>({
      overflow:Math.max(0,document.documentElement.scrollWidth-innerWidth),
      contentVisible:Boolean(document.querySelector('h1')?.getBoundingClientRect().width)
    }));
    const reflow={viewport,path,cssZoomScale:2,
      horizontalOverflowPx:zoom.overflow,
      contentRemainsVisible:zoom.contentVisible,
      browserUiZoomVerified:false,ownerDeviceVerified:false};
    checkReflowProxy(reflow);
    assert.deepEqual(remote,[]);
    assert.deepEqual(exceptions,[]);
    rows.push({projectSlug:brief.projectSlug,page:body,keyboard,reflow,
      screenshotName:name,screenshotSha256:hash});
    console.log('E6_W5F_MOCK_ACCESSIBILITY_PASS '+JSON.stringify({
      projectSlug:brief.projectSlug,viewport,path,axeViolationCount:0,
      scannedRules:axe.passedRules,manualReviewItems:axe.needsManualReview,
      keyboardPassed:true,cssZoom2xProxyPassed:true,
      realModelInvoked:false,wcagCertified:false
    }));
  }finally{await ctx.close();}
}
try{
  await mkdir(target,{recursive:false});
  for(let i=0;i<batch.briefs.length;i++){
    const site=compileAuthoredSite(batch.briefs[i],
      syntheticDesign(batch.briefs[i],i),EXAMPLE_CODES[i],trustedCss);
    const root=join(target,String(i));await mkdir(root);
    for(const [path,content]of site.files){
      const file=join(root,path);
      await mkdir(dirname(file),{recursive:true});
      await writeFile(file,content,{flag:'wx'});
    }
  }
  browser=await chromium.launch({headless:true});
  const rows=[];
  for(let i=0;i<batch.briefs.length;i++){
    const brief=batch.briefs[i],origin=await serve(join(target,String(i)),i);
    for(const [view,width,height]of[
      ['compact',320,700],['mobile',390,844],['desktop',1440,900]]){
      for(const route of ['/','/approach/']){
        await checkOne(browser,origin,brief,view,width,height,route,rows);
      }
    }
  }
  const outcome=summary(rows);
  await writeFile(join(target,'evidence.json'),
    JSON.stringify({summary:outcome,rows},null,2)+'\n',{flag:'wx'});
  console.log('E6_W5F_A11Y_MATRIX_ALL_MOCK_CASES_PASS '+JSON.stringify({
    cases:outcome.uniqueJourneys,manualReviewItems:outcome.manualReviewItems,
    realModelTested:false,screenReaderTested:false,productionGo:false}));
}finally{
  if(browser)await browser.close();
  for(const p of servers)if(p.exitCode===null&&p.signalCode===null){
    p.kill('SIGTERM');await once(p,'exit');
  }
}
