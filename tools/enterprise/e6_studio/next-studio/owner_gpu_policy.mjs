/** E6 real-device benchmark evidence policy (NOT release certification).
 * No trusted authority is derived solely from a self-reported browser string.
 * Physical mobile, VRAM and thermals require separately corroborated evidence.
 */
export const KIND="E6_OWNER_LOCAL_HARDWARE_RESEARCH_V1";
export const REQUIRED_RUNS=3;
const SOFTWARE=/swiftshader|llvmpipe|lavapipe|softpipe|software raster|warp|microsoft basic render|mesa offscreen|virtualbox|vmware|virgl|angle.*null/i;
export const percentile=(values,fraction)=>{
  if(!Array.isArray(values)||values.length===0)throw Error("E6_EMPTY_INTERVALS");
  const a=[...values].sort((x,y)=>x-y);
  return a[Math.max(0,Math.ceil(a.length*fraction)-1)];
};
const finitePositive=(x)=>typeof x==="number"&&Number.isFinite(x)&&x>0;
const hasGPU=(v)=>typeof v==="string"&&v.length>=5&&v.length<=512&&!SOFTWARE.test(v);
export function evaluateHardwareEvidence(report){
  const reasons=[],runs=[];
  if(!report||typeof report!=="object"||Array.isArray(report))return {acceptedForResearch:false,reasons:["missing report"],releaseGo:false,hardwareCertified:false};
  if(report.kind!==KIND||report.schemaVersion!==1)reasons.push("E6_INVALID_REPORT_SCHEMA");
  if(report.captureMode!=="owner-local-headed-chrome")reasons.push("E6_NOT_EXPLICIT_HEADFUL_LOCAL_CHROME");
  if(report.emulated===true||report.ci===true||report.headless===true)reasons.push("E6_EMULATED_OR_CI_RESULT_NOT_HARDWARE");
  if(report.webgl2!==true)reasons.push("E6_NO_WEBGL2");
  const renderer=report.renderer||{};
  if(!hasGPU(renderer.unmaskedRenderer)||!hasGPU(renderer.unmaskedVendor)){
    reasons.push("E6_GPU_HARDWARE_RENDERER_UNVERIFIED");
  }
  if(typeof report.browserVersion!=="string"||!/^Chrome\/\d+\.\d+\.\d+\.\d+$/.test(report.browserVersion))reasons.push("E6_CHROME_VERSION_MISSING");
  const viewport=report.viewport||{};
  if(!Number.isInteger(viewport.width)||viewport.width<320||viewport.width>7680||
     !Number.isInteger(viewport.height)||viewport.height<500||viewport.height>4320||
     !finitePositive(viewport.dpr)||viewport.dpr>4)reasons.push("E6_VIEWPORT_INVALID");
  if(!Array.isArray(report.runs)||report.runs.length!==REQUIRED_RUNS)reasons.push("E6_REPETITIONS_INCOMPLETE");
  else{
    report.runs.forEach((run,index)=>{
      const frames=run?.frameIntervalsMs;
      if(!Array.isArray(frames)||frames.length<180||frames.length>1200||
         !frames.every(x=>finitePositive(x)&&x<1000)){
        reasons.push("E6_FRAME_SAMPLE_INVALID:"+index);return;
      }
      const sorted=[...frames].sort((a,b)=>a-b);
      const p50=percentile(sorted,0.5),p95=percentile(sorted,0.95),p99=percentile(sorted,0.99);
      const missed=frames.filter(x=>x>33.3).length/frames.length;
      const progress=run?.progress||{};
      if(!Number.isFinite(progress.minimum)||!Number.isFinite(progress.maximum)||
        progress.minimum>0.15||progress.maximum<0.85||
        run.reverseObserved!==true)reasons.push("E6_SCROLL_WORKLOAD_UNVERIFIED:"+index);
      if(p95>33.3||p99>50||missed>0.05)reasons.push("E6_FRAME_PACING_BUDGET_NOT_MET:"+index);
      if(!Number.isInteger(run.longTaskCount)||run.longTaskCount<0||
         !Number.isFinite(run.maxLongTaskMs)||run.maxLongTaskMs<0){
         reasons.push("E6_LONGTASK_EVIDENCE_INVALID:"+index);
      } else if(run.maxLongTaskMs>200)reasons.push("E6_LONGTASK_OVER_BUDGET:"+index);
      runs.push({index,frames:frames.length,p50Ms:p50,p95Ms:p95,p99Ms:p99,over33_3Ratio:missed,
        maxLongTaskMs:run.maxLongTaskMs??null});
    });
  }
  if(!report.glbSha256||!(/^[0-9a-f]{64}$/.test(report.glbSha256)))reasons.push("E6_GLB_DIGEST_MISSING");
  if(!report.exactGitCommit||!(/^[0-9a-f]{40}$/.test(report.exactGitCommit)))reasons.push("E6_SOURCE_COMMIT_MISSING");
  if(!Number.isFinite(Date.parse(report.capturedAt))||
      Date.parse(report.capturedAt)>Date.now()+300000)reasons.push("E6_CAPTURE_TIME_INVALID");
  if(report.externalRequests!==0||report.browserErrors!==0||report.webglLosses!==0)reasons.push("E6_BROWSER_ERROR_OR_OUTSIDE_RESOURCE");
  // These are not obtainable as trustworthy cross-platform browser APIs.
  // A passing trace cannot certify thermals, true GPU frame time, GPU VRAM,
  // screen-reader behavior, mobile hardware or a release.
  const acceptedForResearch=reasons.length===0;
  return {
    kind:"E6_LOCAL_BROWSER_FRAME_PACING_ASSESSMENT",
    acceptedForResearch,reasons,runs,
    measurementScope:"WINDOW_RAF_SCROLL_FRAME_INTERVALS_NOT_GPU_RENDER_DURATION",
    unsupportedEvidence:["GPU_FRAME_TIME","GPU_VRAM","GPU_THERMALS","SUSTAINED_POWER","REAL_MOBILE_HARDWARE","INDEPENDENT_VISUAL_ACCEPTANCE"],
    hardwareCertified:false,deviceMatrixComplete:false,ownerAccepted:false,
    stage45Go:false,releaseGo:false
  };
}
