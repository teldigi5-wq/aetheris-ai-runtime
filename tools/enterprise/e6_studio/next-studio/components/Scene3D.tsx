"use client";
import { useEffect } from "react";
import { Canvas, useFrame, useLoader, useThree } from "@react-three/fiber";
import { GLTFLoader } from "three/addons/loaders/GLTFLoader.js";
import { ACESFilmicToneMapping, PerspectiveCamera } from "three";
import { sampleCamera } from "./cameraTimeline";
type SceneProps={progress:number;onReady:()=>void;assetVariant?:"fixture"|"blender"};
function AuthoredPanel({progress,onReady,assetVariant="fixture"}:SceneProps){
  // Review-gated literal paths only. No user-supplied URLs or arbitrary loader endpoints.
  const blenderPreview=assetVariant==="blender";
  const asset=useLoader(GLTFLoader,blenderPreview?"/e6-blender-preview.glb":"/research-panel.glb");
  const invalidate=useThree(state=>state.invalidate);
  const stageWidth=useThree(state=>state.size.width);
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
  const rotation:[number,number,number]=blenderPreview?[Math.PI/2-0.16,0,0.1]:[0.08,0,-0.08];
  // Preview-only framing uses the measured canvas width, never browser sniffing.
  // This preserves the original fixture scale and all camera-timeline values.
  const modelScale:number|[number,number,number]=blenderPreview?(stageWidth<600?0.58:0.84):[0.82,1.25,0.12];
  return <>
    <ambientLight intensity={blenderPreview?0.56:0.95}/>
    <directionalLight position={[-3,5,6]} intensity={blenderPreview?3.5:3.1} color="#f4e5ce"/>
    <directionalLight position={[3,0,-4]} intensity={blenderPreview?2.2:1.6} color="#a9bed0"/>
    {blenderPreview&&<directionalLight position={[1,4,-2]} intensity={1.4} color="#d9a86d"/>}
    <group rotation={rotation}>
      <primitive object={asset.scene} scale={modelScale}/>
    </group>
  </>;
}
export default function Scene3D({progress,onReady,assetVariant="fixture"}:SceneProps){
  return <Canvas frameloop="demand" dpr={[1,1.5]} shadows={false}
    gl={{powerPreference:"low-power",antialias:true,alpha:false}}
    camera={{position:[0,0.5,4.5],fov:38,near:0.1,far:25}}
    fallback={<span role="status">WebGL unavailable. Static studio silhouette available.</span>}
    onCreated={({gl})=>{
      gl.toneMapping=ACESFilmicToneMapping;
      gl.toneMappingExposure=1.0;
    }}
    aria-label="Decorative WebGL product study">
    <AuthoredPanel progress={progress} onReady={onReady} assetVariant={assetVariant}/>
  </Canvas>;
}
