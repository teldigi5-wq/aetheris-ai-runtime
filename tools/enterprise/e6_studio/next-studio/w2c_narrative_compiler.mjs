/**
 * E6 W2-C: deterministic narrative case-study pages over validated W1/W2-A/W2-B.
 * Human-authored fictional concepts; no AI, assets, 3D or release authority.
 */
import {createHash} from 'node:crypto';
import {compileCuratedW2Site} from './w2b_curated_compiler.mjs';

const sha=v=>createHash('sha256').update(v,'utf8').digest('hex');
const esc=v=>v.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')
  .replace(/"/g,'&quot;').replace(/'/g,'&#39;');
const fail=reason=>{throw new Error('E6_W2C_'+reason)};
const slug=/^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$/;
const safeText=(v,min,max)=>typeof v==='string'&&v.trim().length>=min&&v.length<=max&&
  !/[\x00-\x1f\x7f]/.test(v);
function exactRecord(v,required) {
  if(!v||typeof v!=='object'||Array.isArray(v)||
    (Object.getPrototypeOf(v)!==Object.prototype&&Object.getPrototypeOf(v)!==null))return false;
  const keys=Reflect.ownKeys(v);
  return keys.length===required.length&&keys.every(k=>typeof k==='string'&&
    required.includes(k)&&Object.hasOwn(Object.getOwnPropertyDescriptor(v,k)||{},'value'));
}
function safeArray(v,max){
  if(!Array.isArray(v)||Object.getPrototypeOf(v)!==Array.prototype||
     !v.length||v.length>max)return false;
  const keys=Reflect.ownKeys(v);
  if(keys.some(k=>k!=='length'&&(!/^(0|[1-9]\d*)$/.test(String(k))||Number(k)>=v.length)))return false;
  return Array.from({length:v.length},(_,i)=>Object.hasOwn(
    Object.getOwnPropertyDescriptor(v,String(i))||{},'value')).every(Boolean);
}
function validate(spec,brief,content) {
  if(!exactRecord(spec,['version','sourceRoute','sourceSection','studies'])||
    spec.version!==1||typeof spec.sourceRoute!=='string'||
    !/^\/[a-z][a-z0-9]*(?:\/[a-z][a-z0-9]*)*$/.test(spec.sourceRoute)||
    typeof spec.sourceSection!=='string'||!slug.test(spec.sourceSection)||
    !safeArray(spec.studies,6))fail('INVALID_CONFIGURATION');
  const p=brief.pages.find(x=>x.path===spec.sourceRoute);
  const cp=content.pages.find(x=>x.path===spec.sourceRoute);
  if(!p||!cp)fail('UNKNOWN_SOURCE_ROUTE');
  const index=p.sections.findIndex(x=>x.id===spec.sourceSection);
  if(index<0||!['gallery','case-studies'].includes(p.sections[index].kind))
    fail('INVALID_SOURCE_SECTION');
  const items=cp.sections[index].items;
  if(!Array.isArray(items))fail('INVALID_ITEMS');
  const titles=new Set(items.map(x=>x.title)),seen=new Set(),seenTitles=new Set();
  for(const st of spec.studies){
    if(!exactRecord(st,['slug','sourceTitle','eyebrow','headline','challenge','concept','reviewPlan'])||
      typeof st.slug!=='string'||st.slug.length>45||!slug.test(st.slug)||
      !safeText(st.sourceTitle,3,76)||!safeText(st.eyebrow,3,55)||
      !safeText(st.headline,8,120)||!safeText(st.challenge,30,600)||
      !safeText(st.concept,30,600)||!safeText(st.reviewPlan,30,600)||
      seen.has(st.slug)||seenTitles.has(st.sourceTitle)||!titles.has(st.sourceTitle))
      fail('INVALID_STUDY_RECORD');
    seen.add(st.slug);seenTitles.add(st.sourceTitle);
    if(brief.pages.some(x=>x.path===spec.sourceRoute+'/'+st.slug))
      fail('ROUTE_COLLISION');
  }
}
function casePage(sourcePage,spec,study,brand,email) {
  const target=spec.sourceRoute+'/'+study.slug,href=target+'/';
  const sections=[
    ['context','01 / question','What needs to be understood?',study.challenge],
    ['response','02 / exploration','The proposed approach.',study.concept],
    ['verification','03 / validation plan','What must still be tested.',study.reviewPlan]
  ];
  const title=esc(study.sourceTitle),headline=esc(study.headline);
  const contents='<nav class="story-toc" aria-label="Case-study chapters">'+sections.map((c,i)=>
    '<a href="#'+c[0]+'"'+(i===0?' aria-current="location"':'')+'>'+
    esc(c[1])+'</a>').join('')+'</nav>';
  const chapters=sections.map(c=>
    '<section class="story-chapter" id="'+c[0]+'"><p class="eyebrow">'+esc(c[1])+
    '</p><div class="chapter-rule" aria-hidden="true"></div><h2>'+esc(c[2])+
    '</h2><p>'+esc(c[3])+'</p></section>').join('');
  const main='<main id="main" class="story-main" data-w2c-study="'+esc(study.slug)+'">'+
    '<section class="story-mast" aria-labelledby="case-title"><div class="story-headline">'+
    '<p class="eyebrow">'+esc(study.eyebrow)+' / Fictional concept</p>'+
    '<h1 id="case-title">'+headline+'</h1><p class="lead">'+title+
    ' is an invented design exercise, not a commissioned product or verified customer outcome.</p>'+
    '<a class="story-back" href="'+spec.sourceRoute+'/">← Back to concept index</a></div>'+
    '<div class="story-object" aria-hidden="true"><span>ORIGINAL / CONCEPT / 0'+
    (spec.studies.findIndex(s=>s.slug===study.slug)+1)+'</span></div></section>'+
    '<div class="story-layout">'+contents+'<div class="story-chapters">'+chapters+'</div></div>'+
    '<section class="story-end"><p class="eyebrow">Keep exploring</p>'+
    '<h2>Good questions deserve careful work.</h2>'+
    '<a class="text-link" href="'+spec.sourceRoute+'/">Explore other concepts ↗</a> '+
    '<a class="text-link" href="/contact/">Start a conversation ↗</a></section></main>';
  const opening='<main id="main">',closing='</main>';
  const start=sourcePage.indexOf(opening),end=sourcePage.indexOf(closing,start);
  if(start<0||end<0||sourcePage.indexOf(opening,start+1)>=0)fail('BASE_TEMPLATE_MISMATCH');
  let html=sourcePage.slice(0,start)+main+sourcePage.slice(end+closing.length);
  const titleTag=/<title>[^<]*<\/title>/;
  if(!titleTag.test(html))fail('TITLE_MISSING');
  html=html.replace(titleTag,'<title>'+title+' — '+esc(brand)+'</title>');
  if(!html.includes('<meta name="robots" content="noindex,nofollow">')||
    !html.includes('Content-Security-Policy'))fail('SAFETY_HEADERS_MISSING');
  return html;
}
export function compileNarrativeW2Site(brief,content,direction,spec) {
  // Parse all inbound structures through the inherited W1/W2 validation first.
  const compiled=compileCuratedW2Site(brief,content,direction);
  validate(spec,brief,content);
  const route=spec.sourceRoute.slice(1)+'/index.html';
  const gallery=compiled.files.get(route);
  if(!gallery)fail('SOURCE_PAGE_MISSING');
  const files=new Map([...compiled.files].filter(([path])=>path!=='build-evidence.json'));
  let updated=gallery;
  for(const study of spec.studies){
    const sourceTitle=esc(study.sourceTitle);
    const titleToken='<h3>'+sourceTitle+'</h3>';
    if(updated.split(titleToken).length!==2)fail('AMBIGUOUS_GALLERY_TITLE');
    updated=updated.replace(titleToken,titleToken+
      '<a class="study-action" href="'+spec.sourceRoute+'/'+study.slug+
      '/" aria-label="Read fictional concept '+sourceTitle+'">Explore concept ↗</a>');
    const path=spec.sourceRoute.slice(1)+'/'+study.slug+'/index.html';
    if(files.has(path))fail('DUPLICATE_OUTPUT_PATH');
    files.set(path,casePage(gallery,spec,study,content.siteName,content.contactEmail));
  }
  files.set(route,updated);
  const css="\n/* Authored W2-C story architecture: readable without JS and reduced-motion first. */\n.story-main{background:var(--paper);min-height:100vh}\n.story-mast{background:var(--deep);color:#f6f5f0;min-height:76svh;display:grid;grid-template-columns:minmax(0,1.15fr) minmax(18rem,.85fr);overflow:hidden}\n.story-headline{align-self:end;padding:clamp(2rem,8vw,8rem) clamp(1.5rem,7vw,7rem)}\n.story-headline .eyebrow{color:#f4a78f}.story-headline h1{font-size:clamp(3.8rem,8.8vw,10.5rem);max-width:11ch}\n.story-headline .lead{color:#d5e1db}\n.story-object{position:relative;min-height:21rem;background:var(--mint);overflow:hidden;isolation:isolate}\n.story-object:before{content:\"\";position:absolute;inset:12%;border:clamp(2rem,5vw,6rem) solid var(--accent);transform:rotate(-27deg)}\n.story-object:after{content:\"\";position:absolute;width:42%;height:42%;left:32%;top:25%;background:var(--deep);border-radius:50%;box-shadow:3rem 3rem 0 var(--paper)}\n.story-object span{position:absolute;bottom:1.6rem;right:1.6rem;color:var(--deep);font-weight:700;font-size:.7rem;letter-spacing:.18em;z-index:2}\n.story-layout{display:grid;grid-template-columns:minmax(12rem,24%) minmax(0,1fr);gap:clamp(1.5rem,6vw,7rem);padding:0 max(1.5rem,calc((100vw - 1350px)/2))}\n.story-toc{position:sticky;top:2rem;align-self:start;margin:4rem 0;display:grid;gap:.6rem}\n.story-toc a{display:block;padding:.85rem 1rem;border-left:2px solid var(--rule);text-decoration:none;font-size:.8rem;letter-spacing:.07em;text-transform:uppercase}\n.story-toc a[aria-current=\"location\"]{border-left:3px solid var(--accent);background:color-mix(in srgb,var(--accent) 9%,transparent)}\n.story-chapters{min-width:0}\n.story-chapter{min-height:min(68svh,45rem);padding:clamp(4rem,9vw,9rem) 0;border-bottom:1px solid var(--rule);scroll-margin-top:2rem}\n.story-chapter .eyebrow{display:block;margin-bottom:2rem}\n.story-chapter h2{font-size:clamp(2.6rem,5.1vw,5.5rem);max-width:17ch;margin-bottom:2.5rem}\n.story-chapter p:not(.eyebrow){font-size:clamp(1.12rem,1.45vw,1.4rem);max-width:62ch;line-height:1.85;color:var(--muted)}\n.story-chapter .chapter-rule{width:100%;height:1px;background:var(--accent);margin-bottom:2rem;max-width:6rem}\n.story-end{padding:clamp(3rem,7vw,7rem);background:var(--deep);color:var(--paper)}\n.story-end h2{max-width:18ch}.story-end .text-link{color:var(--paper)}\n.story-back{display:inline-flex;color:inherit;text-decoration:none;font-size:.8rem;text-transform:uppercase;letter-spacing:.1em;border-bottom:1px solid currentColor;padding-bottom:.4rem}\n.story-main :focus-visible{outline:3px solid var(--accent);outline-offset:5px}\n@media(max-width:850px){.story-mast{grid-template-columns:1fr}.story-headline{padding:4rem 1.5rem}.story-object{min-height:17rem}.story-layout{grid-template-columns:1fr}.story-toc{position:static;margin:1.5rem 0 0;display:flex;flex-wrap:wrap;gap:.2rem}.story-toc a{padding:.75rem;border-left:0;border-bottom:2px solid var(--rule)}.story-toc a[aria-current=\"location\"]{border-left:0;border-bottom:3px solid var(--accent)}.story-chapter{min-height:0}.story-end{padding:3rem 1.5rem}}\n@media(prefers-reduced-motion:reduce){.story-main *{scroll-behavior:auto!important;animation:none!important;transition:none!important}}\n";
  const js="\n/* Progressive enhancement only: anchor links work with JavaScript disabled. */\n(()=>{'use strict';\n  const nav=document.querySelector('.story-toc');\n  if(!nav||!('IntersectionObserver' in window))return;\n  const links=[...nav.querySelectorAll('a[href^=\"#\"]')];\n  const pairs=links.map(a=>[document.getElementById(a.hash.slice(1)),a]).filter(x=>x[0]);\n  if(!pairs.length)return;\n  const update=id=>{for(const [section,link] of pairs){\n    if(section.id===id)link.setAttribute('aria-current','location');\n    else link.removeAttribute('aria-current');\n  }};\n  update(pairs[0][0].id);\n  const seen=new Map();\n  const io=new IntersectionObserver(entries=>{\n    for(const entry of entries)seen.set(entry.target.id,entry.isIntersecting);\n    const found=pairs.find(([section])=>seen.get(section.id));\n    if(found)update(found[0].id);\n  },{rootMargin:'-10% 0px -55% 0px',threshold:0});\n  for(const [section] of pairs)io.observe(section);\n  window.addEventListener('pagehide',()=>io.disconnect(),{once:true});\n})();\n";
  files.set('assets/studio.css',files.get('assets/studio.css')+'\n'+css+
    '\n.study-action{display:inline-block;margin:0 1.7rem 1.7rem;border-bottom:1px solid currentColor;text-decoration:none;font-size:.83rem;font-weight:700;letter-spacing:.07em;text-transform:uppercase}\n');
  files.set('assets/studio.js',files.get('assets/studio.js')+'\n'+js);
  const hashes=Object.fromEntries([...files].map(([path,body])=>[path,sha(body)])
    .sort(([a],[b])=>a.localeCompare(b)));
  const evidence={
    kind:'E6_W2C_NARRATIVE_PAGES_V1',projectSlug:compiled.evidence.projectSlug,
    routes:[...compiled.evidence.routes,...spec.studies.map(s=>spec.sourceRoute+'/'+s.slug)],
    direction:compiled.evidence.preset,caseStudyCount:spec.studies.length,fileSha256:hashes,
    generatedConceptDisclosure:true,genuineModelAuthorshipProven:false,realBrowserVerified:false,
    physicalGpuMeasured:false,humanReviewed:false,ownerApproved:false,
    deploymentAuthorized:false,stage45Go:false,releaseGo:false,
    limitations:['Fictional human-authored case-study content, not client evidence',
      'IntersectionObserver updates optional chapter rail only; native scrolling remains intact',
      'Static CSS artwork only; W3 licensed visuals and W4 cinematic 3D remain unimplemented',
      'No independent physical-device WCAG, GPU, design or model-authorship certification']
  };
  files.set('build-evidence.json',JSON.stringify(evidence,null,2)+'\n');
  return {files,evidence};
}
