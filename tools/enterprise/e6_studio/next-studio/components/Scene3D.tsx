"use client";
import { useEffect } from "react";
import { Canvas, useFrame, useLoader, useThree } from "@react-three/fiber";
import { GLTFLoader } from "three/addons/loaders/GLTFLoader.js";
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
export default function Scene3D({progress,onReady}:SceneProps){
  return <Canvas frameloop="demand" dpr={[1,1.5]} shadows={false}
    gl={{powerPreference:"low-power",antialias:true,alpha:false}}
    camera={{position:[0,0.5,4.5],fov:38,near:0.1,far:25}}
    fallback={<span role="status">WebGL unavailable. Static studio silhouette available.</span>}
    aria-label="Decorative WebGL product study">
    <AuthoredPanel progress={progress} onReady={onReady}/>
  </Canvas>;
}
