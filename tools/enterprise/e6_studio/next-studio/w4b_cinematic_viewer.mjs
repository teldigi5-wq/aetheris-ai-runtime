/**
 * E6 W4-A: real, source-verified Signal Fold glTF rendered in an isolated static
 * website with native scrolling, a single GSAP ScrollTrigger camera playhead,
 * demand-only GPU frames and accessible progressive fallback.
 *
 * Not model-authored design, GPU-certified, a production asset or release code.
 */
import {
  Scene, Color, PerspectiveCamera, WebGLRenderer, AmbientLight, DirectionalLight,
  Group, Box3, Vector3, ACESFilmicToneMapping, SRGBColorSpace,
  HemisphereLight, Mesh, CylinderGeometry, MeshStandardMaterial
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
  const approvedHash=__W4B_VERIFIED_MODEL_SHA__;
  const shots=[
    {p:0,position:[3.4,2.4,4.6],target:[0,.12,0],fov:35,angle:-.15},
    {p:.5,position:[.35,2.0,3.7],target:[0,.04,0],fov:32,angle:.16},
    {p:1,position:[-2.2,1.55,3.6],target:[-.15,.16,0],fov:30,angle:.32}
  ];
  const ease=t=>t*t*(3-2*t);
  const blend=(a,b,t)=>a+(b-a)*t;
  const poseAt=t=>{
    const left=t<.5?shots[0]:shots[1],right=t<.5?shots[1]:shots[2];
    const u=ease(Math.max(0,Math.min(1,(t-left.p)/(right.p-left.p))));
    return {position:left.position.map((v,i)=>blend(v,right.position[i],u)),
      target:left.target.map((v,i)=>blend(v,right.target[i],u)),
      fov:blend(left.fov,right.fov,u),angle:blend(left.angle,right.angle,u)};
  };
  let renderer,model,observer,trigger,frame=0,disposed=false;
  let progress=0,playing=true;
  const setState=(name,message)=>{root.dataset.engine=name;state.textContent=message;};
  const draw=()=>{
    if(!renderer||!model||!viewport.isConnected)return;
    const width=Math.max(1,viewport.clientWidth),height=Math.max(1,viewport.clientHeight);
    renderer.setPixelRatio(Math.min(devicePixelRatio||1,1.5));
    renderer.setSize(width,height,false);
    camera.aspect=width/height;
    const t=progress;
    const pose=poseAt(t);
    camera.position.set(...pose.position);
    camera.lookAt(...pose.target);
    camera.fov=pose.fov;
    model.rotation.y=pose.angle;
    camera.updateProjectionMatrix();
    renderer.render(scene,camera);
    // Research-only visual evidence: read the framebuffer synchronously after
    // its draw. Never activate for ordinary visitors, remote hosts or deployment.
    // Element screenshots are unsuitable because Playwright may scroll to
    // the canvas and thus change GSAP progress before capturing the frame.
    if(location.hostname==='127.0.0.1' &&
      new URLSearchParams(location.search).get('w4b-visual-proof')==='1'){
      window.__w4bVisualProof=renderer.domElement.toDataURL('image/png');
    }
    root.dataset.progress=t.toFixed(3);
    root.dataset.cameraPhase=t<.333?'arrival':t<.667?'material':'detail';
    root.dataset.w4bCameraX=camera.position.x.toFixed(3);
    root.dataset.w4bCameraY=camera.position.y.toFixed(3);
    root.dataset.w4bCameraZ=camera.position.z.toFixed(3);
    root.dataset.w4bCameraFov=camera.fov.toFixed(2);
    root.dataset.w4bModelHash=approvedHash;
    root.dataset.w4bRenderedFrames=String(1+Number(root.dataset.w4bRenderedFrames||0));
    const label=root.querySelector('[data-w4b-shot]');
    if(label)label.textContent=['01 / Silhouette','02 / Material','03 / Edge'][t<.333?0:t<.667?1:2];
  };
  const scene=new Scene();
  scene.background=new Color('#292825');
  scene.add(new HemisphereLight('#fff5e8','#655e58',2.1));
  const camera=new PerspectiveCamera(38,1,.1,100);
  scene.add(new AmbientLight('#eef0ed',2));
  const key=new DirectionalLight('#fff6e8',5);
  key.position.set(3,5,5);scene.add(key);
  const fill=new DirectionalLight('#a8d1ec',3);
  fill.position.set(-5,1,-3);scene.add(fill);
  const rim=new DirectionalLight('#f5a68e',2);
  rim.position.set(2,2,-5);scene.add(rim);
  const plinthMaterial=new MeshStandardMaterial({color:'#b8aca0',roughness:.82,metalness:0});
  const plinth=new Mesh(new CylinderGeometry(1.4,1.48,.14,72),plinthMaterial);
  plinth.position.set(0,-.95,0);scene.add(plinth);

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
      const response=await fetch(glb,{credentials:'omit',cache:'no-store',mode:'same-origin'});
      if(!response.ok||response.status!==200)throw Error('Verified model unavailable');
      const bytes=await response.arrayBuffer();
      if(bytes.byteLength>2_000_000||bytes.byteLength<1024||!crypto?.subtle)
        throw Error('Model integrity prerequisites unavailable');
      const hash=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',bytes)))
        .map(b=>b.toString(16).padStart(2,'0')).join('');
      if(hash!==approvedHash)throw Error('Model hash mismatch');
      const loader=new GLTFLoader();
      const asset=await loader.parseAsync(bytes,'/assets/');
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
      setState('webgl-loaded','Original source-checked Signal Fold model loaded. Camera follows native scroll.');
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
    }else if(root.dataset.engine==='reduced-static'||root.dataset.engine==='static-ready'){
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
