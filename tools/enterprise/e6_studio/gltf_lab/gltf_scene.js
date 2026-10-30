/* E6 glTF2-rendered three-shot positive control. Owned pinned embedded mesh only; no remote model/deploy. */
(async () => {
  'use strict';
  const section=document.querySelector('#story');
  const canvas=document.querySelector('#stage-canvas');
  const fallback=document.querySelector('#static-stage');
  const label=document.querySelector('#shot-label');
  const status=document.querySelector('#render-status');
  const reduce=matchMedia('(prefers-reduced-motion: reduce)');
  const shots=[
    {name:'arrival', label:'01 / Arrival', yaw:-0.47, pitch:0.17, shift:0.25},
    {name:'material',label:'02 / Material',yaw:0.72,pitch:-0.08,shift:0.21},
    {name:'precision',label:'03 / Precision',yaw:1.75,pitch:0.17,shift:0.16}
  ];
  let gl=null,program=null,buffer=null,pending=0,disposed=false,progress=0;
  const clamp=x=>Math.max(0,Math.min(1,x));
  const setFallback=reason=>{
    canvas.hidden=true;fallback.hidden=false;section.dataset.renderState=reason;
    status.textContent=reason==='reduced-static'
      ?'Reduced motion enabled. The static study remains readable.'
      :'3D unavailable. The static study and all chapter descriptions remain available.';
  };
  function cameraAt(t){
    const v=t*2,i=Math.min(1,Math.floor(v)),f=clamp(v-i),a=shots[i],b=shots[i+1];
    const ease=f*f*(3-2*f);
    return {
      yaw:a.yaw+(b.yaw-a.yaw)*ease,
      pitch:a.pitch+(b.pitch-a.pitch)*ease,
      shift:a.shift+(b.shift-a.shift)*ease
    };
  }
  function updateShot(t){
    const i=t<.33?0:t<.67?1:2;
    section.dataset.shot=shots[i].name;
    label.textContent=shots[i].label;
    section.dataset.progress=t.toFixed(3);
  }
  if(reduce.matches){setFallback('reduced-static');updateShot(0);return;}
  try{gl=canvas.getContext('webgl2',{
    alpha:false,antialias:true,preserveDrawingBuffer:true,powerPreference:'low-power'
  });}catch{gl=null;}
  if(!gl){setFallback('no-webgl');updateShot(0);return;}
  const vertex=[
    '#version 300 es','precision highp float;',
    'in vec3 aPosition; in vec3 aNormal;',
    'uniform vec3 uBoxPosition; uniform vec3 uBoxScale;',
    'uniform float uYaw; uniform float uPitch; uniform float uShift;',
    'out vec3 vNormal;',
    'void main(){',
    'float cy=cos(uYaw),sy=sin(uYaw),cx=cos(uPitch),sx=sin(uPitch);',
    'mat3 ry=mat3(cy,0.0,-sy,0.0,1.0,0.0,sy,0.0,cy);',
    'mat3 rx=mat3(1.0,0.0,0.0,0.0,cx,sx,0.0,-sx,cx);',
    'vec3 p=rx*ry*(aPosition*uBoxScale+uBoxPosition);',
    'p.z-=4.8;',
    'float perspective=2.7/max(2.5,-p.z);',
    'gl_Position=vec4(p.x*perspective*0.95+uShift,p.y*perspective*1.15,0.3+(-p.z)*0.04,1.0);',
    'vNormal=normalize(rx*ry*aNormal);',
    '}'
  ].join('\n');
  const fragment=[
    '#version 300 es','precision highp float;',
    'in vec3 vNormal; uniform vec3 uMaterial; out vec4 fragColor;',
    'void main(){',
    'vec3 n=normalize(vNormal);',
    'float primary=max(dot(n,normalize(vec3(-0.55,0.72,1.0))),0.0);',
    'float rim=pow(1.0-max(0.0,n.z),2.0);',
    'vec3 tint=uMaterial*(0.43+0.50*primary)+vec3(0.05,0.045,0.035)*rim;',
    'fragColor=vec4(tint,1.0);',
    '}'
  ].join('\n');
  function makeShader(kind,code){
    const sh=gl.createShader(kind);gl.shaderSource(sh,code);gl.compileShader(sh);
    if(!gl.getShaderParameter(sh,gl.COMPILE_STATUS)){
      gl.deleteShader(sh);throw Error('E6_STUDIO_SHADER_COMPILE_FAILED');
    }
    return sh;
  }

  const expectedAssetSha256='ace770e960909c2b788312dfd80e9d788ed89fd1eeb3f8008842b4e246af45a7';
  async function loadTrustedGltf(){
    const asset=await fetch('./reference_mesh.gltf',{
      method:'GET',cache:'no-store',credentials:'omit',redirect:'error'
    });
    if(!asset.ok || asset.status!==200)throw Error('E6_GLTF_ASSET_UNAVAILABLE');
    const raw=new Uint8Array(await asset.arrayBuffer());
    if(raw.byteLength<128||raw.byteLength>8192)throw Error('E6_GLTF_SIZE_REFUSED');
    const hash=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',raw)))
      .map(v=>v.toString(16).padStart(2,'0')).join('');
    if(hash!==expectedAssetSha256)throw Error('E6_GLTF_PIN_MISMATCH');
    const source=new TextDecoder('utf-8',{fatal:true}).decode(raw);
    const data=JSON.parse(source);
    const exactly=(a,b)=>Array.isArray(a)&&a.length===b;
    if(data.asset?.version!=='2.0'||
       data.asset.generator!=='Aetheris E6 human-authored cube research fixture v1'||
       data.asset.extras?.blenderExported!==false||
       data.asset.extras?.aiModelAuthored!==false||
       data.scene!==0||
       !exactly(data.scenes,1)||!exactly(data.nodes,1)||
       !exactly(data.meshes,1)||!exactly(data.buffers,1)||
       !exactly(data.bufferViews,1)||!exactly(data.accessors,2)||
       data.meshes[0].primitives?.[0]?.mode!==4||
       data.meshes[0].primitives?.[0]?.attributes?.POSITION!==0||
       data.meshes[0].primitives?.[0]?.attributes?.NORMAL!==1||
       data.bufferViews[0].byteStride!==24||
       data.bufferViews[0].byteLength!==864||
       data.buffers[0].byteLength!==864||
       data.accessors[0].count!==36||
       data.accessors[1].count!==36||
       data.accessors[0].componentType!==5126||
       data.accessors[1].componentType!==5126||
       data.accessors[0].type!=='VEC3'||
       data.accessors[1].type!=='VEC3')
      throw Error('E6_GLTF_UNSAFE_SCHEMA');
    const uri=data.buffers[0].uri;
    if(typeof uri!=='string'||!uri.startsWith('data:application/octet-stream;base64,'))
      throw Error('E6_GLTF_EXTERNAL_BUFFER_REFUSED');
    const blob=Uint8Array.from(atob(uri.slice('data:application/octet-stream;base64,'.length)),c=>c.charCodeAt(0));
    if(blob.byteLength!==864)throw Error('E6_GLTF_BAD_BUFFER');
    const view=new DataView(blob.buffer,blob.byteOffset,blob.byteLength);
    const geometry=new Float32Array(216);
    for(let i=0;i<216;i++){
      const value=view.getFloat32(i*4,true);
      if(!Number.isFinite(value)||Math.abs(value)>1)throw Error('E6_GLTF_INVALID_SCALAR');
      geometry[i]=value;
    }
    section.dataset.assetSha256=hash;
    section.dataset.assetVertices='36';
    section.dataset.geometryOrigin='vetted-gltf2-embedded-buffer';
    return geometry;
  }
  let vettedGeometry;
  try { vettedGeometry=await loadTrustedGltf(); }
  catch { setFallback('asset-invalid'); return; }
  let boxPosition,boxScale,yaw,pitch,shift,material;
  try{
    const vs=makeShader(gl.VERTEX_SHADER,vertex);
    const fs=makeShader(gl.FRAGMENT_SHADER,fragment);
    program=gl.createProgram();gl.attachShader(program,vs);gl.attachShader(program,fs);
    gl.linkProgram(program);gl.deleteShader(vs);gl.deleteShader(fs);
    if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw Error('E6_STUDIO_SHADER_LINK_FAILED');
    buffer=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,buffer);
    gl.bufferData(gl.ARRAY_BUFFER,vettedGeometry,gl.STATIC_DRAW);
    const stride=24,locA=gl.getAttribLocation(program,'aPosition'),locN=gl.getAttribLocation(program,'aNormal');
    gl.enableVertexAttribArray(locA);gl.vertexAttribPointer(locA,3,gl.FLOAT,false,stride,0);
    gl.enableVertexAttribArray(locN);gl.vertexAttribPointer(locN,3,gl.FLOAT,false,stride,12);
    boxPosition=gl.getUniformLocation(program,'uBoxPosition');
    boxScale=gl.getUniformLocation(program,'uBoxScale');
    yaw=gl.getUniformLocation(program,'uYaw');pitch=gl.getUniformLocation(program,'uPitch');
    shift=gl.getUniformLocation(program,'uShift');material=gl.getUniformLocation(program,'uMaterial');
  }catch{setFallback('shader-failed');return;}
  const parts=[
    {pos:[0,0,0],size:[0.82,1.25,0.16],color:[0.62,0.67,0.68]},
    {pos:[0,0,0.19],size:[0.69,1.05,0.04],color:[0.15,0.24,0.28]},
    {pos:[0,0.91,0.25],size:[0.45,0.022,0.02],color:[0.89,0.74,0.52]},
    {pos:[0,-1.03,0.19],size:[0.36,0.055,0.04],color:[0.52,0.55,0.56]}
  ];
  function render(){
    if(disposed||document.hidden||gl.isContextLost()||canvas.hidden)return;
    const dpr=Math.min(devicePixelRatio||1,1.5);
    const area=Math.max(1,canvas.clientWidth*canvas.clientHeight*dpr*dpr);
    const scale=Math.min(dpr,dpr*Math.sqrt(1200000/area));
    const w=Math.max(1,Math.round(canvas.clientWidth*scale));
    const h=Math.max(1,Math.round(canvas.clientHeight*scale));
    if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h;}
    gl.viewport(0,0,w,h);gl.clearColor(0.072,0.093,0.105,1);
    gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);gl.enable(gl.DEPTH_TEST);
    gl.useProgram(program);
    const cam=cameraAt(progress);
    gl.uniform1f(yaw,cam.yaw);gl.uniform1f(pitch,cam.pitch);gl.uniform1f(shift,cam.shift);
    for(const part of parts){
      gl.uniform3fv(boxPosition,part.pos);gl.uniform3fv(boxScale,part.size);
      gl.uniform3fv(material,part.color);gl.drawArrays(gl.TRIANGLES,0,36);
    }
    section.dataset.renderState='webgl2-active';
    section.dataset.frameCount=String(Number(section.dataset.frameCount||0)+1);
    section.dataset.canvasPixels=String(w*h);
  }
  function schedule(){
    if(disposed||pending||document.hidden)return;
    pending=requestAnimationFrame(()=>{
      pending=0;if(disposed)return;
      const span=Math.max(1,section.offsetHeight-innerHeight);
      progress=clamp(-section.getBoundingClientRect().top/span);
      updateShot(progress);render();
    });
  }
  function contextLost(event){
    event.preventDefault();disposed=true;
    if(pending)cancelAnimationFrame(pending);
    setFallback('context-lost');
  }
  canvas.addEventListener('webglcontextlost',contextLost,false);
  window.addEventListener('scroll',schedule,{passive:true});
  window.addEventListener('resize',schedule);
  document.addEventListener('visibilitychange',schedule);
  window.addEventListener('pagehide',()=>{
    disposed=true;if(pending)cancelAnimationFrame(pending);
    window.removeEventListener('scroll',schedule);
    window.removeEventListener('resize',schedule);
    document.removeEventListener('visibilitychange',schedule);
    if(!gl.isContextLost()){
      if(buffer)gl.deleteBuffer(buffer);
      if(program)gl.deleteProgram(program);
    }
  },{once:true});
  fallback.hidden=true;
  status.textContent='Interactive WebGL2 research study. Chapter descriptions are available below.';
  schedule();
})();
