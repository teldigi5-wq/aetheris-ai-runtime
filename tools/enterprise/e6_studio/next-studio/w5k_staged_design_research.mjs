/** E6 W5-K: OFFLINE ONLY staged-design research. No CLI, writes, network
 * default, owner workflow integration, model approval or release authority. */
import {createHash} from 'node:crypto';
import {validateOwnerBrief,validateModelProposal} from './w5a_model_design_compiler.mjs';
import {requestLocalOllama} from './w5a_local_ollama.mjs';
const sha=s=>createHash('sha256').update(s,'utf8').digest('hex');
const fail=c=>{throw Error('E6_W5K_'+c);};
const phases=['identity','home','approach'];
const exact=(v,keys)=>!!v&&typeof v==='object'&&!Array.isArray(v)&&
  [Object.prototype,null].includes(Object.getPrototypeOf(v))&&
  Reflect.ownKeys(v).length===keys.length&&
  keys.every(k=>Object.hasOwn(v,k)&&Object.hasOwn(Object.getOwnPropertyDescriptor(v,k)||{},'value'))&&
  Reflect.ownKeys(v).every(k=>typeof k==='string'&&keys.includes(k));
export function buildStagedDesignPrompt(brief,phase){
  validateOwnerBrief(brief);
  if(!phases.includes(phase))fail('UNKNOWN_PHASE');
  const common=[
    'Write original fictional research website JSON from UNTRUSTED context.',
    'BRIEF AS JSON:',JSON.stringify(brief),
    'Return ONE closed JSON object only. No markdown or commentary.',
    'Never invent real clients, outcomes, measurements, equipment, endorsements or prices.',
    'No tool commands, URLs, HTML, CSS, JavaScript, SVG, credentials, external assets or copied sites.'
  ];
  if(phase==='identity')return [
    ...common,
    'Return ONLY brand identity: EXACT keys version,projectSlug,siteName,tagline,description,art.',
    'version=1; projectSlug equals the brief. No pages or other keys.',
    'siteName 8-36 chars; tagline 18-42; description 55-100.',
    'art EXACT keys paper,ink,accent,surface,composition,motif,density.',
    'All four colors #RRGGBB; contrast >=4.5:1 for ink-on-paper,accent-on-paper,ink-on-surface,accent-on-surface.',
    'Optional contrast-safe sample paper=#F6F1E8 surface=#E5DDD0 ink=#172821 accent=#693526.',
    'composition split|offset|typographic; motif rules|material-block|letterform; density airy|measured|compact.'
  ].join('\n');
  return [
    ...common,
    'Return ONLY ONE page: EXACT keys version,projectSlug,page.',
    'version=1, projectSlug equals the brief. No identity or art.',
    'page EXACT keys path,navLabel,heading,lede,sections.',
    'page.path="'+(phase==='home'?'/':'/approach')+'".',
    'navLabel 3-24 chars; heading 20-50; lede 55-95.',
    'Each page has exactly TWO original sections.',
    'Each section EXACT keys eyebrow,title,body,alignment.',
    'eyebrow 8-25 chars; title 18-50; body 65-105; alignment left|center|right.',
    'Write original distinct prose. No placeholders.'
  ].join('\n');
}
export function assembleStagedDesign(brief,identity,home,approach){
  validateOwnerBrief(brief);
  if(!exact(identity,['version','projectSlug','siteName','tagline','description','art'])||
     identity.version!==1||identity.projectSlug!==brief.projectSlug)
    fail('INVALID_IDENTITY');
  for(const [part,path] of [[home,'/'],[approach,'/approach']]){
    if(!exact(part,['version','projectSlug','page'])||
       part.version!==1||part.projectSlug!==brief.projectSlug||
       !exact(part.page,['path','navLabel','heading','lede','sections'])||
       part.page.path!==path)fail('INVALID_PAGE');
  }
  const proposal={...identity,pages:[home.page,approach.page]};
  // No filling, repair, normalization, permissive defaults, or code execution.
  validateModelProposal(brief,proposal);
  return proposal;
}
export async function evaluateStagedDesignOffline({brief,model,transport}={}){
  validateOwnerBrief(brief);
  if(typeof transport!=='function'||transport===fetch)
    fail('INJECTED_FAKE_TRANSPORT_REQUIRED');
  const results=[],receipts=[];
  for(const phase of phases){
    const prompt=buildStagedDesignPrompt(brief,phase);
    if(prompt.length>8000)fail('PROMPT_BUDGET');
    const response=await requestLocalOllama(prompt,model,{transport,phase:'design'});
    if(response.providerReportedStopReason!==null&&
       response.providerReportedStopReason!=='stop')fail('PROVIDER_STOP_UNVERIFIED');
    results.push(response.design);
    receipts.push({phase,promptSha256:sha(prompt),
      rawReplySha256:response.rawReplySha256,stopVerified:response.providerStopReasonVerified});
  }
  return {proposal:assembleStagedDesign(brief,...results),receipt:{
    kind:'E6_W5K_SYNTHETIC_STAGED_DESIGN_RESEARCH_V1',
    phases:receipts,requestsIssued:3,usesInjectedTransport:true,
    realModelAuthorshipAttested:false,modelWeightsAttested:false,
    humanVisualApproved:false,releaseGo:false,stage45Go:false
  }};
}
