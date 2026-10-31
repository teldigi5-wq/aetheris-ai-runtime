/** E6 local browser rAF-only diagnostic policy.
 * Self-reported localhost evidence is not device attestation or GPU frame time.
 * Pure function: no browser globals, I/O, network or changes to release gates.
 */
export const KIND='E6_LOCAL_BROWSER_RAF_DIAGNOSTIC_V2';
export const EXPECTED_RUNS=3;
const finite=x=>typeof x==='number'&&Number.isFinite(x);
const percentile=(data,f)=>[...data].sort((a,b)=>a-b)[Math.ceil(data.length*f)-1];
export function evaluateLocalBrowserRaf(report){
 const reasons=[],runs=[];
 if(!report||typeof report!=='object'||Array.isArray(report)){
  return {kind:'E6_BROWSER_RAF_RESEARCH_SCREEN',diagnosticValid:false,
   reasons:['E6_RAF_REPORT_REQUIRED'],runs:[],screenBudgetMet:false,
   physicalHardwareCertified:false,task7Completed:false,task8Completed:false,
   stage45Go:false,releaseGo:false};
 }
 if(report.kind!==KIND||report.schemaVersion!==2)reasons.push('E6_RAF_SCHEMA_INVALID');
 if(report.captureMode!=='user-click-visible-loopback-browser'||report.localhost!==true)
  reasons.push('E6_RAF_LOCAL_USER_CAPTURE_UNVERIFIED');
 if(report.webgl2!==true||report.engine!=='r3f-loaded')
  reasons.push('E6_RAF_REAL_SCENE_NOT_READY');
 if(!/^[a-f0-9]{64}$/.test(report.glbSha256||''))
  reasons.push('E6_RAF_ASSET_SHA_MISSING');
 const v=report.viewport||{};
 if(!Number.isInteger(v.width)||v.width<320||v.width>7680||
    !Number.isInteger(v.height)||v.height<400||v.height>4320||
    !finite(v.dpr)||v.dpr<0.5||v.dpr>4)reasons.push('E6_RAF_VIEWPORT_INVALID');
 const when=Date.parse(report.capturedAt);
 const now=Date.now();
 if(!Number.isFinite(when)||when>now+300000||when<now-30*86400000)
  reasons.push('E6_RAF_CAPTURE_DATE_INVALID');
 if(!Array.isArray(report.runs)||report.runs.length!==EXPECTED_RUNS){
  reasons.push('E6_RAF_REPETITIONS_INCOMPLETE');
 }else{
  for(const [index,run] of report.runs.entries()){
   const a=run?.frameIntervalsMs;
   if(!Array.isArray(a)||a.length<300||a.length>450||
       !a.every(x=>finite(x)&&x>0&&x<1000)){
    reasons.push('E6_RAF_FRAME_SAMPLES_INVALID:'+index);continue;
   }
   // A valid progress envelope alone is not enough. Demand a witnessed
   // start -> midpoint -> return sequence AND actual scrollY travel over
   // the GSAP-controlled pinned ScrollTrigger range.
   const progress=[run.progressMinimum,run.progressMaximum,run.progressStart,
    run.progressMidpoint,run.progressEnd];
   const positions=[run.observedScrollMinPx,run.observedScrollMaxPx,run.scrollRangePx];
   if(!progress.every(finite)||progress.some(x=>x<0||x>1)||
       run.progressMinimum>0.15||run.progressMaximum<0.85||
       run.progressStart>0.15||run.progressMidpoint<0.85||
       run.progressEnd>0.15||run.reverseObserved!==true||
       !positions.every(finite)||run.scrollRangePx<100||
       run.observedScrollMaxPx-run.observedScrollMinPx<
          run.scrollRangePx*0.80){
     reasons.push('E6_RAF_REVERSE_SCROLL_UNVERIFIED:'+index);
   }
   if(run.longTaskObserverSupported!==true||
      !Number.isInteger(run.longTaskCount)||run.longTaskCount<0||
      !finite(run.maxLongTaskMs)||run.maxLongTaskMs<0){
    reasons.push('E6_RAF_LONGTASK_NOT_RECORDED:'+index);
   }
   const p50=percentile(a,.50),p95=percentile(a,.95),p99=percentile(a,.99);
   const over33_3Ratio=a.filter(x=>x>33.3).length/a.length;
   runs.push({run:index+1,frames:a.length,p50Ms:p50,p95Ms:p95,p99Ms:p99,
    over33_3Ratio,maxLongTaskMs:run.maxLongTaskMs});
  }
 }
 if(report.browserErrorCount!==0||report.contextLossCount!==0)
  reasons.push('E6_RAF_BROWSER_OR_WEBGL_ERRORS');
 const diagnosticValid=reasons.length===0&&runs.length===EXPECTED_RUNS;
 const screenBudgetMet=diagnosticValid&&runs.every(x=>
  x.p95Ms<=33.3&&x.p99Ms<=50&&x.over33_3Ratio<=.05&&x.maxLongTaskMs<=200);
 return {
  kind:'E6_BROWSER_RAF_RESEARCH_SCREEN',diagnosticValid,reasons,runs,
  screenBudgetMet,
  scope:'BROWSER_REQUEST_ANIMATION_FRAME_INTERVALS_NOT_GPU_FRAME_TIME',
  hardwareIdentityVerified:false,
  sourceCheckoutIndependentlyVerified:false,
  physicalHardwareCertified:false,
  gpuTimeMeasured:false,gpuVramMeasured:false,thermalsMeasured:false,
  physicalMobileTested:false,
  task7Completed:false,task8Completed:false,stage45Go:false,releaseGo:false
 };
}
