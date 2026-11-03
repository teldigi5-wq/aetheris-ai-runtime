/** E6 W5-A. Pure closed-schema compiler: model proposes prose, page
 * architecture and art tokens. No model-supplied HTML/CSS/JS, URLs or paths.
 * Schema acceptance is NOT evidence that an actual model was called.
 */
import {createHash} from 'node:crypto';
const sha=s=>createHash('sha256').update(s,'utf8').digest('hex');
const fail=s=>{throw Error('E6_W5A_'+s);};
const rec=(v,keys)=>!!v&&typeof v==='object'&&!Array.isArray(v)&&
  (Object.getPrototypeOf(v)===Object.prototype||Object.getPrototypeOf(v)===null)&&
  Reflect.ownKeys(v).length===keys.length&&
  keys.every(k=>Object.hasOwn(v,k)&&Object.hasOwn(Object.getOwnPropertyDescriptor(v,k)||{},'value'))&&
  Reflect.ownKeys(v).every(k=>typeof k==='string'&&keys.includes(k));
const arr=(v,min,max)=>Array.isArray(v)&&Object.getPrototypeOf(v)===Array.prototype&&
  v.length>=min&&v.length<=max&&
  Reflect.ownKeys(v).every(k=>k==='length'||(/^(0|[1-9][0-9]*)$/.test(k)&&Number(k)<v.length))&&
  Array.from({length:v.length},(_,i)=>i).every(i=>Object.hasOwn(v,i));
const plain=(v,min,max)=>typeof v==='string'&&v.length>=min&&v.length<=max&&
  v.trim().length>=min&&!/[\x00-\x1f\x7f]/.test(v)&&
  !/https?:\/\/|www\.|javascript:|data:|<[^>]*>|\$\{\{/.test(v);
const esc=v=>v.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')
 .replace(/"/g,'&quot;').replace(/'/g,'&#39;');
const profiles=new Set(['creative-studio','product','portfolio','agency','fashion']);
const compositions=new Set(['split','offset','typographic']);
const motifs=new Set(['rules','material-block','letterform']);
const hex=/^#[0-9a-fA-F]{6}$/;
const linear=n=>{const v=n/255;return v<=.04045?v/12.92:((v+.055)/1.055)**2.4;};
export function contrast(a,b){
  const l=s=>{
    const c=[1,3,5].map(i=>linear(parseInt(s.slice(i,i+2),16)));
    return .2126*c[0]+.7152*c[1]+.0722*c[2];
  };
  const x=l(a),y=l(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);
}
export function validateOwnerBrief(b){
  if(!rec(b,['version','projectSlug','siteProfile','audience','purpose','fictional','constraints'])||
    b.version!==1||!profiles.has(b.siteProfile)||
    typeof b.projectSlug!=='string'||b.projectSlug.length>48||
    !/^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$/.test(b.projectSlug)||
    b.fictional!==true||!plain(b.audience,15,240)||
    !plain(b.purpose,30,360)||!arr(b.constraints,2,6)||
    b.constraints.some(x=>!plain(x,12,160)))
    fail('INVALID_OR_NONFICTIONAL_BRIEF');
  return b;
}
export function validateModelIdentity(b,d){
  validateOwnerBrief(b);
  if(!rec(d,['version','projectSlug','siteName','tagline','description','art'])||
    d.version!==1||d.projectSlug!==b.projectSlug||
    !plain(d.siteName,3,70)||!plain(d.tagline,8,100)||
    !plain(d.description,25,240)||
    !rec(d.art,['paper','ink','accent','surface','composition','motif','density'])||
    !compositions.has(d.art.composition)||!motifs.has(d.art.motif)||
    !['airy','measured','compact'].includes(d.art.density)||
    !['paper','ink','accent','surface'].every(k=>hex.test(d.art[k]))||
    contrast(d.art.paper,d.art.ink)<4.5||
    contrast(d.art.paper,d.art.accent)<4.5||
    contrast(d.art.surface,d.art.ink)<4.5||
    contrast(d.art.surface,d.art.accent)<4.5)
    fail('MODEL_SCHEMA_OR_CONTRAST_REJECTED');
  return d;
}
export function validateModelPage(p){
  if(!rec(p,['path','navLabel','heading','lede','sections'])||
    !['/','/approach'].includes(p.path)||
    !plain(p.navLabel,3,24)||!plain(p.heading,8,95)||
    !plain(p.lede,35,260)||!arr(p.sections,2,3))
    fail('INVALID_PAGE_ARCHITECTURE');
  for(const sec of p.sections){
    if(!rec(sec,['eyebrow','title','body','alignment'])||
      !plain(sec.eyebrow,3,42)||!plain(sec.title,8,100)||
      !plain(sec.body,40,400)||
      !['left','center','right'].includes(sec.alignment))
      fail('UNTRUSTED_PAGE_CONTENT');
  }
  return p;
}
export function validateModelProposal(b,d){
  validateOwnerBrief(b);
  if(!rec(d,['version','projectSlug','siteName','tagline','description','art','pages'])||
    !arr(d.pages,2,2))fail('MODEL_SCHEMA_OR_CONTRAST_REJECTED');
  const {pages,...identity}=d;
  validateModelIdentity(b,identity);
  const byPath=new Map();
  for(const p of d.pages){
    validateModelPage(p);
    if(byPath.has(p.path))fail('INVALID_PAGE_ARCHITECTURE');
    byPath.set(p.path,p);
  }
  if(!byPath.has('/')||!byPath.has('/approach')||
    byPath.get('/').heading===byPath.get('/approach').heading)
    fail('INCOMPLETE_PAGE_IDENTITY');
  return byPath;
}
export function compileModelProposedSite(brief,proposal,baseCss){
  const pages=validateModelProposal(brief,proposal);
  if(typeof baseCss!=='string'||baseCss.length<1000||
    baseCss.length>15_000||!baseCss.includes('prefers-reduced-motion'))
    fail('INVALID_TRUSTED_STYLESHEET');
  const files=new Map();
  const {art}=proposal;
  const tokens=':root{--paper:'+art.paper+';--ink:'+art.ink+
    ';--accent:'+art.accent+';--surface:'+art.surface+';color-scheme:light}\n';
  files.set('assets/site.css',tokens+baseCss);
  for(const [path,page] of pages){
    const nav=[...pages].map(([to,p])=>
      '<a href="'+(to==='/'?'/':to+'/')+'"'+(to===path?' aria-current="page"':'')+
      '>'+esc(p.navLabel)+'</a>').join('');
    const sections=page.sections.map((s,i)=>
      '<section class="section" data-align="'+s.alignment+'" aria-labelledby="h-'+i+'">'+
      '<div class="section__content"><p class="eyebrow">'+esc(s.eyebrow)+
      '</p><div><h2 id="h-'+i+'">'+esc(s.title)+'</h2><p class="section__text">'+
      esc(s.body)+'</p></div></div></section>').join('\n');
    const html='<!doctype html><html lang="en"><head><meta charset="utf-8">'+
      '<meta name="viewport" content="width=device-width,initial-scale=1">'+
      '<meta name="robots" content="noindex,nofollow">'+
      '<meta name="description" content="'+esc(proposal.description)+'">'+
      '<meta http-equiv="Content-Security-Policy" content="default-src &#39;none&#39;; style-src &#39;self&#39;; script-src &#39;none&#39;; img-src &#39;none&#39;; connect-src &#39;none&#39;; form-action &#39;none&#39;; base-uri &#39;none&#39;">'+
      '<title>'+esc(page.heading)+' — '+esc(proposal.siteName)+'</title>'+
      '<link rel="stylesheet" href="/assets/site.css"></head><body>'+
      '<a class="skip" href="#main">Skip to content</a>'+
      '<header class="header"><a class="brand" href="/">'+esc(proposal.siteName)+'</a>'+
      '<nav class="nav" aria-label="Primary">'+nav+'</nav></header>'+
      '<main id="main" tabindex="-1" class="density--'+art.density+' motif--'+art.motif+'">'+
      '<section class="hero hero--'+art.composition+'" aria-labelledby="page-title">'+
      '<div class="hero__type"><p class="eyebrow">'+esc(proposal.tagline)+'</p>'+
      '<h1 id="page-title">'+esc(page.heading)+'</h1><p class="lede">'+esc(page.lede)+'</p></div>'+
      '<div class="hero__figure" aria-hidden="true"></div></section>'+
      sections+'</main><footer class="footer"><strong>'+esc(proposal.siteName)+'</strong>'+
      '<p class="footnote">Fictional research demonstration. Model-proposed wording and visual direction are unverified. No real clients, products, endorsements or commercial outcomes are claimed. No visitor data is collected.</p></footer>'+
      '</body></html>\n';
    files.set(path==='/'?'index.html':path.slice(1)+'/index.html',html);
  }
  const fileSha256=Object.fromEntries([...files].map(([name,body])=>
    [name,sha(body)]).sort((a,b)=>a[0].localeCompare(b[0])));
  const evidence={
    kind:'E6_W5A_BOUNDED_PROPOSAL_COMPILER_V1',
    projectSlug:brief.projectSlug,siteProfile:brief.siteProfile,
    proposalSha256:sha(JSON.stringify(proposal)),fileSha256,
    modelCalled:false,realModelAuthorshipVerified:false,
    executableModelCodeAccepted:false,generatedStaticPages:2,
    visualApproval:false,contentClaimsVerified:false,
    accessibilityVerified:false,ownerApproved:false,
    deploymentAuthorized:false,stage45Go:false,releaseGo:false
  };
  files.set('build-evidence.json',JSON.stringify(evidence,null,2)+'\n');
  return {files,evidence};
}
