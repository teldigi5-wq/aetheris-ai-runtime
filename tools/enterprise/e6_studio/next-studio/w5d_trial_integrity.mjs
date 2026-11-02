/**
 * E6 W5-D: fail-closed verification of a PREVIOUSLY completed W5-C owner-local
 * trial before browser inspection. No model is run, no network is contacted.
 * A local receipt is a self-report, NEVER cryptographic proof that model
 * weights ran or that the resulting design is original.
 */
import {createHash} from 'node:crypto';
import {readFile,readdir,lstat} from 'node:fs/promises';
import {join,relative,isAbsolute} from 'node:path';
import {verifyHeldoutCollection} from './w5b_evaluation_contract.mjs';
const fail=s=>{throw Error('E6_W5D_'+s);};
const hash=b=>createHash('sha256').update(b).digest('hex');
const hex=s=>typeof s==='string'&&/^[a-f0-9]{64}$/.test(s);
const checkKeys=(v,expected)=>{
  if(!v||typeof v!=='object'||Array.isArray(v))return false;
  return Object.keys(v).length===expected.length&&
    expected.every(k=>Object.hasOwn(v,k));
};
async function regular(file,budget){
  const s=await lstat(file);
  if(s.isSymbolicLink()||!s.isFile()||s.size<=0||s.size>budget)
    fail('UNSAFE_FILE_OR_SIZE');
  return readFile(file);
}
async function directory(path){
  const s=await lstat(path);
  if(s.isSymbolicLink()||!s.isDirectory())fail('UNTRUSTED_DIRECTORY');
}
async function exactly(path,names){
  await directory(path);
  const found=(await readdir(path)).sort();
  const want=[...names].sort();
  if(JSON.stringify(found)!==JSON.stringify(want))
    fail('UNEXPECTED_OUTPUT_ENTRY');
}
async function json(file,budget){
  const data=await regular(file,budget);
  let value;try{value=JSON.parse(data.toString('utf8'));}
  catch{fail('MALFORMED_EVIDENCE_JSON');}
  return {value,raw:data};
}
function assertFalse(obj,names){
  for(const n of names)if(obj[n]!==false)fail('UNSUPPORTED_AUTHORITY_CLAIM');
}
function htmlGuard(source){
  const text=source.toString('utf8');
  if(!text.startsWith('<!doctype html>')||
      !text.includes('default-src &#39;none&#39;')||
      !text.includes('script-src &#39;none&#39;')||
      !text.includes('noindex,nofollow')||
      !text.includes('Fictional research demonstration')||
      /<script\b|<iframe\b|<form\b|<img\b|<link\b[^>]+https?:|https?:\/\/|javascript:/i.test(text))
    fail('UNTRUSTED_RENDERED_HTML');
  return text;
}
export async function inspectTrial(trialRoot,heldoutSet){
  if(typeof trialRoot!=='string'||!trialRoot)fail('INVALID_AUDIT_ROOT');
  const briefs=verifyHeldoutCollection(heldoutSet);
  const slugs=briefs.map(x=>x.projectSlug);
  await exactly(trialRoot,[...slugs,'source-code-trial-report.json']);
  const reportEntry=await json(join(trialRoot,'source-code-trial-report.json'),150_000);
  const report=reportEntry.value;
  if(report.kind!=='E6_W5C_LOCAL_SOURCE_CODE_TRIAL_V1'||
     report.briefCount!==3||report.accepted!==3||report.rejected!==0||
     report.providerWasActualLoopbackRequest!==true||
     report.modelAuthoredRestrictedCssCandidate!==true||
     report.modelGeneratedJavaScript!==false||
     report.sourcePolicyEnforced!==true||
     !Array.isArray(report.cases)||report.cases.length!==3||
     !report.runtime||report.runtime.endpoint!=='LOOPBACK_OLLAMA'||
     typeof report.runtime.reportedVersion!=='string'||
     !/^(?:sha256:)?[0-9a-f]{64}$/i.test(report.runtime.reportedModelDigest||'')||
     report.runtime.reportedModelName!==report.cases[0].modelReportedName)
    fail('MISSING_COMPLETE_LOOPBACK_TRIAL_RECEIPT');
  assertFalse(report,['modelRuntimeAttested','modelAuthorshipIndependentlyAttested',
    'humanVisualApproved','physicalGpuMeasured','rightsIndependentlyCertified',
    'ownerApproved','stage45Go','releaseGo']);
  const evidence=[];
  for(let i=0;i<slugs.length;i++){
    const slug=slugs[i],c=report.cases[i],root=join(trialRoot,slug);
    if(c.projectSlug!==slug||c.accepted!==true||
       c.status!=='HUMAN_REVIEW_REQUIRED'||
       !hex(c.designPromptSha256)||!hex(c.cssPromptSha256)||
       !hex(c.designRawReplySha256)||!hex(c.cssRawReplySha256)||
       !hex(c.modelAuthoredCssCandidateSha256)||
       c.modelReportedName!==report.runtime.reportedModelName||
       c.modelRuntimeAttested!==false||
       c.modelAuthorshipIndependentlyAttested!==false||
       c.humanVisualAccepted!==false||c.releaseGo!==false||
       c.codeSecurityPolicyPassed!==true)fail('INVALID_CASE_RECEIPT');
    await exactly(root,['index.html','approach','assets','build-evidence.json']);
    await exactly(join(root,'approach'),['index.html']);
    await exactly(join(root,'assets'),['site.css']);
    const manifest=(await json(join(root,'build-evidence.json'),50_000)).value;
    if(manifest.kind!=='E6_W5C_BOUNDED_MODEL_CSS_SOURCE_V1'||
       manifest.projectSlug!==slug||
       manifest.proposalSha256!==c.designSha256||
       manifest.authoredCssSourceSha256!==c.modelAuthoredCssCandidateSha256||
       manifest.modelSourceLanguage!=='RESTRICTED_CSS_ONLY'||
       manifest.compiledScriptCount!==0||
       manifest.noUntrustedJavascript!==true||
       !Number.isInteger(manifest.acceptedCssRules)||
       manifest.acceptedCssRules<3||manifest.acceptedCssRules>8)
      fail('INVALID_COMPILED_SOURCE_MANIFEST');
    assertFalse(manifest,['realModelInferenceVerified','modelWeightsAttested',
      'humanAuthorshipExcluded','independentRightsReview',
      'browserQualityAccepted','ownerApproved','stage45Go','releaseGo']);
    const files=['index.html','approach/index.html','assets/site.css'];
    if(!checkKeys(manifest.fileSha256,files)||
       !checkKeys(c.outputSha256,files))fail('FILES_MANIFEST_MISMATCH');
    const fileEvidence={};
    for(const path of files){
      if(path.startsWith('/')||path.includes('..')||path.includes('\\')||
         relative(root,join(root,path)).startsWith('..')||isAbsolute(path))
        fail('UNSAFE_COMPILED_PATH');
      const bytes=await regular(join(root,path),path.endsWith('.css')?25_000:50_000);
      const digest=hash(bytes);
      if(digest!==manifest.fileSha256[path]||digest!==c.outputSha256[path])
        fail('GENERATED_FILE_HASH_MISMATCH');
      if(path.endsWith('.html'))htmlGuard(bytes);
      if(path.endsWith('.css')){
        const text=bytes.toString('utf8');
        if(!text.includes('validated model-source CSS')||
           /@import|url\s*\(|<script|expression\s*\(|javascript:/i.test(text)||
           digest!==manifest.compiledCssFileSha256)
          fail('UNSAFE_COMPILED_CSS');
      }
      fileEvidence[path]=digest;
    }
    evidence.push({projectSlug:slug,fileSha256:fileEvidence,
      acceptedCssRules:manifest.acceptedCssRules,
      compiledCssSourceSha256:manifest.authoredCssSourceSha256});
  }
  const audit={
    kind:'E6_W5D_LOCAL_CANDIDATE_INTEGRITY_AUDIT_V1',
    inputReportSha256:hash(reportEntry.raw),
    reportedModelName:report.runtime.reportedModelName,
    reportedModelDigest:report.runtime.reportedModelDigest,
    inspectedCases:evidence,
    acceptedSiteCount:evidence.length,
    allCandidateFilesHashVerified:true,
    providerSelfReportedLoopbackOnly:true,
    actualWeightsExecutionAttested:false,
    independentModelAuthorshipVerified:false,
    originalityVerified:false,licenseRightsVerified:false,
    visualAcceptanceApproved:false,ownerDeviceMeasured:false,
    wcagCertified:false,modelGeneratedJavascript:false,
    ownerApproved:false,stage45Go:false,releaseGo:false,
    decision:'REVIEWABLE_LOCAL_CANDIDATES_ONLY'
  };
  return {audit,sites:slugs.map(slug=>join(trialRoot,slug))};
}
