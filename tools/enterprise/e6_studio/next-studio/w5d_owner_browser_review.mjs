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
  /^E6_W5D_[A-Z_]+$/.test(e?.message)?e.message:'E6_W5D_UNVERIFIED_FAILURE');
  process.exitCode=1;
}finally{
  if(browser)await browser.close();
  for(const proc of servers)if(proc.exitCode===null&&proc.signalCode===null){
    proc.kill('SIGTERM');await once(proc,'exit');
  }
}
