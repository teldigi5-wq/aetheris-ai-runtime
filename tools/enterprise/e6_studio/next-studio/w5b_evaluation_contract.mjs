/** W5-B deterministic evaluator: measured schema and diversity signals only.
 * No heuristic can certify true originality, truthfulness, design quality or
 * independent model authorship. All outputs remain HUMAN_REVIEW_REQUIRED.
 */
import {createHash} from 'node:crypto';
import {compileModelProposedSite,validateOwnerBrief,validateModelProposal} from './w5a_model_design_compiler.mjs';
const sha=x=>createHash('sha256').update(x,'utf8').digest('hex');
const fail=s=>{throw Error('E6_W5B_'+s);};
const tokens=s=>new Set(s.toLowerCase().match(/[a-z]{4,}/g)||[]);
function jaccard(a,b){
  const aa=tokens(a),bb=tokens(b),union=new Set([...aa,...bb]);
  return union.size?[...aa].filter(x=>bb.has(x)).length/union.size:0;
}
export function verifyHeldoutCollection(batch){
  if(!batch||batch.kind!=='E6_W5B_HELDOUT_FICTIONAL_BRIEFS_V1'||
     batch.author!=='HUMAN_DEFINED_EVAL_ONLY'||!Array.isArray(batch.briefs)||
     batch.briefs.length!==3)fail('INVALID_HELDOUT_SET');
  const slugs=new Set();
  for(const b of batch.briefs){
    validateOwnerBrief(b);
    if(slugs.has(b.projectSlug))fail('DUPLICATE_BRIEF');
    slugs.add(b.projectSlug);
  }
  return batch.briefs;
}
export function evaluateProposal(brief,proposal,css,prior=[]){
  validateOwnerBrief(brief);
  validateModelProposal(brief,proposal);
  const compiled=compileModelProposedSite(brief,proposal,css);
  const text=proposal.pages.flatMap(p=>[p.heading,p.lede,
    ...p.sections.flatMap(s=>[s.eyebrow,s.title,s.body])]).join(' ');
  const comparisons=prior.map(p=>({
    againstProjectSlug:p.projectSlug,
    titleOverlap:jaccard(text,p.text),
    sameArtTokens:JSON.stringify(proposal.art)===JSON.stringify(p.art)
  }));
  const warnings=[];
  if(comparisons.some(c=>c.titleOverlap>.70))warnings.push('HIGH_LEXICAL_SIMILARITY_NEEDS_HUMAN_REVIEW');
  if(comparisons.some(c=>c.sameArtTokens))warnings.push('IDENTICAL_ART_DIRECTION_NEEDS_HUMAN_REVIEW');
  const claimSignals=[
    /\b(?:award-winning|certified|patented|accredited|featured in)\b/i,
    /\b\d+\s+(?:customers|clients|deployments|installations|awards)\b/i,
    /\b(?:guaranteed results|proven improvement|industry-leading)\b/i
  ];
  if(claimSignals.some(re=>re.test(text)))warnings.push('UNVERIFIED_OUTCOME_CLAIM_NEEDS_HUMAN_REVIEW');
  const sections=proposal.pages.reduce((count,p)=>count+p.sections.length,0);
  const evidence={
    kind:'E6_W5B_HELDOUT_CANDIDATE_EVAL_V1',
    projectSlug:brief.projectSlug,
    candidateSha256:sha(JSON.stringify(proposal)),
    generatedHtmlSha256:compiled.evidence.fileSha256,
    sectionCount:sections,routeCount:compiled.evidence.generatedStaticPages,
    comparisons,warnings,
    schemaAndCompilerPassed:true,
    modelCodeGenerated:false,
    lexicalNoveltyCertified:false,
    contentFactualityVerified:false,
    independentModelAuthorshipVerified:false,
    humanVisualApproved:false,ownerApproved:false,stage45Go:false,releaseGo:false,
    status:warnings.length?'REQUIRES_HUMAN_REVIEW_WITH_WARNINGS':'REQUIRES_HUMAN_REVIEW'
  };
  return {built:compiled,evidence,comparison:{projectSlug:brief.projectSlug,text,art:proposal.art}};
}
export function summarizeBatch(cases){
  if(!Array.isArray(cases)||cases.length!==3)fail('INCOMPLETE_HELDOUT_EVAL');
  const slugs=new Set(),shaSet=new Set();
  for(const c of cases){
    if(!c||!c.projectSlug||slugs.has(c.projectSlug))fail('DUPLICATE_OR_INVALID_CASE');
    slugs.add(c.projectSlug);
    if(c.accepted){
      if(!/^[a-f0-9]{64}$/.test(c.candidateSha256)||shaSet.has(c.candidateSha256))
        fail('DUPLICATE_OR_INVALID_OUTPUT');
      shaSet.add(c.candidateSha256);
    }else if(typeof c.rejectionCode!=='string'||!/^E6_W5[AB]_[A-Z0-9_]+$/.test(c.rejectionCode))
      fail('UNCONTROLLED_REJECTION_EVIDENCE');
  }
  const accepted=cases.filter(x=>x.accepted).length;
  return {kind:'E6_W5B_HELDOUT_SUMMARY_V1',cases:3,accepted,rejected:3-accepted,
    allAccepted:accepted===3,
    verifiedRealModelRun:false,independentAuthorshipVerified:false,
    humanVisualApproved:false,modelCodeGenerated:false,
    ownerApproved:false,releaseGo:false,stage45Go:false,
    result:'BLOCKED_PENDING_REAL_MODEL_AND_HUMAN_REVIEW'};
}
