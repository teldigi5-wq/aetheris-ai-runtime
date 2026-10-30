"use client";
import { useEffect } from "react";
import { Canvas, useFrame, useLoader, useThree } from "@react-three/fiber";
import { GLTFLoader } from "three/addons/loaders/GLTFLoader.js";
import { ACESFilmicToneMapping, PerspectiveCamera } from "three";
import { sampleCamera } from "./cameraTimeline";
type SceneProps={progress:number;onReady:()=>void};
function AuthoredPanel({progress,onReady}:SceneProps){
  const asset=useLoader(GLTFLoader,"/research-panel.glb");
  const invalidate=useThree(state=>state.invalidate);
  useEffect(()=>{onReady();},[onReady]);
  useEffect(()=>{invalidate();},[progress,invalidate]);
  useFrame(({camera})=>{
    // The ScrollTrigger progress prop is the only playhead; useFrame applies it
    // on-demand without creating a competing RAF or autonomous camera loop.
    const shot=sampleCamera(progress);
    camera.position.set(...shot.position);
    camera.lookAt(0,0,0);
    if(camera instanceof PerspectiveCamera && Math.abs(camera.fov-shot.fov)>0.001){
      camera.fov=shot.fov;
      camera.updateProjectionMatrix();
    }
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
    onCreated={({gl})=>{
      gl.toneMapping=ACESFilmicToneMapping;
      gl.toneMappingExposure=1.0;
    }}
    aria-label="Decorative WebGL product study">
    <AuthoredPanel progress={progress} onReady={onReady}/>
  </Canvas>;
}
