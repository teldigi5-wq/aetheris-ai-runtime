/**
 * E6 W4-A: real, source-verified Signal Fold glTF rendered in an isolated static
 * website with native scrolling, a single GSAP ScrollTrigger camera playhead,
 * demand-only GPU frames and accessible progressive fallback.
 *
 * Not model-authored design, GPU-certified, a production asset or release code.
 */
import {
  Scene, Color, PerspectiveCamera, WebGLRenderer, AmbientLight, DirectionalLight,
  Group, Box3, Vector3, ACESFilmicToneMapping, SRGBColorSpace
} from 'three';
import {GLTFLoader} from 'three/addons/loaders/GLTFLoader.js';
import {gsap} from 'gsap';
import {ScrollTrigger} from 'gsap/ScrollTrigger';

const root=document.querySelector('[data-w4a-scene]');
if(root){
  const activate=root.querySelector('[data-w4a-activate]');
  const fallback=root.querySelector('[data-w4a-fallback]');
  const viewport=root.querySelector('[data-w4a-viewport]');
  const state=root.querySelector('[data-w4a-state]');
  const pause=root.querySelector('[data-w4a-pause]');
  const reduce=window.matchMedia('(prefers-reduced-motion: reduce)');
  const glb='/assets/arcloom-signal-fold.glb';
  let renderer,model,observer,trigger,frame=0,disposed=false;
  let progress=0,playing=true;
  const setState=(name,message)=>{root.dataset.engine=name;state.textContent=message;};
  const draw=()=>{
    if(!renderer||!model||!viewport.isConnected)return;
    const width=Math.max(1,viewport.clientWidth),height=Math.max(1,viewport.clientHeight);
    renderer.setPixelRatio(Math.min(devicePixelRatio||1,1.5));
    renderer.setSize(width,height,false);
    const t=progress;
    const angle=-.55+t*1.65;
    camera.position.set(Math.sin(angle)*3.7,1.9-t*.7,Math.cos(angle)*3.7);
    camera.lookAt(0,.07,0);
    model.rotation.y=.15+t*.45;
    camera.updateProjectionMatrix();
    renderer.render(scene,camera);
    root.dataset.progress=t.toFixed(3);
    root.dataset.cameraPhase=t<.333?'arrival':t<.667?'material':'detail';
  };
  const scene=new Scene();
  scene.background=new Color('#192126');
  const camera=new PerspectiveCamera(38,1,.1,100);
  scene.add(new AmbientLight('#eef0ed',2));
  const key=new DirectionalLight('#fff6e8',5);
  key.position.set(3,5,5);scene.add(key);
  const fill=new DirectionalLight('#a8d1ec',3);
  fill.position.set(-5,1,-3);scene.add(fill);
  const rim=new DirectionalLight('#f5a68e',2);
  rim.position.set(2,2,-5);scene.add(rim);

  function cleanup(){
    if(trigger){trigger.kill();trigger=null;}
    if(observer){observer.disconnect();observer=null;}
    if(frame){cancelAnimationFrame(frame);frame=0;}
    if(model){model.traverse(child=>{
      if(child.isMesh){
        child.geometry?.dispose();
        const materials=Array.isArray(child.material)?child.material:[child.material];
        for(const m of materials)m?.dispose();
      }
    });scene.remove(model);model=null;}
    if(renderer){
      renderer.domElement.removeEventListener('webglcontextlost',onLost);
      renderer.dispose();renderer.domElement.remove();renderer=null;
    }
  }
  function fail(reason){
    cleanup();
    fallback.hidden=false;viewport.hidden=true;activate.hidden=false;
    setState('static-fallback',reason+'. Static concept content remains available.');
  }
  function onLost(event){
    event.preventDefault();
    fail('WebGL context unavailable');
  }
  function shutdown(){
    cleanup();disposed=true;
  }
  async function start(){
    if(disposed||reduce.matches)return;
    activate.disabled=true;activate.hidden=true;
    setState('loading','Preparing local original concept geometry.');
    try{
      const canvas=document.createElement('canvas');
      const context=canvas.getContext('webgl2',{alpha:false,antialias:true,powerPreference:'low-power'})
        ||canvas.getContext('webgl',{alpha:false,antialias:true,powerPreference:'low-power'});
      if(!context)throw Error('WebGL not supported');
      renderer=new WebGLRenderer({canvas,context,antialias:true,alpha:false,powerPreference:'low-power'});
      renderer.outputColorSpace=SRGBColorSpace;
      renderer.toneMapping=ACESFilmicToneMapping;
      renderer.toneMappingExposure=1.05;
      renderer.domElement.setAttribute('aria-hidden','true');
      renderer.domElement.addEventListener('webglcontextlost',onLost,{passive:false});
      viewport.append(renderer.domElement);
      const loader=new GLTFLoader();
      const asset=await loader.loadAsync(glb);
      if(disposed||reduce.matches){asset.scene.traverse(o=>{
        if(o.isMesh){o.geometry?.dispose();o.material?.dispose();}
      });cleanup();return;}
      model=new Group();
      model.add(asset.scene);
      const bounds=new Box3().setFromObject(model);
      const size=bounds.getSize(new Vector3()),center=bounds.getCenter(new Vector3());
      if(!size.x||!size.y||!size.z||Math.max(size.x,size.y,size.z)>100)throw Error('Invalid fixed model bounds');
      asset.scene.position.sub(center);
      const factor=2.25/Math.max(size.x,size.y,size.z);
      model.scale.setScalar(factor);
      scene.add(model);
      viewport.hidden=false;
      fallback.hidden=true;
      setState('webgl-loaded','Original Signal Fold model loaded. Camera follows native page scroll.');
      observer=new ResizeObserver(()=>{if(renderer)draw();});
      observer.observe(viewport);
      gsap.registerPlugin(ScrollTrigger);
      trigger=ScrollTrigger.create({
        trigger:root,start:'top bottom',end:'bottom top',
        invalidateOnRefresh:true,
        onUpdate:self=>{
          if(!playing)return;
          progress=Math.min(1,Math.max(0,self.progress));
          if(frame)cancelAnimationFrame(frame);
          frame=requestAnimationFrame(()=>{frame=0;draw();});
        }
      });
      draw();
      pause.disabled=false;
    }catch(error){fail('3D preview unavailable');}
    finally{activate.disabled=false;}
  }
  function onPreference(){
    if(reduce.matches){
      cleanup();fallback.hidden=false;viewport.hidden=true;
      activate.hidden=true;pause.hidden=true;
      setState('reduced-static','Reduced-motion static presentation. No GPU scene required.');
    }else if(root.dataset.engine==='reduced-static'){
      activate.hidden=false;pause.hidden=false;
      setState('static-ready','Static presentation. Optional 3D can be activated.');
    }
  }
  activate.addEventListener('click',start);
  pause.addEventListener('click',()=>{
    playing=!playing;pause.setAttribute('aria-pressed',String(!playing));
    pause.textContent=playing?'Pause camera motion':'Resume camera motion';
    if(playing&&trigger){
      progress=Math.min(1,Math.max(0,trigger.progress));draw();
    }
  });
  reduce.addEventListener('change',onPreference);
  window.addEventListener('pagehide',shutdown,{once:true});
  onPreference();
}
