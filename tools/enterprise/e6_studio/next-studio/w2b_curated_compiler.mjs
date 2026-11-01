/**
 * E6 W2-B: bounded, deterministic art direction for previously validated W1/W2 briefs.
 * Curated human-written presets; no AI, remote assets, arbitrary CSS or release authority.
 */
import {createHash} from 'node:crypto';
import {compileW2Site} from './w2_compiler.mjs';

const DIRECTIONS = Object.freeze({
  'mineral-editorial': Object.freeze({description:'Warm architectural paper, deep pine typography, terracotta geometry',css:"html[data-art-direction=\"mineral-editorial\"]{--ink:#213b37;--paper:#f5f0e6;--muted:#5d6a62;--rule:#c9c4b9;--accent:#a84c33;--deep:#1f3735;--mint:#c0d0ba}\nhtml[data-art-direction=\"mineral-editorial\"] .hero-art{background:#d9d0bc}\nhtml[data-art-direction=\"mineral-editorial\"] .hero-arc{border-color:#a84c33;border-radius:2rem;transform:rotate(34deg) translate(-15%,25%);width:70%}\nhtml[data-art-direction=\"mineral-editorial\"] .hero-disc{background:#304b42;clip-path:polygon(50% 0,100% 50%,50% 100%,0 50%);width:33%}\nhtml[data-art-direction=\"mineral-editorial\"] .section-editorial,html[data-art-direction=\"mineral-editorial\"] .section-process{background:#e6e0d4}\nhtml[data-art-direction=\"mineral-editorial\"] .project-card{background:#fcf7ec}\nhtml[data-art-direction=\"mineral-editorial\"] .art-1{background:#d6c6b5}\nhtml[data-art-direction=\"mineral-editorial\"] .art-2{background:#b7c3b4}\nhtml[data-art-direction=\"mineral-editorial\"] .art-3{background:#d8b2a0}"}),
  'ink-monograph': Object.freeze({description:'Graphite and chalk editorial grid with vermilion accents',css:"html[data-art-direction=\"ink-monograph\"]{--ink:#171d24;--paper:#f5f3ec;--muted:#4b5860;--rule:#b4bcb9;--accent:#b7372b;--deep:#151b23;--mint:#d3d9d2}\nhtml[data-art-direction=\"ink-monograph\"] .hero{background:#f5f3ec;color:#151b23;border-bottom:1px solid #171d24}\nhtml[data-art-direction=\"ink-monograph\"] .hero-lead{color:#485159}\nhtml[data-art-direction=\"ink-monograph\"] .hero .eyebrow{color:#b7372b}\nhtml[data-art-direction=\"ink-monograph\"] .hero-art{background:#b7372b}\nhtml[data-art-direction=\"ink-monograph\"] .hero-art:before{border-color:#f5f3ec}\nhtml[data-art-direction=\"ink-monograph\"] .hero-arc{width:90%;border-color:#111b27;border-radius:0;transform:rotate(47deg) translate(8%,22%);border-width:clamp(2rem,4vw,4rem)}\nhtml[data-art-direction=\"ink-monograph\"] .hero-disc{background:#e3e1d8;clip-path:circle(50% at 50% 50%);width:36%;transform:translate(-8%,-10%)}\nhtml[data-art-direction=\"ink-monograph\"] .hero-art-label{color:#fff}\nhtml[data-art-direction=\"ink-monograph\"] h1,html[data-art-direction=\"ink-monograph\"] h2,html[data-art-direction=\"ink-monograph\"] h3{letter-spacing:-.045em}\nhtml[data-art-direction=\"ink-monograph\"] .section-editorial,html[data-art-direction=\"ink-monograph\"] .section-process{background:#e9e8e2}\nhtml[data-art-direction=\"ink-monograph\"] .art-1{background:#deded6}\nhtml[data-art-direction=\"ink-monograph\"] .art-2{background:#b7372b}\nhtml[data-art-direction=\"ink-monograph\"] .art-3{background:#2d3d4d}"})
});
const sha = value => createHash('sha256').update(value,'utf8').digest('hex');
const die = reason => {throw new Error('E6_W2B_'+reason);};
function validateDirection(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value) ||
      (Object.getPrototypeOf(value) !== Object.prototype && Object.getPrototypeOf(value) !== null))
    die('INVALID_DIRECTION');
  const keys=Reflect.ownKeys(value);
  if (keys.length!==2 || !keys.includes('version') || !keys.includes('preset') ||
      keys.some(k=>typeof k!=='string'||!Object.hasOwn(Object.getOwnPropertyDescriptor(value,k)||{},'value')))
    die('INVALID_DIRECTION');
  if (value.version!==1 || typeof value.preset!=='string' ||
      !Object.hasOwn(DIRECTIONS,value.preset)) die('UNSUPPORTED_PRESET');
  return value.preset;
}
export function compileCuratedW2Site(brief,content,direction) {
  const preset=validateDirection(direction);
  const result=compileW2Site(brief,content);
  const files=new Map();
  for(const [path,data] of result.files) {
    if(path==='build-evidence.json') continue;
    if(path==='assets/studio.css') files.set(path,data+'\n'+DIRECTIONS[preset].css+'\n');
    else if(path.endsWith('/index.html')||path==='index.html') {
      const needle='<html lang="en">';
      if(!data.includes(needle)) die('INCOMPATIBLE_HTML_BASE');
      files.set(path,data.replace(needle,'<html lang="en" data-art-direction="'+preset+'">'));
    } else files.set(path,data);
  }
  const fileSha256=Object.fromEntries([...files].map(([name,bytes])=>[name,sha(bytes)]).sort((a,b)=>a[0].localeCompare(b[0])));
  const evidence=Object.freeze({
    kind:'E6_W2B_CURATED_MULTIBRIEF_V1',projectSlug:result.evidence.projectSlug,
    routes:result.evidence.routes,preset,designAuthorship:'HUMAN_AUTHORED_PRESET',
    fileSha256,deterministicCompiler:true,genuineModelAuthorshipProven:false,
    realBrowserVerified:false,physicalGpuMeasured:false,humanReviewed:false,
    ownerApproved:false,deploymentAuthorized:false,stage45Go:false,releaseGo:false,
    limitations:[
      'Preset is curated human-authored CSS, not generated by AI',
      'Static CSS artwork contains no photography, media rights approval or 3D scene',
      'Contact opens a local email draft; no backend message delivery',
      'No independent designer acceptance, WCAG 2.2 AA or physical GPU certification'
    ]
  });
  files.set('build-evidence.json',JSON.stringify(evidence,null,2)+'\n');
  return {files,evidence};
}
export const W2B_PRESETS=Object.freeze(Object.keys(DIRECTIONS));
