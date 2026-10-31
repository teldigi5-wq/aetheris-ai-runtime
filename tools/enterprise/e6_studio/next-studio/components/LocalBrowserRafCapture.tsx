"use client";
/** Explicit local-only three-run browser rAF capture; never a GPU timer. */
import {useEffect,useRef,useState} from "react";
import {KIND,evaluateLocalBrowserRaf} from "../local_browser_raf_policy.mjs";
import type {LocalRafRun,LocalRafReport,LocalRafAssessment} from "../local_browser_raf_policy.mjs";

const loopback=()=>["localhost","127.0.0.1","[::1]"].includes(window.location.hostname);
function nextFrame(signal:AbortSignal):Promise<number>{
 return new Promise((resolve,reject)=>{
  if(signal.aborted){reject(Error("Capture cancelled"));return;}
  let id=0,timer=0;
  const clear=()=>{window.cancelAnimationFrame(id);window.clearTimeout(timer);signal.removeEventListener("abort",cancel);};
  const cancel=()=>{clear();reject(Error("Capture cancelled"));};
  signal.addEventListener("abort",cancel,{once:true});
  id=window.requestAnimationFrame(now=>{clear();resolve(now);});
  timer=window.setTimeout(()=>{clear();reject(Error("Frame stalled or tab was backgrounded"));},2000);
 });
}
async function glbDigest(signal:AbortSignal){
 const response=await fetch("/e6-blender-preview.glb",{signal,cache:"no-store",redirect:"error"});
 if(!response.ok||response.type!=="basic")throw Error("Local GLB unavailable");
 const length=Number(response.headers.get("content-length"));
 if(Number.isFinite(length)&&length>5*1024*1024)throw Error("Local GLB exceeds bound");
 const data=await response.arrayBuffer();
 if(data.byteLength<1024||data.byteLength>5*1024*1024)throw Error("Local GLB size out of bounds");
 const bytes=new Uint8Array(await crypto.subtle.digest("SHA-256",data));
 return Array.from(bytes,x=>x.toString(16).padStart(2,"0")).join("");
}
export default function LocalBrowserRafCapture(){
 const [available,setAvailable]=useState(false);
 const [working,setWorking]=useState(false);
 const [message,setMessage]=useState("No capture performed.");
 const [report,setReport]=useState<LocalRafReport|null>(null);
 const [assessment,setAssessment]=useState<LocalRafAssessment|null>(null);
 const active=useRef<AbortController|null>(null);
 useEffect(()=>{
  setAvailable(loopback());
  return ()=>active.current?.abort();
 },[]);
 const run=async()=>{
  if(active.current||!loopback())return;
  if(window.matchMedia("(prefers-reduced-motion: reduce)").matches){
   setMessage("Motion capture disabled by reduced-motion preference.");return;
  }
  const story=document.getElementById("studio-story");
  const canvas=story?.querySelector("canvas");
  if(!story||!canvas||story.dataset.engine!=="r3f-loaded"){
   setMessage("Load the visible 3D scene first; this is not a static fallback benchmark.");return;
  }
  if(document.visibilityState!=="visible"){
   setMessage("Keep this browser tab visible and in the foreground.");return;
  }
  const controller=new AbortController();
  active.current=controller;
  setWorking(true);setReport(null);setAssessment(null);
  setMessage("Capturing three forward/reverse scroll runs. Keep this Chrome tab visible.");
  const initialScroll=window.scrollY;
  const onHidden=()=>{if(document.visibilityState!=="visible")controller.abort();};
  document.addEventListener("visibilitychange",onHidden);
  let errors=0,contextLosses=0;
  const onError=()=>{errors++;};
  const onLoss=(event:Event)=>{event.preventDefault();contextLosses++;controller.abort();};
  window.addEventListener("error",onError);
  window.addEventListener("unhandledrejection",onError);
  canvas.addEventListener("webglcontextlost",onLoss);
  try{
   const digest=await glbDigest(controller.signal);
   const gl=canvas.getContext("webgl2");
   const info=gl?.getExtension("WEBGL_debug_renderer_info");
   const renderer=gl&&info?{
    unmaskedRenderer:String(gl.getParameter(info.UNMASKED_RENDERER_WEBGL)),
    unmaskedVendor:String(gl.getParameter(info.UNMASKED_VENDOR_WEBGL))
   }:{unmaskedRenderer:null,unmaskedVendor:null};
   const runs:LocalRafRun[]=[];
   // Each run starts at the same ScrollTrigger pin origin; preserve user position.
   const start=window.scrollY+story.getBoundingClientRect().top;
   const span=window.innerHeight*2.2;
   const hasLongTask=typeof PerformanceObserver!=="undefined"&&
      PerformanceObserver.supportedEntryTypes?.includes("longtask")===true;
   for(let repeat=0;repeat<3;repeat++){
    if(controller.signal.aborted)throw Error("Capture cancelled");
    window.scrollTo({top:start,behavior:"instant"});
    for(let n=0;n<36;n++)await nextFrame(controller.signal);
    let longTaskCount=0,maxLongTaskMs=0;
    const record=(entries:PerformanceEntry[])=>{
     for(const entry of entries){longTaskCount++;maxLongTaskMs=Math.max(maxLongTaskMs,entry.duration);}
    };
    const observer=hasLongTask?new PerformanceObserver(entries=>record(entries.getEntries())):null;
    observer?.observe({entryTypes:["longtask"]});
    const intervals:number[]=[],progress:number[]=[];
    let previous:number|null=null;
    try{
     for(let i=0;i<=360;i++){
      const now=await nextFrame(controller.signal);
      if(previous!==null)intervals.push(now-previous);
      previous=now;
      const pos=Number(story.dataset.progress);
      if(Number.isFinite(pos)&&pos>=0&&pos<=1)progress.push(pos);
      const fraction=i<=180?i/180:(360-i)/180;
      window.scrollTo({top:start+span*fraction,behavior:"instant"});
     }
     for(let i=0;i<3;i++)await nextFrame(controller.signal);
    }finally{
     if(observer){record(observer.takeRecords());observer.disconnect();}
    }
    const middle=progress[Math.floor(progress.length/2)];
    runs.push({
     frameIntervalsMs:intervals,
     progressMinimum:progress.length?Math.min(...progress):Number.NaN,
     progressMaximum:progress.length?Math.max(...progress):Number.NaN,
     reverseObserved:progress.length>300&&Number.isFinite(middle)&&
       progress[progress.length-1]<middle-0.35,
     longTaskObserverSupported:hasLongTask,
     longTaskCount,maxLongTaskMs
    });
    setMessage("Completed local pass "+(repeat+1)+" of 3. Continue keeping Chrome visible.");
   }
   const result:LocalRafReport={
    kind:KIND,schemaVersion:1,captureMode:"user-click-visible-loopback-browser",
    localhost:true,capturedAt:new Date().toISOString(),
    engine:story.dataset.engine||"unknown",webgl2:Boolean(gl),renderer,glbSha256:digest,
    viewport:{width:window.innerWidth,height:window.innerHeight,dpr:window.devicePixelRatio},
    browserErrorCount:errors,contextLossCount:contextLosses,runs,
    metadata:{
     browser:navigator.userAgent.match(/Chrome\/[\d.]+/)?.[0]||"unverified",
     sourceCommitAttested:false,actualGpuTimeMeasured:false,externalUpload:false
    }
   };
   const outcome=evaluateLocalBrowserRaf(result);
   setReport(result);setAssessment(outcome);
   setMessage(outcome.diagnosticValid
     ?"Three-run rAF trace ready for MANUAL review. Not GPU frame-time evidence."
     :"Capture needs review: "+outcome.reasons.join(", "));
  }catch(error){
   setMessage(error instanceof Error?error.message:"Local capture failed");
  }finally{
   document.removeEventListener("visibilitychange",onHidden);
   window.removeEventListener("error",onError);
   window.removeEventListener("unhandledrejection",onError);
   canvas.removeEventListener("webglcontextlost",onLoss);
   window.scrollTo({top:initialScroll,behavior:"instant"});
   active.current=null;setWorking(false);
  }
 };
 const save=()=>{
  if(!report||!assessment||!loopback())return;
  const data=JSON.stringify({report,assessment},null,2)+"\n";
  const url=URL.createObjectURL(new Blob([data],{type:"application/json"}));
  const link=document.createElement("a");
  link.href=url;
  link.download="e6-local-raf-"+report.capturedAt.replace(/[:.]/g,"-")+".json";
  document.body.appendChild(link);link.click();link.remove();
  window.setTimeout(()=>URL.revokeObjectURL(url),1000);
 };
 if(!available)return null;
 return <section className="e6-local-raf" aria-label="Optional local native-browser frame pacing diagnostic">
  <p className="eyebrow">OPTIONAL / OWNER-ONLY LOCAL MEASUREMENT</p>
  <h2>Measure the scroll, not a claim.</h2>
  <p>Explicitly opt in to three local forward/reverse scroll runs. This uses browser animation-frame
     timestamps, <strong>not GPU render time, VRAM, temperature or power</strong>.
     No benchmark starts automatically and nothing is uploaded.</p>
  <div className="e6-raf-actions">
   <button type="button" disabled={working} onClick={run}>Start three-pass local trace</button>
   <button type="button" disabled={!working} onClick={()=>active.current?.abort()}>Cancel capture</button>
   <button type="button" disabled={!report||working} onClick={save}>Save JSON on this device</button>
  </div>
  <p role="status" aria-live="polite">{message}</p>
  {assessment&&<div className="e6-raf-result">
   <p>Diagnostic input: {assessment.diagnosticValid?"Structured sample complete":"Incomplete / rejected"}.
      Browser rAF budget screen: {assessment.diagnosticValid?(assessment.screenBudgetMet?"Within preliminary screening limits":"Over screening limits"):"Not assessed"}.</p>
   {assessment.runs.map(x=><p key={x.run}>Pass {x.run}: p50 {x.p50Ms.toFixed(1)}ms ·
    p95 {x.p95Ms.toFixed(1)}ms · p99 {x.p99Ms.toFixed(1)}ms ·
    over 33.3ms {(100*x.over33_3Ratio).toFixed(1)}%</p>)}
  </div>}
  <p className="e6-raf-boundary">Results are self-reported and not independently authenticated.
     Record Chrome version and physical device configuration separately; review JSON before sharing.
     Task 7 incomplete · Task 8 incomplete · Stage45 NO_GO.</p>
 </section>;
}
