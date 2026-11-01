/* E6 W4-C: tiny progressive bootstrap. Heavy Three/GSAP is never fetched
   until a user intentionally activates 3D. All static chapters remain native. */
const root=document.querySelector('[data-w4a-scene]');
if(root){
  const activate=root.querySelector('[data-w4a-activate]');
  const fallback=root.querySelector('[data-w4a-fallback]');
  const viewport=root.querySelector('[data-w4a-viewport]');
  const pause=root.querySelector('[data-w4a-pause]');
  const state=root.querySelector('[data-w4a-state]');
  const reduce=matchMedia('(prefers-reduced-motion: reduce)');
  let engaged=false;
  const ready=()=>{
    if(engaged)return;
    const noMotion=reduce.matches;
    activate.hidden=noMotion;
    pause.hidden=noMotion;
    fallback.hidden=false;viewport.hidden=true;
    root.dataset.engine=noMotion?'reduced-static':'static-ready';
    state.textContent=noMotion
      ?'Reduced-motion static Blender studio render. No 3D engine requested.'
      :'Original Blender studio render available. Activate optional 3D.';
  };
  const use3D=async()=>{
    if(engaged||reduce.matches)return;
    engaged=true;
    activate.disabled=true;activate.hidden=true;
    window.__w4cUserActivated=true;
    root.dataset.engine='loading';
    state.textContent='Loading local verified 3D engine on demand.';
    try{
      // Uses fixed same-origin module path; normal script CSP still applies.
      await import('/assets/signal-fold-engine.mjs');
      reduce.removeEventListener('change',ready);
    }catch(error){
      window.__w4cUserActivated=false;
      engaged=false;
      root.dataset.engine='static-fallback';
      fallback.hidden=false;viewport.hidden=true;
      activate.disabled=false;activate.hidden=false;
      state.textContent='Optional 3D unavailable. Static Blender render remains accessible.';
    }
  };
  activate.addEventListener('click',use3D,{once:true});
  reduce.addEventListener('change',ready);
  ready();
}
