/* E3.9H INERT controlled rendering reference.
 * Procedural local WebGL2. No imports, network, telemetry, cookies, external assets,
 * model inference, arbitrary scripts, app execution or owner-device control.
 * Not evidence of agent-authored creative production.
 */
(() => {
  'use strict';
  const section = document.querySelector('#chapter');
  const canvas = document.querySelector('#study-canvas');
  const fallback = document.querySelector('#static-fallback');
  const status = document.querySelector('#motion-status');
  const reduce = window.matchMedia('(prefers-reduced-motion: reduce)');
  let gl = null, program = null, buffer = null, frame = 0, progress = 0;
  let disposed = false;
  const clamp = value => Math.max(0, Math.min(1, value));
  const showFallback = (reason) => {
    canvas.hidden = true;
    fallback.hidden = false;
    section.dataset.motionState = reason;
    status.textContent = reason === 'reduced-static'
      ? 'Reduced-motion preference respected. The full study remains readable.'
      : '3D view unavailable. The static study remains available.';
  };
  if (reduce.matches) { showFallback('reduced-static'); return; }
  try { gl = canvas.getContext('webgl2', {alpha: false, antialias: true, preserveDrawingBuffer: true}); }
  catch { gl = null; }
  if (!gl) { showFallback('no-webgl'); return; }
  function makeShader(kind, source) {
    const shader = gl.createShader(kind);
    gl.shaderSource(shader, source);
    gl.compileShader(shader);
    if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
      const message = gl.getShaderInfoLog(shader);
      gl.deleteShader(shader);
      throw Error('Shader compilation failed: ' + message);
    }
    return shader;
  }
  function createGeometry() {
    const q = [
      [[-0.72,-1.3,0.5],[0.72,-1.3,0.5],[0.72,1.3,0.5],[-0.72,1.3,0.5],[0,0,1]],
      [[0.72,-1.3,-0.5],[-0.72,-1.3,-0.5],[-0.72,1.3,-0.5],[0.72,1.3,-0.5],[0,0,-1]],
      [[0.72,-1.3,0.5],[0.72,-1.3,-0.5],[0.72,1.3,-0.5],[0.72,1.3,0.5],[1,0,0]],
      [[-0.72,-1.3,-0.5],[-0.72,-1.3,0.5],[-0.72,1.3,0.5],[-0.72,1.3,-0.5],[-1,0,0]],
      [[-0.72,1.3,0.5],[0.72,1.3,0.5],[0.72,1.3,-0.5],[-0.72,1.3,-0.5],[0,1,0]],
      [[-0.72,-1.3,-0.5],[0.72,-1.3,-0.5],[0.72,-1.3,0.5],[-0.72,-1.3,0.5],[0,-1,0]]
    ];
    const vertices = [];
    for (const face of q) {
      for (const index of [0,1,2,0,2,3]) vertices.push(...face[index], ...face[4]);
    }
    return new Float32Array(vertices);
  }
  const vertexSource = `#version 300 es
precision highp float;
in vec3 aPosition;
in vec3 aNormal;
uniform float uRotation;
uniform float uProgress;
out vec3 vNormal;
void main(){
 float c=cos(uRotation),s=sin(uRotation);
 mat3 spin=mat3(c,0.0,-s, 0.0,1.0,0.0, s,0.0,c);
 mat3 tilt=mat3(1.0,0.0,0.0, 0.0,0.978,-0.208, 0.0,0.208,0.978);
 vec3 p=spin*tilt*aPosition;
 p.y+=uProgress*0.07;
 p.z-=5.0;
 vNormal=spin*tilt*aNormal;
 gl_Position=vec4(p.x*1.60, p.y*1.20, -p.z-0.2, -p.z);
}`;
  const fragmentSource = `#version 300 es
precision highp float;
in vec3 vNormal;
out vec4 fragColor;
void main(){
 vec3 normal=normalize(vNormal);
 float light=max(dot(normal,normalize(vec3(-0.6,0.9,1.3))),0.0);
 vec3 stone=mix(vec3(0.25,0.18,0.13),vec3(0.95,0.82,0.64),0.30+light*0.70);
 fragColor=vec4(stone,1.0);
}`;
  try {
    const vs=makeShader(gl.VERTEX_SHADER,vertexSource);
    const fs=makeShader(gl.FRAGMENT_SHADER,fragmentSource);
    program=gl.createProgram();
    gl.attachShader(program,vs);
    gl.attachShader(program,fs);
    gl.linkProgram(program);
    gl.deleteShader(vs);
    gl.deleteShader(fs);
    if(!gl.getProgramParameter(program,gl.LINK_STATUS)) throw Error('Shader linking failed');
    buffer=gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER,buffer);
    gl.bufferData(gl.ARRAY_BUFFER,createGeometry(),gl.STATIC_DRAW);
    const stride=6*Float32Array.BYTES_PER_ELEMENT;
    const pLoc=gl.getAttribLocation(program,'aPosition');
    const nLoc=gl.getAttribLocation(program,'aNormal');
    gl.enableVertexAttribArray(pLoc);
    gl.vertexAttribPointer(pLoc,3,gl.FLOAT,false,stride,0);
    gl.enableVertexAttribArray(nLoc);
    gl.vertexAttribPointer(nLoc,3,gl.FLOAT,false,stride,3*Float32Array.BYTES_PER_ELEMENT);
  } catch (error) {
    console.error('E3.9H_SHADERS_FAILED',String(error));
    showFallback('shader-failed');
    return;
  }
  const rotationLoc=gl.getUniformLocation(program,'uRotation');
  const progressLoc=gl.getUniformLocation(program,'uProgress');
  const render=() => {
    if(disposed || !gl || gl.isContextLost()) return;
    const ratio=Math.min(window.devicePixelRatio||1,1.75);
    const width=Math.max(1,Math.round(canvas.clientWidth*ratio));
    const height=Math.max(1,Math.round(canvas.clientHeight*ratio));
    if(canvas.width!==width || canvas.height!==height){canvas.width=width;canvas.height=height;}
    gl.viewport(0,0,canvas.width,canvas.height);
    gl.clearColor(0.125,0.105,0.085,1);
    gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);
    gl.enable(gl.DEPTH_TEST);
    gl.useProgram(program);
    gl.uniform1f(rotationLoc,progress*2.18+0.17);
    gl.uniform1f(progressLoc,progress);
    gl.drawArrays(gl.TRIANGLES,0,36);
    section.dataset.motionState='webgl2-active';
    section.dataset.progress=progress.toFixed(3);
    section.dataset.frameCount=String(Number(section.dataset.frameCount||0)+1);
  };
  function schedule() {
    if(frame || disposed) return;
    frame=window.requestAnimationFrame(()=>{
      frame=0;
      const available=Math.max(1,section.offsetHeight-window.innerHeight);
      progress=clamp(-section.getBoundingClientRect().top/available);
      render();
    });
  }
  function lost(event) {
    event.preventDefault();
    disposed=true;
    if(frame) window.cancelAnimationFrame(frame);
    showFallback('context-lost');
  }
  canvas.addEventListener('webglcontextlost',lost,false);
  window.addEventListener('scroll',schedule,{passive:true});
  window.addEventListener('resize',schedule);
  window.addEventListener('pagehide',()=>{
    disposed=true;
    if(frame) window.cancelAnimationFrame(frame);
    window.removeEventListener('scroll',schedule);
    window.removeEventListener('resize',schedule);
    if(buffer) gl.deleteBuffer(buffer);
    if(program) gl.deleteProgram(program);
  },{once:true});
  fallback.hidden=true;
  status.textContent='Scroll to examine a real WebGL2-rendered geometry study.';
  schedule();
})();
