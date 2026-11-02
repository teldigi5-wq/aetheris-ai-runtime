/** E6 W6: independent, read-only screenshot evidence integrity preflight.
 * Verifies saved screenshot bytes and matrix from W5-D, NOT visual quality,
 * WCAG certification, real model inference, owner signoff, or release.
 * No model/network access, external packages, or write side effects.
 */
import {createHash} from 'node:crypto';
import {readFile,readdir,lstat} from 'node:fs/promises';
import {join,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {inspectTrial} from './w5d_trial_integrity.mjs';

const here=dirname(fileURLToPath(import.meta.url));
const fail=code=>{throw Error('E6_W6_'+code);};
const hex=v=>typeof v==='string'&&/^[0-9a-f]{64}$/.test(v);
const sha=b=>createHash('sha256').update(b).digest('hex');
const sig=Buffer.from([137,80,78,71,13,10,26,10]);
const between=(x,lo,hi)=>typeof x==='number'&&Number.isFinite(x)&&x>=lo&&x<=hi;
const integerBetween=(x,lo,hi)=>Number.isInteger(x)&&x>=lo&&x<=hi;
const viewports=[[320,700],[390,844],[1440,900]];
const sameSet=(actual,expected)=>actual.length===expected.length&&
 new Set(actual).size===expected.length&&expected.every(x=>actual.includes(x));
async function regular(path,max){
 let s;
 try{s=await lstat(path);}catch{fail('MISSING_OR_UNSAFE_EVIDENCE_FILE');}
 if(s.isSymbolicLink()||!s.isFile()||s.size<25||s.size>max)
   fail('MISSING_OR_UNSAFE_EVIDENCE_FILE');
 return readFile(path);
}
async function directory(path){
 let s;
 try{s=await lstat(path);}catch{fail('MISSING_OR_UNSAFE_DIRECTORY');}
 if(s.isSymbolicLink()||!s.isDirectory())fail('MISSING_OR_UNSAFE_DIRECTORY');
}
export async function verifyScreenshotEvidence(root,heldout,{trialRoot}={}){
 if(typeof root!=='string'||!root||
    !Array.isArray(heldout?.briefs)||heldout.briefs.length!==3)
   fail('INVALID_FIXED_REVIEW_CONTEXT');
 const slugs=heldout.briefs.map(v=>v?.projectSlug);
 if(slugs.some(v=>typeof v!=='string'||!/^[a-z][a-z0-9-]{0,47}$/.test(v))||
    new Set(slugs).size!==3)fail('INVALID_FIXED_REVIEW_CONTEXT');
 await directory(root);
 const raw=await regular(join(root,'review-report.json'),240_000);
 let report;
 try{report=JSON.parse(raw.toString('utf8'));}catch{fail('MALFORMED_BROWSER_REVIEW');}
 if(!report||report.kind!=='E6_W5D_LOCAL_BROWSER_REVIEW_V1'||
    !Array.isArray(report.profiles)||report.profiles.length!==9||
    report.fullPageScreenshotCount!==18||
    report.automatedAccessibilityRouteScanCount!==18||
    report.textResize200PercentRouteScanCount!==18||
    report.localNavigationLabRouteCount!==18||
    report.localNavigationLabBudgetsPassed!==true||
    report.allNineSoftwareBrowserReviewsPassed!==true||
    report.bothRoutesCapturedForAllNineProfiles!==true||
    report.bothRoutesKeyboardFocusVerified!==true||
    report.bothRoutesAutomatedAccessibilityPassed!==true||
    report.bothRoutesTextResizePassed!==true||
    report.sourceIntegrityAudit?.allCandidateFilesHashVerified!==true||
    report.sourceIntegrityAudit?.acceptedSiteCount!==3||
    report.visualQualityHumanApproved!==false||
    report.accessibilityCertified!==false||
    report.realDevicePerformanceMeasured!==false||
    report.allContentRightsVerified!==false||
    report.ownerApproved!==false||report.releaseGo!==false||
    report.stage45Go!==false||
    report.status!=='HUMAN_REVIEW_REQUIRED_NO_RELEASE_AUTHORITY')
    fail('UNVERIFIED_BROWSER_REVIEW');
 // Reopen the original, previously saved case files before publishing review
 // screenshots. A report's embedded source audit is not independent evidence
 // that those same files still exist and have the reviewed bytes.
 if(typeof trialRoot!=='string'||!trialRoot)
   fail('SOURCE_REAUDIT_CONTEXT_REQUIRED');
 let fresh;
 try{fresh=(await inspectTrial(trialRoot,heldout)).audit;}
 catch{fail('SAVED_SOURCE_REAUDIT_REJECTED');}
 if(sha(Buffer.from(JSON.stringify(fresh)))!==
    sha(Buffer.from(JSON.stringify(report.sourceIntegrityAudit))))
   fail('SAVED_SOURCE_AUDIT_CHANGED');
 const expectedKeys=slugs.flatMap(slug=>viewports.map(([width,height])=>
   slug+':'+width+'x'+height));
 const observedKeys=[],images=[];
 for(const v of report.profiles){
   if(!slugs.includes(v?.projectSlug)||!Array.isArray(v.viewport)||
      v.viewport.length!==2||!Number.isInteger(v.viewport[0])||
      !Number.isInteger(v.viewport[1])||
      v.externalRequestCount!==0||v.uncaughtExceptions!==0||
      v.noScriptTags!==true||v.homeHttpStatus!==200||
      v.approachHttpStatus!==200||v.cssHttpStatus!==200||
      v.approachNavigationPassed!==true||
      v.bothRoutesAutomatedAccessibilityPassed!==true||
      v.bothRoutesKeyboardFocusVerified!==true||
      !between(v.horizontalOverflowPx,0,2)||
      !between(v.approachHorizontalOverflowPx,0,2))
     fail('UNVERIFIED_BROWSER_PROFILE');
   const [width,height]=v.viewport;
   observedKeys.push(v.projectSlug+':'+width+'x'+height);
   for(const scan of [v.homeAutomatedAccessibility,v.approachAutomatedAccessibility]){
     const z=scan?.textResize200Percent;
     if(scan?.automatedRuleScanPassed!==true||scan.violations!==0||
        !integerBetween(scan.passedRules,1,500)||
        !integerBetween(scan.needsManualReview,0,500)||
        scan.generatedAuthorJavaScript!==false||
        scan.independentWcagCertified!==false||scan.screenReaderVerified!==false||
        z?.technique!=='CHROMIUM_SAME_ORIGIN_CSS_RESPONSE_ROOT_RESIZE'||
        !between(z.rootFontRatio,1.95,2.05)||
        !between(z.bodyFontRatio,1.95,2.05)||
        !between(z.horizontalOverflowPx,0,2)||
        z.mainVisible!==true||z.headingVisible!==true||
        z.navigationLinksVisible!==true||
        z.browserUiZoomVerified!==false||z.ownerDeviceVerified!==false||
        z.independentWcagCertified!==false)fail('UNVERIFIED_TEXT_RESIZE');
     const nav=scan.localNavigation;
     if(nav?.technique!=='ACTUAL_LOCAL_CHROMIUM_NAVIGATION_AND_STATIC_BYTES'||
        nav.sameOriginLocalOnly!==true||
        !integerBetween(nav.htmlDocumentBytes,500,50_000)||
        !integerBetween(nav.cssStylesheetBytes,1000,25_000)||
        !between(nav.responseEndMs,0,30_000)||
        !between(nav.domContentLoadedMs,nav.responseEndMs,30_000)||
        !between(nav.loadEventEndMs,nav.domContentLoadedMs,30_000)||
        nav.fieldCoreWebVitalsMeasured!==false||
        nav.physicalDevicePerformanceMeasured!==false||
        nav.productionPerformanceCertified!==false)
       fail('UNVERIFIED_LAB_NAVIGATION');
   }
   const names=[
     [v.screenshot,v.screenshotSha256,v.projectSlug+'-'+width+'x'+height+'.png'],
     [v.approachScreenshot,v.approachScreenshotSha256,
       v.projectSlug+'-approach-'+width+'x'+height+'.png']
   ];
   for(const [name,digest,expected] of names){
     if(name!==expected||!hex(digest))fail('SCREENSHOT_IDENTITY_MISMATCH');
     images.push({name,digest,width});
   }
 }
 if(!sameSet(observedKeys,expectedKeys)||images.length!==18||
    new Set(images.map(x=>x.name)).size!==18)
   fail('INCOMPLETE_OR_DUPLICATE_VIEWPORT_MATRIX');
 const expectedFiles=['review-report.json',...images.map(v=>v.name)].sort();
 const diskFiles=(await readdir(root)).sort();
 if(!sameSet(diskFiles,expectedFiles))
   fail('EXTRA_OR_MISSING_REVIEW_ARTIFACT');
 for(const {name,digest,width} of images){
   const raw=await regular(join(root,name),32_000_000);
   if(raw.length<=500||!raw.subarray(0,8).equals(sig)||
      raw.readUInt32BE(8)!==13||
      raw.toString('ascii',12,16)!=='IHDR'||
      raw.readUInt32BE(16)!==width||
      raw.readUInt32BE(20)<1)
     fail('INVALID_PNG_STRUCTURE_OR_VIEWPORT');
   if(sha(raw)!==digest)fail('SCREENSHOT_SHA256_MISMATCH');
 }
 return Object.freeze({
   kind:'E6_W6_SAVED_SCREENSHOT_INTEGRITY_V1',
   profileCount:9,verifiedScreenshots:18,
   screenshotBytesHashVerified:true,
   savedSourceReauditHashVerified:true,measuredBrowserFieldsFinite:true,
   syntheticOrRealProviderNotAttested:true,
   humanVisualApproved:false,actualBrowserUiZoomVerified:false,
   screenReaderVerified:false,independentWcagCertified:false,
   releaseGo:false,stage45Go:false
 });
}
async function main(){
 if(process.argv.length!==2)fail('NO_ARBITRARY_ARGS');
 const batch=JSON.parse(await readFile(join(here,'w5b_heldout_briefs.json'),'utf8'));
 const receipt=await verifyScreenshotEvidence(
  join(here,'target/e6-w5d-local-owner-browser-evidence'),batch,{
   trialRoot:join(here,'target/e6-w5c-local-css-research')
  });
 console.log('E6_W6_SAVED_SCREENSHOTS_VERIFIED_NO_RELEASE '+JSON.stringify(receipt));
}
if(process.argv[1]&&fileURLToPath(import.meta.url)===process.argv[1])
 main().catch(e=>{
   const m=String(e?.message||'');
   console.error('E6_W6_SCREENSHOT_AUDIT_BLOCKED',
     /^E6_W6_[A-Z0-9_]+$/.test(m)?m:'E6_W6_UNVERIFIED_FAILURE');
   process.exitCode=1;
 });
