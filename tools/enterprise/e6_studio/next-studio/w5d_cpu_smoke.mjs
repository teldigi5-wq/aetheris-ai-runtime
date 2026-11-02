/**
 * W5-D opt-in TEMPORARY GITHUB CLOUD CPU inference smoke.
 * Not a 3-brief acceptance, GPU benchmark, independent weight attestation,
 * original site or production authorization. Never accepts remote model URLs.
 * This module is safe to contract-test with a fake transport without model IO.
 */
import {createHash} from 'node:crypto';
import {readFile,writeFile,mkdir,lstat} from 'node:fs/promises';
import {join,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
const here=dirname(fileURLToPath(import.meta.url));
const endpoint='http://127.0.0.1:11434';
const model='qwen2.5:0.5b';
const sha=b=>createHash('sha256').update(b).digest('hex');
const fail=s=>{throw Error('E6_W5D_CPU_'+s)};
async function boundedResponse(url,init,transport=fetch){
  if(!url.startsWith(endpoint+'/'))fail('EXTERNAL_HOST_DENIED');
  const response=await transport(url,{...init,redirect:'error'});
  if(response?.status!==200||!response.body)fail('LOCAL_OLLAMA_UNAVAILABLE');
  const parts=[];let count=0;
  for await(const chunk of response.body){
    const bytes=Buffer.from(chunk);count+=bytes.length;
    if(count>30_000)fail('PROVIDER_RESPONSE_TOO_LARGE');
    parts.push(bytes);
  }
  try{return JSON.parse(Buffer.concat(parts).toString('utf8'));}
  catch{fail('PROVIDER_JSON_INVALID');}
}
export async function runCpuSmoke({transport=fetch}={}){
  const version=await boundedResponse(endpoint+'/api/version',{
    method:'GET',signal:AbortSignal.timeout(10_000)
  },transport);
  const installed=await boundedResponse(endpoint+'/api/tags',{
    method:'GET',signal:AbortSignal.timeout(10_000)
  },transport);
  if(!/^[0-9A-Za-z.+-]{1,48}$/.test(version.version||'')||
     !Array.isArray(installed.models)||installed.models.length>100)
    fail('INVALID_LOCAL_RUNTIME_METADATA');
  const match=installed.models.find(m=>(m.name===model||m.model===model));
  if(!match||!/^(?:sha256:)?[a-f0-9]{64}$/i.test(match.digest||''))
    fail('PINNED_MODEL_NOT_INSTALLED');
  const prompt='Respond with a minimal valid JSON object with a single key named research_status and a string value describing a fictional static website study. No URLs or code.';
  const payload={model,prompt,stream:false,format:'json',keep_alive:'0',
    options:{temperature:0,num_gpu:0,num_predict:96,num_ctx:2048}};
  const actual=await boundedResponse(endpoint+'/api/generate',{
    method:'POST',headers:{'content-type':'application/json'},
    body:JSON.stringify(payload),signal:AbortSignal.timeout(180_000)
  },transport);
  if(actual.done!==true||actual.model!==model||
     typeof actual.response!=='string'||actual.response.length<3||
     actual.response.length>5_000||!Number.isFinite(actual.total_duration)||
     actual.total_duration<=0||!Number.isFinite(actual.eval_count)||
     actual.eval_count<=0)fail('NO_COMPLETE_REAL_PROVIDER_GENERATION');
  let parsed;
  try{parsed=JSON.parse(actual.response);}catch{fail('MODEL_OUTPUT_NOT_VALID_JSON');}
  if(!parsed||typeof parsed!=='object'||Array.isArray(parsed))
    fail('MODEL_OUTPUT_NOT_JSON_OBJECT');
  return {kind:'E6_W5D_EPHEMERAL_CPU_INFERENCE_SMOKE_V1',
    modelReportedName:model,ollamaReportedVersion:version.version,
    ollamaReportedModelDigest:match.digest,
    promptSha256:sha(Buffer.from(prompt)),
    responseSha256:sha(Buffer.from(actual.response)),
    outputWasParsableJsonObject:true,providerReportedGenerationComplete:true,
    providerReportedEvalTokens:actual.eval_count,
    providerReportedDurationNanos:actual.total_duration,
    realLoopbackApiCalled:transport===fetch,
    gpuUseRequested:false,cpuOnlyModeConfiguredByWorkflow:true,
    actualWeightsExecutionCryptographicallyAttested:false,
    threeHeldoutSitesGenerated:false,premiumArtAccepted:false,
    realDeviceGpuBenchmarked:false,fieldWebVitalsMeasured:false,
    independentModelAuthorshipVerified:false,modelGeneratedExecutableJavascript:false,
    independentRightsAudited:false,ownerApproved:false,stage45Go:false,releaseGo:false};
}
async function main(){
  if(process.argv.length!==2||process.env.E6_W5D_CPU_RUN_AUTHORIZED!=='PR_LABEL_OWNER_APPROVED')
    fail('OWNER_LABEL_OPT_IN_REQUIRED');
  if(process.env.GITHUB_EVENT_NAME!=='pull_request'||process.env.GITHUB_REPOSITORY!=='teldigi5-wq/aetheris-ai-runtime')
    fail('NOT_APPROVED_EPHEMERAL_GITHUB_CONTEXT');
  const result=await runCpuSmoke();
  const parent=join(here,'target');
  try{const st=await lstat(parent);if(st.isSymbolicLink()||!st.isDirectory())fail('UNSAFE_OUTPUT_PARENT');}
  catch(e){if(e.code!=='ENOENT')throw e;await mkdir(parent);}
  const dest=join(parent,'e6-w5d-cpu-inference-smoke');
  await mkdir(dest,{recursive:false,mode:0o700});
  await writeFile(join(dest,'research-receipt.json'),JSON.stringify(result,null,2)+'\n',{flag:'wx',mode:0o600});
  console.log('E6_W5D_CPU_ONLY_CLOUD_MODEL_SMOKE '+JSON.stringify({
    generated:result.providerReportedGenerationComplete,
    model:result.modelReportedName,
    actualWeightsAttested:false,hardwareGpuVerified:false,
    threeHeldoutSitesGenerated:false,releaseGo:false
  }));
}
if(process.argv[1]&&fileURLToPath(import.meta.url)===process.argv[1]){
  main().catch(e=>{
    console.error('E6_W5D_CPU_SMOKE_BLOCKED',/^E6_W5D_CPU_[A-Z_]+$/.test(e?.message)?e.message:'E6_W5D_CPU_UNVERIFIED_FAILURE');
    process.exitCode=1;
  });
}
