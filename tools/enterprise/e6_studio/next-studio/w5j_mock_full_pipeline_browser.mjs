/** E6 W5-J — FULL browser acceptance dry-run, deliberately FAKE MODEL DATA.
 * Test the real W5-C file layout, W5-J hash receipt, W5-D source auditor
 * and W5-D two-route Chromium screenshots without downloading any model.
 * DO NOT upload this fixture or claim actual Ollama execution.
 */
import assert from 'node:assert/strict';
import {readFile,writeFile,mkdir,lstat} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {dirname,join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {evaluateCodeHeldout} from './w5c_local_code_trial.mjs';
import {fakeCssSourceProvider} from './w5c_test_doubles.mjs';
import {MODEL} from './w5b_simulated_provider.mjs';
import {summarizeObservedTrial} from './w5j_cpu_trial_gate.mjs';
import {inspectTrial} from './w5d_trial_integrity.mjs';
const here=dirname(fileURLToPath(import.meta.url));
const trial=join(here,'target/e6-w5c-local-css-research');
const evidence=join(here,'target/e6-w5d-local-owner-browser-evidence');
const sha=b=>createHash('sha256').update(b).digest('hex');
const fail=s=>{throw Error('E6_W5J_MOCK_'+s);};
async function main(){
 if(process.argv.length!==2||
   process.env.E6_W5J_MOCK_INTEGRATION!=='TEST_DOUBLE_ONLY'||
   process.env.E6_W5C_ALLOW_LOCAL_INFERENCE==='YES')
   fail('EXPLICIT_MOCK_ONLY_GATE_REQUIRED');
 const batch=JSON.parse(await readFile(join(here,'w5b_heldout_briefs.json'),'utf8'));
 const css=await readFile(join(here,'w5a_base.css'),'utf8');
 const result=await evaluateCodeHeldout({
   batch,model:MODEL,baseCss:css,
   transport:fakeCssSourceProvider({briefs:batch.briefs})
 });
 assert.equal(result.report.accepted,3);
 assert.equal(result.report.rejected,0);
 assert.equal(result.report.providerWasActualLoopbackRequest,false);
 // Simulate a loopback provider receipt strictly INSIDE THIS TEST;
 // not an actual network request or model-origin evidence.
 const fake=structuredClone(result.report);
 fake.providerWasActualLoopbackRequest=true;
 for(const c of fake.cases){
   c.designStopReasonVerified=true;c.cssStopReasonVerified=true;
   c.designReportedStopReason='stop';c.cssReportedStopReason='stop';
 }
 const parent=join(here,'target');
 const stat=await lstat(parent);
 assert(stat.isDirectory()&&!stat.isSymbolicLink());
 await mkdir(trial,{recursive:false,mode:0o700});
 for(const [slug,site] of result.sites){
   const root=join(trial,slug);
   await mkdir(root);
   for(const [path,content]of site.files){
     assert(!path.includes('..')&&!path.startsWith('/')&&!path.includes('\\'));
     const file=join(root,path);
     await mkdir(dirname(file),{recursive:true});
     await writeFile(file,content,{flag:'wx',mode:0o600});
   }
 }
 const raw=JSON.stringify(fake,null,2)+'\n';
 await writeFile(join(trial,'source-code-trial-report.json'),raw,
   {flag:'wx',mode:0o600});
 const summary=summarizeObservedTrial(fake,batch.briefs,{
   sourceSha:'f'.repeat(64),reportRawBytes:raw
 });
 assert.equal(summary.accepted,3);
 assert.equal(summary.releaseGo,false);
 assert.equal(summary.modelWeightsCryptographicallyAttested,false);
 const {audit}=await inspectTrial(trial,batch);
 assert.equal(audit.inputReportSha256,summary.reportRawFileSha256);
 assert.equal(audit.acceptedSiteCount,3);
 assert.equal(audit.releaseGo,false);
 const reviewer=spawn(process.execPath,[join(here,'w5d_owner_browser_review.mjs')],{
   cwd:here,env:{...process.env,E6_W5C_ALLOW_LOCAL_INFERENCE:'NO'},
   stdio:['ignore','pipe','pipe']
 });
 const stdout=[],stderr=[];
 for(const [source,parts]of [[reviewer.stdout,stdout],[reviewer.stderr,stderr]]){
   source.on('data',buf=>{
     if(parts.reduce((n,c)=>n+c.length,0)<12_000)parts.push(Buffer.from(buf));
   });
 }
 const [exitCode]=await once(reviewer,'exit');
 if(exitCode!==0){
   console.error('E6_W5J_MOCK_REVIEW_FAILED',Buffer.concat(stderr).toString('utf8').slice(-1500));
   fail('TWO_ROUTE_BROWSER_REVIEW_FAILED');
 }
 const review=JSON.parse(await readFile(join(evidence,'review-report.json'),'utf8'));
 assert.equal(review.fullPageScreenshotCount,18);
 assert.equal(review.allNineSoftwareBrowserReviewsPassed,true);
 assert.equal(review.bothRoutesCapturedForAllNineProfiles,true);
 assert.equal(review.bothRoutesKeyboardFocusVerified,true);
 assert.equal(review.bothRoutesAutomatedAccessibilityPassed,true);
 assert.equal(review.automatedAccessibilityRouteScanCount,18);
 assert.equal(review.textResize200PercentRouteScanCount,18);
 assert.equal(review.bothRoutesTextResizePassed,true);
 assert(Number.isInteger(review.automatedAccessibilityManualReviewItems));
 assert.equal(review.profiles.length,9);
 assert.equal(review.visualQualityHumanApproved,false);
 assert.equal(review.releaseGo,false);
 assert.equal(review.stage45Go,false);
 for(const v of review.profiles){
   assert.equal(v.approachNavigationPassed,true);
   assert.equal(v.bothRoutesKeyboardFocusVerified,true);
   assert.equal(v.bothRoutesAutomatedAccessibilityPassed,true);
   for(const scan of [v.homeAutomatedAccessibility,v.approachAutomatedAccessibility]){
     assert.equal(scan.automatedRuleScanPassed,true);
     assert.equal(scan.violations,0);
     assert.equal(scan.independentWcagCertified,false);
     assert.equal(scan.screenReaderVerified,false);
     assert.equal(scan.generatedAuthorJavaScript,false);
     assert.equal(scan.textResize200Percent.technique,
       'CHROMIUM_SAME_ORIGIN_CSS_RESPONSE_ROOT_RESIZE');
     assert(scan.textResize200Percent.rootFontRatio>=1.95);
     assert(scan.textResize200Percent.bodyFontRatio>=1.95);
     assert(scan.textResize200Percent.horizontalOverflowPx<=2);
     assert.equal(scan.textResize200Percent.mainVisible,true);
     assert.equal(scan.textResize200Percent.headingVisible,true);
     assert.equal(scan.textResize200Percent.navigationLinksVisible,true);
     assert.equal(scan.textResize200Percent.browserUiZoomVerified,false);
     assert.equal(scan.textResize200Percent.ownerDeviceVerified,false);
     assert.equal(scan.textResize200Percent.independentWcagCertified,false);
     assert(Number.isInteger(scan.needsManualReview));
   }
   for(const k of [v.homeKeyboard,v.approachKeyboard]){
     assert.equal(k.firstTabIsSkip,true);
     assert.equal(k.skipVisible,true);
     assert.equal(k.focusOutlineVisible,true);
     assert.equal(k.hashReached,true);
     assert.equal(k.mainFocused,true);
     assert.equal(k.mainTabIndexMinusOne,true);
     assert.equal(k.focusReleased,true);
     assert.equal(k.authorJavascriptEnabled,false);
     assert.equal(k.wcagCertified,false);
   }
   assert.equal(v.horizontalOverflowPx<=2,true);
   assert.equal(v.approachHorizontalOverflowPx<=2,true);
   assert.equal(v.externalRequestCount,0);
   for(const [file,digest]of [
     [v.screenshot,v.screenshotSha256],
     [v.approachScreenshot,v.approachScreenshotSha256]
   ]){
     assert.match(file,/^[a-z0-9-]+(?:-approach)?-\d+x\d+\.png$/);
     const s=await lstat(join(evidence,file));
     assert(s.isFile()&&!s.isSymbolicLink()&&s.size>500);
     assert.equal(sha(await readFile(join(evidence,file))),digest);
   }
 }
 // Verify that the source auditor is not fooled by changing HTML after its
 // self-reported manifest was emitted. This is a disposable MOCK fixture only.
 const victim=join(trial,batch.briefs[0].projectSlug,'approach/index.html');
 await writeFile(victim,(await readFile(victim,'utf8'))+'\n<!-- simulated tamper -->');
 await assert.rejects(inspectTrial(trial,batch),/GENERATED_FILE_HASH_MISMATCH/);
 console.log('E6_W5J_MOCK_ONLY_3_SITES_18_ROUTE_SCREENSHOTS_HASHED '+JSON.stringify({
   syntheticModelResponses:true,realProviderCalled:false,views:9,
   screenshotFiles:18,textResize200PercentRouteScans:18,
   sourceTamperingRejected:true,
   actualWeightsAttested:false,humanVisualApproved:false,releaseGo:false
 }));
}
main().catch(error=>{
 console.error('E6_W5J_MOCK_ONLY_INTEGRATION_BLOCKED',
  /^E6_W5J_MOCK_[A-Z_]+$/.test(error?.message)?
   error.message:String(error?.message||'UNKNOWN').slice(0,220));
 process.exitCode=1;
});
