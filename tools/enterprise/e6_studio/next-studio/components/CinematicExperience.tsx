"use client";
import { Component, useCallback, useEffect, useRef, useState } from "react";
import type { ReactNode } from "react";
import dynamic from "next/dynamic";
import { motion } from "motion/react";
import { gsap } from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import { sampleCamera } from "./cameraTimeline";
const Scene3D=dynamic(()=>import("./Scene3D"),{ssr:false});
const descriptions=[
  ["01","Arrival","Proportion","The silhouette establishes scale and a measured point of view."],
  ["02","Material","Finish","Directional lighting reveals the surface without a bloom-heavy spectacle."],
  ["03","Precision","Detail","A closer angle gives the edge treatment a deliberate place."]
] as const;
function StaticGeometry(){return <div className="static-geometry" aria-hidden="true"><div className="static-geometry__panel"><span/><span/></div></div>;}
class SceneErrorBoundary extends Component<{children:ReactNode;onUnavailable:()=>void},{failed:boolean}>{
  state={failed:false};
  static getDerivedStateFromError(){return {failed:true};}
  componentDidCatch(){this.props.onUnavailable();}
  render(){return this.state.failed?null:this.props.children;}
}
export default function CinematicExperience({assetVariant="fixture"}:{assetVariant?:"fixture"|"blender"}){
  const blenderPreview=assetVariant==="blender";
  const story=useRef<HTMLElement>(null);
  const [reduced,setReduced]=useState(true);
  const [progress,setProgress]=useState(0);
  const [ready,setReady]=useState(false);
  const [smooth,setSmooth]=useState(false);
  const [engine,setEngine]=useState("static");
  const [unavailable,setUnavailable]=useState(false);
  const [sceneRevision,setSceneRevision]=useState(0);
  const sceneReady=useCallback(()=>{setReady(true);setEngine("r3f-loaded");},[]);
  const sceneUnavailable=useCallback(()=>{
    setReady(false);setUnavailable(true);setSmooth(false);
    setEngine("webgl-unavailable");
  },[]);
  const retryScene=()=>{
    setReady(false);setEngine("static");
    setSceneRevision(n=>n+1);setUnavailable(false);
  };
  useEffect(()=>{
    const query=window.matchMedia("(prefers-reduced-motion: reduce)");
    const update=()=>{
      setReduced(query.matches);
      if(query.matches){setReady(false);setEngine("reduced-static");setSmooth(false);}
    };
    update();query.addEventListener("change",update);
    return ()=>query.removeEventListener("change",update);
  },[]);
  useEffect(()=>{
    if(reduced||!story.current)return;
    gsap.registerPlugin(ScrollTrigger);
    const ctx=gsap.context(()=>{
      ScrollTrigger.create({trigger:story.current,start:"top top",end:"+=220%",
        pin:true,scrub:true,anticipatePin:1,invalidateOnRefresh:true,
        onUpdate:self=>setProgress(Math.max(0,Math.min(1,self.progress)))});
    },story);
    return ()=>ctx.revert();
  },[reduced]);
  useEffect(()=>{
    if(!smooth||reduced)return;
    let stopped=false;
    let lenis:{destroy():void;on(event:string,callback:()=>void):void}|undefined;
    import("lenis").then(({default:Lenis})=>{
      if(stopped)return;
      lenis=new Lenis({autoRaf:true,smoothWheel:true,syncTouch:false});
      lenis.on("scroll",()=>ScrollTrigger.update());ScrollTrigger.refresh();
    });
    return ()=>{stopped=true;lenis?.destroy();ScrollTrigger.refresh();};
  },[smooth,reduced]);
  useEffect(()=>{
    if(reduced)return;
    const marker=document.querySelector(".waapi-marker");
    if(!marker||typeof marker.animate!=="function")return;
    const animation=marker.animate(
      [{opacity:0.3,transform:"translateY(6px)"},{opacity:1,transform:"translateY(0)"}],
      {duration:540,easing:"ease-out",fill:"both"});
    return ()=>animation.cancel();
  },[reduced]);
  const camera=sampleCamera(progress);
  const shot=Math.min(2,Math.floor(Math.min(progress,0.999999)*3));
  return <>
    <a href="#studio-story" className="skip-link">Skip to the studio story</a>
    <header className="nav-shell">
      <a href="#top" className="mark">AETHERIS <span>/ CINEMATIC LAB</span></a>
      <div className="nav-right"><span className="waapi-marker">{blenderPreview?"BLENDER PROOF / UNRELEASED":"E6 RESEARCH"}</span>
        <a href="#approach">Method</a><a href="#limits">Boundaries</a></div>
    </header>
    <section className="intro" id="top" aria-labelledby="studio-title">
      <p className="eyebrow">{blenderPreview?"AETHERIS / PHYSICAL DESIGN RESEARCH / 003":"CINEMATIC PRODUCT ENGINEERING / EXPERIMENT 002"}</p>
      <motion.h1 id="studio-title" initial={false}
        animate={reduced?{}:{opacity:1,y:0}} transition={{duration:0.6}}>
        {blenderPreview?<>Designed to <em>make trust tangible.</em></>:<>Engineering <em>the reveal.</em></>}
      </motion.h1>
      <p className="lead">{blenderPreview?"A conceptual command module, authored in Blender from original primitives. Brushed alloy, graphite and restrained amber details reveal a hypothetical physical expression of governed intelligence—not a manufactured device or shipping product.":"A restrained three-act product story: authored geometry, physically based material, and camera direction shaped by your scroll. A local research study, not a production AI-generated customer experience."}</p>
      <a href="#studio-story" className="jump">Explore the sequence <span aria-hidden="true">↘</span></a>
    </section>
    <section ref={story} id="studio-story" className={blenderPreview?"studio-story studio-story--blender":"studio-story"}
      aria-label="Scroll-directed architectural product composition"
      tabIndex={-1}
      data-recovery={unavailable?"needed":"none"}
      data-asset-variant={assetVariant}
      data-engine={engine} data-shot={descriptions[shot][1].toLowerCase()}
      data-progress={progress.toFixed(3)} data-camera-shot={camera.shot}
      data-camera-x={camera.position[0].toFixed(4)}
      data-camera-y={camera.position[1].toFixed(4)}
      data-camera-z={camera.position[2].toFixed(4)}
      data-camera-fov={camera.fov.toFixed(3)}>
      <div className="visual-panel" aria-hidden="true">
        {!reduced&&!unavailable&&
          <SceneErrorBoundary key={sceneRevision} onUnavailable={sceneUnavailable}>
            <Scene3D progress={progress} onReady={sceneReady} onUnavailable={sceneUnavailable} assetVariant={assetVariant}/>
          </SceneErrorBoundary>}

        {(!ready||reduced||unavailable)&&<StaticGeometry/>}
      </div>
      <div className="studio-text">
        <span className="eyebrow">{descriptions[shot][0]} / 03 — ORIGINAL RESEARCH STUDY</span>
        <h2>{descriptions[shot][1]}.</h2><p>{descriptions[shot][3]}</p>
        <p className="tech-state" role="status">{reduced?"Static presentation · reduced motion":unavailable?"WebGL unavailable · static presentation":ready?"WebGL scene · demand rendering":"Static presentation · loading"}</p>
        {unavailable&&!reduced&&<button className="scene-retry" type="button" onClick={retryScene}>Retry 3D view</button>}
      </div>
      <div className="progress-indicator" aria-hidden="true"><span style={{width:String(Math.round(progress*100))+"%"}}/></div>
      <p className="story-caption">SCROLL TO DIRECT THE CAMERA — {descriptions[shot][0]} / 03</p>
    </section>
    <section className="chapters" id="approach" aria-label="Storyboard design method">
      {descriptions.map(([number,name,heading,description])=>
        <motion.article key={number} initial={false}
          whileInView={reduced?undefined:{opacity:1,y:0}}
          viewport={{once:true,amount:0.3}} transition={{duration:0.45}}>
          <span className="eyebrow">{number} / {name}</span>
          <h3>{heading}.</h3><p>{description}</p>
        </motion.article>
      )}
    </section>
    <section className="controls" aria-label="Optional smooth scroll setting">
      <div><h2>Respect the user's device.</h2><p>Native scrolling is the default. Optional Lenis smoothing is disabled for reduced-motion preferences and never enabled on behalf of visitors.</p></div>
      <button type="button" aria-pressed={smooth} disabled={reduced}
        onClick={()=>setSmooth(value=>!value)}>Smooth scroll: {smooth&&!reduced?"On":"Off"}</button>
    </section>
    <section className="limitations" id="limits" aria-label="Research status and release limitations">
      <p className="eyebrow">BUILD EVIDENCE / NOT A CUSTOMER RELEASE</p>
      <h2>Craft backed by verifiable boundaries.</h2>
      <p>{blenderPreview?"This opt-in, unmerged E6 research preview uses an original Blender-authored conceptual module generated in CI and checked against the prior geometry/provenance evidence. It is not a manufactured product, final brand asset, hardware benchmark, licensed third-party asset inventory, independent design review, security release or production deployment. The normal research page remains untouched.":"Next.js App Router, React, React Three Fiber, Three.js, GSAP ScrollTrigger, Motion, optional Lenis and WAAPI are integrated as an isolated human-authored research prototype. Three deterministic, reviewable camera keyframes now direct this page while its original GLB remains unchanged. A separate unmerged E6 Blender research pipeline has produced and compared an original Blender source, but that asset has not been adopted by this page. No physical GPU performance, VRAM or thermal benchmark, independent human visual acceptance, external design certification, complete E6 production pipeline or autonomous AI-generated website has been verified."}</p>
    </section>
    <footer>STAGE45 NO_GO · NO PRODUCTION DEPLOYMENT · NO CUSTOMER DATA</footer>
  </>;
}
