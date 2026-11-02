/**
 * E6 W5-D: OWNER-LOCAL visual review of previously generated W5-C sites.
 * No inference or remote network requests. This script cannot establish
 * artistic originality, field Core Web Vitals or real model-weight execution.
 *
 * Run ONLY after W5-C owner-local evaluation succeeds on all 3 briefs.
 */
import {readFile,writeFile,mkdir,lstat} from 'node:fs/promises';
import {join,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {setTimeout as delay} from 'node:timers/promises';
import {chromium} from 'playwright';
import AxeBuilder from '@axe-core/playwright';
import {auditedRuleTags,checkAxeResult} from './w5f_accessibility_policy.mjs';
import {inspectTrial} from './w5d_trial_integrity.mjs';
const here=dirname(fileURLToPath(import.meta.url));
const parent=join(here,'target');
const trial=join(parent,'e6-w5c-local-css-research');
const resultRoot=join(parent,'e6-w5d-local-owner-browser-evidence');
const sha=x=>createHash('sha256').update(x).digest('hex');
const refuse=s=>{throw Error('E6_W5D_'+s);};
let browser;
const servers=[];
async function launchLocal(site,index){
  const port=19330+index,origin='http://127.0.0.1:'+port;
  const proc=spawn('python3',['-m','http.server',String(port),'--bind','127.0.0.1',
    '--directory',site],{stdio:'ignore'});
  servers.push(proc);
  for(let i=0;i<80;i++){
    if(proc.exitCode!==null)refuse('STATIC_SERVER_FAILED');
    try{
      if((await fetch(origin+'/',{signal:AbortSignal.timeout(1200)})).status===200)
        return origin;
    }catch{}
    await delay(125);
  }
  refuse('STATIC_SERVER_UNAVAILABLE');
}
async function verifyKeyboardFocus(page,expectedRoute){
  // The candidate itself has JavaScript disabled. This verifies browser-native
  // keyboard focus; a URL hash or visual scroll alone never qualifies.
  await page.keyboard.press('Tab');
  const skip=await page.evaluate(()=>{
    const active=document.activeElement;
    const rect=active?.getBoundingClientRect();
    const style=active&&getComputedStyle(active);
    return {
      firstTabIsSkip:active?.matches?.('a.skip[href="#main"]')===true,
      skipVisible:Boolean(rect&&rect.width>0&&rect.height>0&&rect.top>=0),
      focusOutlineVisible:Boolean(style&&style.outlineStyle!=='none'&&
        parseFloat(style.outlineWidth)>=2)
    };
  });
  if(!skip.firstTabIsSkip||!skip.skipVisible||!skip.focusOutlineVisible)
    refuse('KEYBOARD_SKIP_FOCUS_MISSING');
  await page.keyboard.press('Enter');
  const hashReached=new URL(page.url()).hash==='#main';
  const mainFocused=await page.waitForFunction(()=>
    document.activeElement===document.querySelector('main#main')&&
    document.activeElement.getAttribute('tabindex')==='-1',null,
    {timeout:2_000}).then(()=>true,()=>false);
  await page.keyboard.press('Tab');
  const focusReleased=await page.evaluate(()=>
    document.activeElement!==document.querySelector('main#main'));
  if(!hashReached||!mainFocused||!focusReleased||
     new URL(page.url()).pathname!==expectedRoute)
    refuse('KEYBOARD_MAIN_FOCUS_MISSING');
  return {firstTabIsSkip:true,skipVisible:true,focusOutlineVisible:true,
    hashReached:true,mainFocused:true,mainTabIndexMinusOne:true,
    focusReleased:true,authorJavascriptEnabled:false,
    assistiveTechnologySpeechVerified:false,wcagCertified:false};
}
async function auditCandidateRoutes(origin,width,height){
  // The production candidate stays JS-disabled in the primary reviewer.
  // axe injects TEST-RUNNER JavaScript into separate isolated pages; the
  // generated HTML must contain zero author scripts before injection.
  const ctx=await browser.newContext({
    viewport:{width,height},javaScriptEnabled:true,
    reducedMotion:'reduce',serviceWorkers:'block',deviceScaleFactor:1
  });
  const page=await ctx.newPage(),outside=[],exceptions=[];
  try{
    await page.route('**/*',route=>{
      const url=route.request().url();
      if(!url.startsWith(origin+'/')){outside.push(url);return route.abort();}
      return route.continue();
    });
    page.on('pageerror',e=>exceptions.push(String(e)));
    const evidence={};
    for(const [route,field] of [['/','home'],['/approach/','approach']]){
      const response=await page.goto(origin+route,{waitUntil:'load',timeout:30_000});
      if(response?.status()!==200||await page.locator('script').count()!==0)
        refuse('GENERATED_ACCESSIBILITY_SOURCE_CONTRACT_FAILED');
      let data;
      try{
        data=checkAxeResult(await new AxeBuilder({page})
          .withTags(auditedRuleTags()).analyze());
      }catch{
        refuse('GENERATED_ACCESSIBILITY_AXE_CHECK_FAILED');
      }
      // Preserve the candidate's CSP (style-src 'self') and saved files.
      // An inline addStyleTag would be blocked by CSP. The isolated browser
      // substitutes ONLY the same-origin CSS response, then restores routing.
      // This is simulated 200% root-font resize, NOT real browser UI zoom.
      const baseline=await page.evaluate(()=>({
        root:parseFloat(getComputedStyle(document.documentElement).fontSize),
        body:parseFloat(getComputedStyle(document.body).fontSize)
      }));
      const cssUrl=origin+'/assets/site.css';
      await page.route(cssUrl,async route=>{
        if(route.request().url()!==cssUrl)
          return route.abort();
        const response=await route.fetch({maxRedirects:0,timeout:10000});
        const raw=await response.body();
        if(response.status()!==200||raw.length<1||raw.length>25_000)
          return route.abort();
        return route.fulfill({response,
          body:Buffer.concat([raw,Buffer.from(
            String.fromCharCode(10)+'html {font-size:200% !important}')])});
      });
      let resized;
      try{
        const reloaded=await page.reload({waitUntil:'load',timeout:30000});
        if(reloaded?.status()!==200||await page.locator('script').count()!==0)
          refuse('TEXT_RESIZE_RELOAD_FAILED');
        resized=await page.evaluate(()=>({
        root:parseFloat(getComputedStyle(document.documentElement).fontSize),
        body:parseFloat(getComputedStyle(document.body).fontSize),
        overflow:Math.max(0,document.documentElement.scrollWidth-innerWidth),
        mainVisible:(()=>{
          const el=document.querySelector('main');
          const rect=el?.getBoundingClientRect();
          return !!rect&&rect.width>0&&rect.height>0;
        })(),
        headingVisible:(()=>{
          const el=document.querySelector('h1');
          const rect=el?.getBoundingClientRect();
          return !!rect&&rect.width>0&&rect.height>0;
        })(),
        navigationLinksVisible:[...document.querySelectorAll('.nav a')]
          .length>=2&&[...document.querySelectorAll('.nav a')]
          .every(el=>el.getClientRects().length>0),
        overflowSelectors:(()=>{
          const names=['.brand','.nav','.header','.hero__type',
            '.hero__figure','h1','h2','.section__content',
            '.section__text','.footer'];
          return names.filter(name=>[...document.querySelectorAll(name)]
            .some(el=>el.getBoundingClientRect().right>innerWidth+2));
        })()
        }));
      }finally{
        await page.unroute(cssUrl);
      }
      const rootRatio=resized.root/baseline.root;
      const bodyRatio=resized.body/baseline.body;
      if(!Number.isFinite(rootRatio)||!Number.isFinite(bodyRatio)||
         Math.abs(rootRatio-2)>.05||Math.abs(bodyRatio-2)>.05)
        refuse('TEXT_RESIZE_SCALE_FAILED');
      if(!Number.isFinite(resized.overflow)||resized.overflow>2){
        // Bounded geometry only. Do not log model text or untrusted HTML.
        console.error('E6_W5D_TEXT_RESIZE_OVERFLOW_METRIC '+JSON.stringify({
          route:field,width,height,
          overflowPx:Number.isFinite(resized.overflow)?Math.ceil(resized.overflow):null,
          structuralSelectors:resized.overflowSelectors
        }));
        refuse('TEXT_RESIZE_HORIZONTAL_OVERFLOW');
      }
      if(!resized.mainVisible||!resized.headingVisible||
         !resized.navigationLinksVisible)
        refuse('TEXT_RESIZE_CONTENT_MISSING');
      const textResize200Percent={
        technique:'CHROMIUM_SAME_ORIGIN_CSS_RESPONSE_ROOT_RESIZE',
        rootFontRatio:rootRatio,bodyFontRatio:bodyRatio,
        horizontalOverflowPx:resized.overflow,
        mainVisible:true,headingVisible:true,navigationLinksVisible:true,
        browserUiZoomVerified:false,screenReaderVerified:false,
        ownerDeviceVerified:false,independentWcagCertified:false
      };
      evidence[field]={
        textResize200Percent,
        automatedRuleScanPassed:true,
        violations:data.violations,
        passedRules:data.passedRules,
        needsManualReview:data.needsManualReview,
        independentWcagCertified:false,
        screenReaderVerified:false,
        generatedAuthorJavaScript:false
      };
    }
    if(outside.length||exceptions.length)
      refuse('GENERATED_ACCESSIBILITY_OUTSIDE_NETWORK_OR_ERROR');
    return evidence;
  }finally{await ctx.close();}
}
async function visit(origin,slug,width,height){
  const ctx=await browser.newContext({
    viewport:{width,height},javaScriptEnabled:false,serviceWorkers:'block',
    reducedMotion:'reduce',deviceScaleFactor:1
  });
  const page=await ctx.newPage(),remote=[],errors=[];
  try{
    await page.route('**/*',route=>{
      const url=route.request().url();
      if(!url.startsWith(origin+'/')){remote.push(url);return route.abort();}
      return route.continue();
    });
    page.on('pageerror',error=>errors.push(String(error)));
    const load=await page.goto(origin+'/',{waitUntil:'load',timeout:30_000});
    if(load?.status()!==200)refuse('HOME_HTTP_ERROR');
    const stylesheet=await page.request.get(origin+'/assets/site.css');
    if(stylesheet.status()!==200)refuse('CSS_NOT_AVAILABLE');
    if(await page.locator('main').count()!==1||
       await page.locator('h1').count()!==1||
       await page.locator('section.section').count()<2||
       await page.locator('script').count()!==0)
      refuse('PAGE_SEMANTIC_CONTRACT_FAILED');
    // With intentionally disabled JS, basic navigation and responsive CSS
    // must remain fully functional. Screenshots are subject to human review.
    const shot=await page.screenshot({fullPage:true,animations:'disabled'});
    const file=slug+'-'+width+'x'+height+'.png';
    await writeFile(join(resultRoot,file),shot,{flag:'wx',mode:0o600});
    const ui=await page.evaluate(()=>({
      overflow:Math.max(0,document.documentElement.scrollWidth-innerWidth),
      font:getComputedStyle(document.body).fontFamily,
      background:getComputedStyle(document.body).backgroundColor,
      h1:document.querySelector('h1')?.textContent?.trim().slice(0,120)??''
    }));
    if(ui.overflow>2||!ui.h1)refuse('MOBILE_OVERFLOW_OR_EMPTY_HEADING');
    const homeKeyboard=await verifyKeyboardFocus(page,'/');
    const automatedAccessibility=await auditCandidateRoutes(origin,width,height);
    // Each website has TWO routes. Do not accept nine home screenshots as
    // evidence of all 18 generated route/viewport compositions.
    await Promise.all([
      page.waitForURL(origin+'/approach/',{waitUntil:'load',timeout:12_000}),
      page.locator('a[href="/approach/"]').click()
    ]);
    const approachState=await page.evaluate(()=>({
      overflow:Math.max(0,document.documentElement.scrollWidth-innerWidth),
      heading:document.querySelector('h1')?.textContent?.trim()??'',
      mainCount:document.querySelectorAll('main').length,
      headingCount:document.querySelectorAll('h1').length,
      scriptCount:document.querySelectorAll('script').length,
      navigationCurrentCount:document.querySelectorAll('a[aria-current="page"]').length
    }));
    if(approachState.overflow>2||!approachState.heading||
       approachState.mainCount!==1||approachState.headingCount!==1||
       approachState.scriptCount!==0||approachState.navigationCurrentCount!==1)
      refuse('APPROACH_PAGE_CONTRACT_FAILED');
    const approachFile=slug+'-approach-'+width+'x'+height+'.png';
    const approachShot=await page.screenshot({fullPage:true,animations:'disabled'});
    await writeFile(join(resultRoot,approachFile),approachShot,{flag:'wx',mode:0o600});
    const approachKeyboard=await verifyKeyboardFocus(page,'/approach/');
    if(remote.length||errors.length)refuse('BROWSER_NETWORK_OR_EXCEPTION');
    return {projectSlug:slug,viewport:[width,height],
      screenshot:file,screenshotSha256:sha(shot),
      approachScreenshot:approachFile,approachScreenshotSha256:sha(approachShot),
      homeHttpStatus:200,approachHttpStatus:200,cssHttpStatus:200,
      noScriptTags:true,externalRequestCount:0,uncaughtExceptions:0,
      horizontalOverflowPx:ui.overflow,
      approachHorizontalOverflowPx:approachState.overflow,
      computedBodyBackground:ui.background,
      computedFontFamily:ui.font,homeHeading:ui.h1,
      approachHeading:approachState.heading,approachNavigationPassed:true,
      homeKeyboard,approachKeyboard,
      homeAutomatedAccessibility:automatedAccessibility.home,
      approachAutomatedAccessibility:automatedAccessibility.approach,
      bothRoutesAutomatedAccessibilityPassed:true,
      bothRoutesKeyboardFocusVerified:true,
      bothFullPageScreenshotsForHumanReview:true,
      physicalGpuFrameTimingMeasured:false,coreWebVitalsCertified:false};
  }finally{await ctx.close();}
}
async function main(){
  if(process.argv.length!==2)refuse('NO_ARBITRARY_ARGUMENTS');
  const parentState=await lstat(parent);
  if(parentState.isSymbolicLink()||!parentState.isDirectory())
    refuse('UNTRUSTED_TARGET_PARENT');
  const heldout=JSON.parse(await readFile(join(here,'w5b_heldout_briefs.json'),'utf8'));
  const {audit,sites}=await inspectTrial(trial,heldout);
  // Directory creation is exclusive; no overwrite of previous evidence.
  await mkdir(resultRoot,{recursive:false,mode:0o700});
  browser=await chromium.launch({headless:true});
  const views=[];
  for(let i=0;i<sites.length;i++){
    const origin=await launchLocal(sites[i],i);
    for(const [width,height] of [[320,700],[390,844],[1440,900]]){
      const entry=await visit(origin,audit.inspectedCases[i].projectSlug,width,height);
      views.push(entry);
      console.log('E6_W5D_OWNER_LOCAL_BROWSER_CANDIDATE '+JSON.stringify({
        slug:entry.projectSlug,viewport:entry.viewport,
        overflowPx:entry.horizontalOverflowPx,modelWeightsAttested:false
      }));
    }
  }
  const report={kind:'E6_W5D_LOCAL_BROWSER_REVIEW_V1',
    sourceIntegrityAudit:audit,profiles:views,
    allNineSoftwareBrowserReviewsPassed:views.length===9,
    bothRoutesCapturedForAllNineProfiles:views.length===9&&
      views.every(x=>x.approachScreenshot&&x.approachScreenshotSha256),
    fullPageScreenshotCount:views.length*2,
    automatedAccessibilityRouteScanCount:views.length*2,
    textResize200PercentRouteScanCount:views.length*2,
    bothRoutesTextResizePassed:views.length===9&&
      views.every(v=>[v.homeAutomatedAccessibility,v.approachAutomatedAccessibility]
        .every(x=>x.textResize200Percent?.rootFontRatio>=1.95&&
          x.textResize200Percent?.bodyFontRatio>=1.95&&
          x.textResize200Percent?.horizontalOverflowPx<=2&&
          x.textResize200Percent?.browserUiZoomVerified===false&&
          x.textResize200Percent?.independentWcagCertified===false)),
    bothRoutesAutomatedAccessibilityPassed:views.length===9&&
      views.every(v=>v.bothRoutesAutomatedAccessibilityPassed===true&&
        v.homeAutomatedAccessibility.automatedRuleScanPassed===true&&
        v.approachAutomatedAccessibility.automatedRuleScanPassed===true&&
        v.homeAutomatedAccessibility.violations===0&&
        v.approachAutomatedAccessibility.violations===0),
    automatedAccessibilityManualReviewItems:views.reduce((sum,v)=>
      sum+v.homeAutomatedAccessibility.needsManualReview+
      v.approachAutomatedAccessibility.needsManualReview,0),
    bothRoutesKeyboardFocusVerified:views.length===9&&
      views.every(v=>v.bothRoutesKeyboardFocusVerified===true&&
        v.homeKeyboard.mainFocused===true&&
        v.approachKeyboard.mainFocused===true),
    providerSelfReportedLoopbackOnly:true,
    independentModelAuthorshipVerified:false,
    visualQualityHumanApproved:false,
    accessibilityCertified:false,
    realDevicePerformanceMeasured:false,
    allContentRightsVerified:false,
    releaseGo:false,stage45Go:false,ownerApproved:false,
    status:'HUMAN_REVIEW_REQUIRED_NO_RELEASE_AUTHORITY'};
  await writeFile(join(resultRoot,'review-report.json'),
    JSON.stringify(report,null,2)+'\n',{flag:'wx',mode:0o600});
  console.log('E6_W5D_OWNER_LOCAL_REVIEWABLE_SITES '+JSON.stringify({
    profiles:views.length,status:report.status,releaseGo:false
  }));
}
try{await main();}
catch(e){console.error('E6_W5D_REVIEW_BLOCKED',
  /^E6_W5D_[A-Z0-9_]+$/.test(e?.message)?e.message:'E6_W5D_UNVERIFIED_FAILURE');
  process.exitCode=1;
}finally{
  if(browser)await browser.close();
  for(const proc of servers)if(proc.exitCode===null&&proc.signalCode===null){
    proc.kill('SIGTERM');await once(proc,'exit');
  }
}
