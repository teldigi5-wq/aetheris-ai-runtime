"use client";
import { useEffect, useRef, useState } from "react";
import { Canvas, useFrame, useLoader, useThree } from "@react-three/fiber";
import { GLTFLoader } from "three/addons/loaders/GLTFLoader.js";
import { ACESFilmicToneMapping } from "three";
type SceneProps={progress:number;onReady:()=>void};
function AuthoredPanel({progress,onReady}:SceneProps){
  const asset=useLoader(GLTFLoader,"/research-panel.glb");
  const invalidate=useThree(state=>state.invalidate);
  useEffect(()=>{onReady();},[onReady]);
  useEffect(()=>{invalidate();},[progress,invalidate]);
  useFrame(({camera})=>{
    const t=Math.max(0,Math.min(1,progress));
    const yaw=-0.6+t*2.15;
    const pitch=0.13+Math.sin(t*Math.PI)*0.13;
    camera.position.set(Math.sin(yaw)*4.5,0.75+pitch,Math.cos(yaw)*4.5);
    camera.lookAt(0,0,0);
  });
  return <>
    <ambientLight intensity={0.95}/>
    <directionalLight position={[-3,5,6]} intensity={3.1} color="#f4e5ce"/>
    <directionalLight position={[3,0,-4]} intensity={1.6} color="#a9bed0"/>
    <group rotation={[0.08,0,-0.08]}><primitive object={asset.scene} scale={[0.82,1.25,0.12]}/></group>
  </>;
}
// Bound the actual R3F render surface BEFORE its first WebGL allocation.
// This is a pixel-allocation policy, NOT a measured GPU frame-time budget.
const PIXEL_BUDGET=1_200_000;
const TARGET_PIXELS=1_150_000; // leave space for browser rounding
type Bounds={width:number;height:number;nativeDpr:number};
function boundedDpr(b:Bounds):number{
  return Math.min(1.5,b.nativeDpr,Math.sqrt(TARGET_PIXELS/(b.width*b.height)));
}
export default function Scene3D({progress,onReady}:SceneProps){
  const surface=useRef<HTMLDivElement>(null);
  const [bounds,setBounds]=useState<Bounds|null>(null);
  useEffect(()=>{
    const node=surface.current;
    if(!node)return;
    const measure=()=>{
      const rect=node.getBoundingClientRect();
      const width=Math.ceil(rect.width),height=Math.ceil(rect.height);
      if(width<1||height<1)return;
      const value={width,height,nativeDpr:Math.max(0.1,window.devicePixelRatio||1)};
      setBounds(prev=>prev&&prev.width===value.width&&
        prev.height===value.height&&prev.nativeDpr===value.nativeDpr?prev:value);
    };
    measure();
    const observer=typeof ResizeObserver==="function"?new ResizeObserver(measure):null;
    observer?.observe(node);
    window.addEventListener("resize",measure,{passive:true});
    return ()=>{observer?.disconnect();window.removeEventListener("resize",measure);};
  },[]);
  const dpr=bounds?boundedDpr(bounds):null;
  return <div ref={surface} data-render-pixel-budget={PIXEL_BUDGET}>
    {dpr!==null&&<Canvas frameloop="demand" dpr={dpr} shadows={false}
      gl={{powerPreference:"low-power",antialias:true,alpha:false}}
      camera={{position:[0,0.5,4.5],fov:38,near:0.1,far:25}}
      fallback={<span role="status">WebGL unavailable. Static studio silhouette available.</span>}
      onCreated={({gl})=>{
        gl.toneMapping=ACESFilmicToneMapping;
        gl.toneMappingExposure=1.0;
      }}
      aria-label="Decorative WebGL product study">
      <AuthoredPanel progress={progress} onReady={onReady}/>
    </Canvas>}
  </div>;
}
