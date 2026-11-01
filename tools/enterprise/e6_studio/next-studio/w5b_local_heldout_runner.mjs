/** E6 W5-B: strictly local *real model* held-out candidate runner.
 * CI uses fake transports and NEVER claims model inference.
 * This owner-only opt-in talks to the fixed Ollama loopback service.
 * No arbitrary paths, executables, paid API, download, deployment or repairs.
 */
import {readFile,mkdir,lstat,writeFile} from 'node:fs/promises';
import {dirname,join,relative,isAbsolute} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import {buildPrompt,requestLocalOllama} from './w5a_local_ollama.mjs';
import {verifyHeldoutCollection,evaluateProposal,summarizeBatch} from './w5b_evaluation_contract.mjs';
const here=dirname(fileURLToPath(import.meta.url));
const sha=x=>createHash('sha256').update(x,'utf8').digest('hex');
const fail=s=>{throw Error('E6_W5B_'+s);};
const PATTERN=/^(?=.{1,80}$)[a-z0-9][a-z0-9._-]*(?:\/[a-z0-9][a-z0-9._-]*)?(?::[a-z0-9][a-z0-9._-]*)?$/;
const ORIGIN='http://127.0.0.1:11434';
async function smallJSON(transport,path,method='GET',body){
  const response=await transport(ORIGIN+path,{
    method,headers:{'Content-Type':'application/json'},
    ...(body?{body:JSON.stringify(body)}:{}),
    redirect:'error',signal:AbortSignal.timeout(15_000)
  });
  if(!response||response.status!==200||!response.body)fail('LOCAL_METADATA_UNAVAILABLE');
  const chunks=[];let total=0;
  for await(const part of response.body){
    const bytes=Buffer.from(part);total+=bytes.length;
    if(total>24_000)fail('METADATA_RESPONSE_TOO_LARGE');
    chunks.push(bytes);
  }
  try{return JSON.parse(Buffer.concat(chunks).toString('utf8'));}
  catch{fail('INVALID_LOCAL_METADATA_JSON');}
}
export async function probeLocalModel(model,{transport=fetch}={}){
  if(typeof model!=='string'||!PATTERN.test(model))fail('INVALID_MODEL_IDENTIFIER');
  const [v,tags]=await Promise.all([
    smallJSON(transport,'/api/version'),
    smallJSON(transport,'/api/tags')
  ]);
  if(!v||typeof v.version!=='string'||v.version.length>48||
     !/^[0-9a-zA-Z.+-]+$/.test(v.version)||
     !Array.isArray(tags?.models)||tags.models.length>100)
    fail('UNVERIFIED_LOCAL_SERVICE_INFO');
  const entry=tags.models.find(x=>x&&typeof x==='object'&&
    (x.name===model||x.model===model));
  if(!entry||typeof entry.digest!=='string'||
    !/^(?:sha256:)?[0-9a-f]{64}$/i.test(entry.digest))
    fail('MODEL_NOT_INSTALLED_OR_DIGEST_MISSING');
  return {endpoint:'LOOPBACK_OLLAMA',reportedVersion:v.version,
    reportedModelName:model,reportedModelDigest:entry.digest,
    modelDigestCryptographicallyAttested:false};
}
const rejectionCode=e=>{
  const msg=String(e?.message||'');
  return /^E6_W5[AB]_[A-Z0-9_]+$/.test(msg)?msg:'E6_W5B_LOCAL_REQUEST_OR_VALIDATION_REJECTED';
};
export async function evaluateHeldout({batch,model,baseCss,transport=fetch}){
  if(!PATTERN.test(model||'')||typeof baseCss!=='string')
    fail('INVALID_EVAL_REQUEST');
  const briefs=verifyHeldoutCollection(batch);
  const runtime=await probeLocalModel(model,{transport});
  const prior=[],cases=[],acceptedSites=new Map();
  for(const brief of briefs){
    const prompt=buildPrompt(brief);
    const base={projectSlug:brief.projectSlug,briefSha256:sha(JSON.stringify(brief)),
      promptSha256:sha(prompt),modelReportedName:model};
    try{
      const result=await requestLocalOllama(prompt,model,{transport});
      const evaluated=evaluateProposal(brief,result.design,baseCss,prior);
      prior.push(evaluated.comparison);
      const value={...base,accepted:true,rejectionCode:null,
        candidateSha256:evaluated.evidence.candidateSha256,
        modelRawReplySha256:result.rawReplySha256,
        warnings:evaluated.evidence.warnings,
        status:evaluated.evidence.status,
        generatedRoutes:2,
        independentModelAuthorshipVerified:false,
        humanVisualApproved:false,releaseGo:false};
      cases.push(value);
      acceptedSites.set(brief.projectSlug,{built:evaluated.built,evidence:evaluated.evidence});
    }catch(error){
      cases.push({...base,accepted:false,rejectionCode:rejectionCode(error),
        candidateSha256:null,modelRawReplySha256:null,warnings:[],
        status:'REJECTED',generatedRoutes:0,
        independentModelAuthorshipVerified:false,
        humanVisualApproved:false,releaseGo:false});
    }
  }
  const summary=summarizeBatch(cases);
  const report={kind:'E6_W5B_LOCAL_HELDOUT_RESEARCH_V1',
    runtime,model,briefCount:3,
    directLoopbackTransportInvoked:transport===fetch,
    actualWeightsExecutionAttested:false,
    independentModelAuthorshipVerified:false,
    schemaOnlyNotModelCode:true,
    humanVisualApproved:false,
    ownerApproved:false,stage45Go:false,releaseGo:false,
    summary,cases};
  return {report,acceptedSites};
}
async function writeResults(result){
  const parent=join(here,'target');
  try{
    const st=await lstat(parent);
    if(st.isSymbolicLink()||!st.isDirectory())fail('UNTRUSTED_OUTPUT_PARENT');
  }catch(e){if(e.code!=='ENOENT')throw e;await mkdir(parent,{recursive:false});}
  const dest=join(parent,'e6-w5b-real-heldout-evaluation');
  // Always write once. Never automatically clear or overwrite prior research.
  await mkdir(dest,{recursive:false});
  for(const [slug,{built,evidence}] of result.acceptedSites){
    if(!/^[a-z][a-z0-9-]{0,47}$/.test(slug))fail('UNSAFE_PROJECT_SLUG');
    const site=join(dest,slug);
    await mkdir(site);
    for(const [path,body] of built.files){
      if(path.includes('..')||path.includes('\\')||path.startsWith('/'))fail('UNSAFE_RELATIVE_OUTPUT');
      const target=join(site,path),rel=relative(site,target);
      if(!rel||rel.startsWith('..')||isAbsolute(rel))fail('OUTPUT_ESCAPE');
      await mkdir(dirname(target),{recursive:true});
      await writeFile(target,body,{flag:'wx',mode:0o644});
    }
    await writeFile(join(site,'w5b-candidate-evaluation.json'),
      JSON.stringify(evidence,null,2)+'\n',{flag:'wx',mode:0o600});
  }
  await writeFile(join(dest,'summary.json'),
    JSON.stringify(result.report,null,2)+'\n',{flag:'wx',mode:0o600});
}
async function main(){
  if(process.argv.length!==2)fail('NO_ARGS_OR_ARBITRARY_PATHS');
  if(process.env.E6_W5B_ALLOW_LOCAL_INFERENCE!=='YES')
    fail('EXPLICIT_USER_LOCAL_RUN_REQUIRED');
  const model=process.env.E6_W5B_MODEL;
  if(!PATTERN.test(model||''))fail('MODEL_NAME_REQUIRED');
  const [batch,baseCss]=await Promise.all([
    readFile(join(here,'w5b_heldout_briefs.json'),'utf8').then(JSON.parse),
    readFile(join(here,'w5a_base.css'),'utf8')
  ]);
  const result=await evaluateHeldout({batch,model,baseCss});
  await writeResults(result);
  console.log('E6_W5B_LOCAL_MODEL_EVAL_REVIEW_ONLY '+JSON.stringify({
    source:'DIRECT_LOOPBACK_PROVIDER_RESPONSE_NOT_ATTESTED_WEIGHTS',
    accepted:result.report.summary.accepted,rejected:result.report.summary.rejected,
    output:'target/e6-w5b-real-heldout-evaluation',
    realModelCodeGenerated:false,ownerApproved:false,releaseGo:false
  }));
  if(result.report.summary.rejected>0)process.exitCode=1;
}
if(process.argv[1]&&fileURLToPath(import.meta.url)===process.argv[1])
  main().catch(e=>{console.error('E6_W5B_LOCAL_EVAL_BLOCKED',rejectionCode(e));process.exitCode=1;});
