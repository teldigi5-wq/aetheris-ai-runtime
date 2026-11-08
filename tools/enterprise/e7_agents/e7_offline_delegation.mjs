/** E7 offline bounded delegation: real separate deterministic workers, not LLMs.
 * This review-only tool never invokes a model, accepts approval from a model,
 * runs an arbitrary command, or deploys. Child processes are NOT OS sandboxes.
 */
import {createHash, createPublicKey, verify as verifySignature} from 'node:crypto';
import {spawn} from 'node:child_process';
import fs from 'node:fs/promises';
import {constants as FS} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const WORKER=fileURLToPath(new URL('./e7_specialist_worker.mjs',import.meta.url));
const FILES=Object.freeze(['index.html','scene.js','styles.css']);
const ROLES=Object.freeze(['integrity','safety']);
const MAX_TOTAL=65000;
const MAX_STDOUT=16000;
const OPT_IN='AETHERIS_E7_OFFLINE_DELEGATION_OPT_IN';
const APPROVER_FINGERPRINT='AETHERIS_E7_TRUSTED_APPROVER_PUBLIC_KEY_SHA256';
const REQUEST_ID=/^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/;
const HASH=/^[a-f0-9]{64}$/;
const NONCE=/^[A-Za-z0-9_-]{16,80}$/;
const sha=bytes=>createHash('sha256').update(bytes).digest('hex');
const strictKeys=(o,keys)=>o!==null&&typeof o==='object'&&!Array.isArray(o)&&
  Object.keys(o).sort().join('|')===keys.slice().sort().join('|');
const fail=code=>{throw Error('E7_'+code);};

export function canonical(value){
  if(value===null||typeof value!=='object')return JSON.stringify(value);
  if(Array.isArray(value))return '['+value.map(canonical).join(',')+']';
  return '{'+Object.keys(value).sort().map(k=>JSON.stringify(k)+':'+canonical(value[k])).join(',')+'}';
}
export function plan(){
  return {status:'PLAN_ONLY_NO_FILES_OR_AGENTS_ACCESSED',
    agents:ROLES.map((role)=>({role,tools:role==='integrity'?['sha256']:['bounded-static-pattern-review']})),
    maxChildProcesses:2,maxFileBytes:MAX_TOTAL,maxWorkerMs:8000,
    noArbitraryShell:true,noNetworkTools:true,modelCalls:0,
    ownerApproved:false,stage45Go:false,productionReady:false};
}

async function loadWorkspace(folder){
  if(typeof folder!=='string'||!path.isAbsolute(folder))fail('ABSOLUTE_WORKSPACE_REQUIRED');
  const root=await fs.lstat(folder);
  if(!root.isDirectory()||root.isSymbolicLink())fail('UNSAFE_WORKSPACE_DIRECTORY');
  // Resolve the trusted workspace once. Windows runner directories can have
  // junction/canonical spelling differences; compare each child against this
  // resolved root, not the caller's original path spelling.
  const canonicalRoot=await fs.realpath(folder);
  const list=(await fs.readdir(folder)).sort();
  if(list.join('|')!==FILES.slice().sort().join('|'))fail('NON_ALLOWLISTED_INPUT_FILE');
  const input={}, digests=[];let total=0;
  for(const name of FILES){
    const target=path.join(folder,name);
    // Node's O_NOFOLLOW is ineffective on Windows. Explicitly reject reparse
    // targets on BOTH platforms before opening, then check file identity
    // again after reading. This is a local-review defense, not proof against
    // a malicious same-account actor racing arbitrary Windows reparse points.
    const prior=await fs.lstat(target);
    if(!prior.isFile()||prior.isSymbolicLink()||prior.nlink!==1)
      fail('UNSAFE_INPUT_FILE');
    const real=await fs.realpath(target);
    const norm=s=>process.platform==='win32'
      ?path.resolve(s).toLowerCase():path.resolve(s);
    if(norm(real)!==norm(path.join(canonicalRoot,name)))fail('UNSAFE_INPUT_FILE');
    const f=await fs.open(target,FS.O_RDONLY|(FS.O_NOFOLLOW||0));
    try{
      const meta=await f.stat();
      if(!meta.isFile()||meta.nlink!==1||meta.size<1||meta.size>MAX_TOTAL||
         meta.dev!==prior.dev||meta.ino!==prior.ino)
        fail('UNSAFE_INPUT_FILE');
      const data=await f.readFile();
      const after=await fs.lstat(target);
      if(data.length!==meta.size||!after.isFile()||after.isSymbolicLink()||
         after.dev!==prior.dev||after.ino!==prior.ino||
         after.size!==meta.size)fail('INPUT_RACE_DENIED');
      total+=data.length;
      if(total>MAX_TOTAL)fail('INPUT_BUDGET_EXCEEDED');
      // Disallow malformed replacement sequences, not instructions in text.
      const decoded=new TextDecoder('utf-8',{fatal:true}).decode(data);
      if(decoded.includes('\0'))fail('BINARY_CONTENT_DENIED');
      input[name]=data.toString('base64');
      digests.push({path:name,bytes:data.length,sha256:sha(data)});
    }finally{await f.close();}
  }
  return {input,digests,total,inputSha256:sha(canonical(digests))};
}

function runWorker(role, input){
  if(!ROLES.includes(role))fail('UNKNOWN_SPECIALIST');
  return new Promise((resolve,reject)=>{
    const child=spawn(process.execPath,[WORKER,role],{
      shell:false,windowsHide:true,stdio:['pipe','pipe','pipe'],
      env:{NODE_OPTIONS:''},timeout:8000,killSignal:'SIGKILL'
    });
    let stdout='',stderr='',tooLarge=false,finished=false;
    child.stdout.on('data',chunk=>{
      stdout+=chunk.toString('utf8');
      if(Buffer.byteLength(stdout)>MAX_STDOUT){tooLarge=true;child.kill('SIGKILL');}
    });
    child.stderr.on('data',chunk=>{
      stderr+=chunk.toString('utf8');
      if(stderr.length>2000){tooLarge=true;child.kill('SIGKILL');}
    });
    child.on('error',err=>{if(!finished){finished=true;reject(Error('E7_WORKER_UNAVAILABLE'));}});
    child.on('close',(code,signal)=>{
      if(finished)return;
      finished=true;
      if(code!==0||signal||tooLarge) return reject(Error('E7_WORKER_FAILED_OR_TIMED_OUT'));
      try{
        const response=JSON.parse(stdout.trim());
        if(!strictKeys(response,['schema','role','result'])||
           response.schema!==1||response.role!==role)
          fail('WORKER_RESULT_SCHEMA');
        resolve(response.result);
      }catch{reject(Error('E7_WORKER_RESULT_UNTRUSTED'));}
    });
    child.stdin.on('error',()=>{});
    child.stdin.end(JSON.stringify({version:1,role,files:input}));
  });
}

export function verifyTrace(trace){
  if(!Array.isArray(trace)||trace.length!==4)return false;
  let last='0'.repeat(64);
  for(let i=0;i<trace.length;i++){
    const entry=trace[i];
    if(!strictKeys(entry,['sequence','type','previousHash','payload','sha256'])||
       entry.sequence!==i+1||entry.previousHash!==last||!HASH.test(entry.sha256))
      return false;
    const unsigned={sequence:entry.sequence,type:entry.type,previousHash:last,payload:entry.payload};
    if(sha(canonical(unsigned))!==entry.sha256)return false;
    last=entry.sha256;
  }
  return true;
}
function chain(events){
  let last='0'.repeat(64);
  return events.map(([type,payload],i)=>{
    const unsigned={sequence:i+1,type,previousHash:last,payload};
    const entry={...unsigned,sha256:sha(canonical(unsigned))};
    last=entry.sha256;
    return entry;
  });
}

export async function review({workspace,requestId,operatorOptIn=process.env[OPT_IN]}={}){
  if(operatorOptIn!=='yes')fail('EXPLICIT_OFFLINE_OPERATOR_OPT_IN_REQUIRED');
  if(typeof requestId!=='string'||!REQUEST_ID.test(requestId))fail('REQUEST_ID_REQUIRED');
  const inspected=await loadWorkspace(workspace);
  const [integrity,safety]=[
    await runWorker('integrity',inspected.input),
    await runWorker('safety',inspected.input)
  ];
  if(!strictKeys(integrity,['files','totalBytes'])||
     canonical(integrity.files)!==canonical(inspected.digests)||
     integrity.totalBytes!==inspected.total)
    fail('SPECIALIST_INTEGRITY_DISAGREEMENT');
  if(!strictKeys(safety,['checks','staticHeuristicOnly'])||
     safety.staticHeuristicOnly!==true||
     !strictKeys(safety.checks,[
       'mainLandmark','skipLink','reducedMotion','fixedLocalJs','fixedLocalCss',
       'noInlineEvent','noUnreviewedResource','noRemoteCss','noRemoteJs'
     ])||Object.values(safety.checks).some(x=>typeof x!=='boolean'))
    fail('SPECIALIST_POLICY_RESULT_INVALID');
  const accepted=Object.values(safety.checks).every(v=>v===true);
  const events=[
    ['PLAN',{requestId,roles:ROLES,scopedTools:['sha256','bounded-static-pattern-review'],maxAttempts:2}],
    ['INTEGRITY_AGENT',{identity:'local-integrity-process',evidence:integrity}],
    ['SAFETY_AGENT',{identity:'local-safety-process',evidence:safety}],
    ['REVIEWER',{candidate:accepted,policy:'E7_STATIC_HEURISTIC_REVIEW_V1',
      independentlyAudited:false,ownerApproved:false,stage45Go:false}]
  ];
  const trace=chain(events);
  return {schema:1,status:accepted?'REVIEW_CANDIDATE_NOT_APPROVED':'STATIC_REVIEW_DENIED',
    requestId,inputSha256:inspected.inputSha256,
    workerProcessCount:2,workerExecutionAttempts:2,workerRetries:0,
    evidenceOnly:true,staticHeuristicOnly:true,modelCalls:0,
    ownerAuthenticated:false,ownerApproved:false,productionReady:false,stage45Go:false,
    rootSha256:trace[trace.length-1].sha256,trace};
}

export function validateApproval({receipt,pem,review:decision,exportPath,now=Math.floor(Date.now()/1000),
  pinnedKeySha256=process.env[APPROVER_FINGERPRINT]}={}){
  if(!decision||!verifyTrace(decision.trace)||
      decision.rootSha256!==decision.trace.at(-1).sha256||
      !HASH.test(decision.inputSha256))fail('REVIEW_TRACE_TAMPERED');
  if(typeof pem!=='string'||typeof pinnedKeySha256!=='string'||
      !HASH.test(pinnedKeySha256)||sha(Buffer.from(pem))!==pinnedKeySha256)
    fail('PINNED_APPROVER_KEY_REQUIRED');
  if(typeof exportPath!=='string'||!path.isAbsolute(exportPath))fail('EXPORT_PATH_INVALID');
  const names=['schema','action','requestId','inputSha256','rootSha256',
    'outputPath','issuedAtUnix','expiresAtUnix','nonce','signature'];
  if(!strictKeys(receipt,names)||receipt.schema!==1||
     receipt.action!=='EXPORT_E7_REVIEW_TRACE_ONLY'||
     receipt.requestId!==decision.requestId||
     receipt.inputSha256!==decision.inputSha256||
     receipt.rootSha256!==decision.rootSha256||
     receipt.outputPath!==exportPath||
     typeof receipt.nonce!=='string'||!NONCE.test(receipt.nonce)||
     !Number.isSafeInteger(receipt.issuedAtUnix)||
     !Number.isSafeInteger(receipt.expiresAtUnix)||
     !Number.isSafeInteger(now)||
     receipt.issuedAtUnix>now||receipt.issuedAtUnix<now-600||
     receipt.expiresAtUnix<=now||receipt.expiresAtUnix>now+600||
     receipt.expiresAtUnix<=receipt.issuedAtUnix||
     typeof receipt.signature!=='string'||
     !/^[A-Za-z0-9+/]{86}==$/.test(receipt.signature))
    fail('EXPLICIT_SIGNED_EXPORT_RECEIPT_REQUIRED');
  let key;
  try{key=createPublicKey(pem);}
  catch{fail('APPROVER_PUBLIC_KEY_INVALID');}
  if(key.asymmetricKeyType!=='ed25519')fail('ED25519_ONLY');
  const unsigned={...receipt};delete unsigned.signature;
  const sig=Buffer.from(receipt.signature,'base64');
  if(sig.length!==64||!verifySignature(null,Buffer.from(canonical(unsigned)),key,sig))
    fail('EXPORT_SIGNATURE_DENIED');
  return {signatureValid:true,verifiedLocalKeyFingerprint:pinnedKeySha256,
    authenticatedOwner:false,releaseApproval:false};
}

export async function exportReview({decision,outputPath,receipt,pem,
  pinnedKeySha256=process.env[APPROVER_FINGERPRINT],now}={}){
  validateApproval({receipt,pem,review:decision,exportPath:outputPath,now,pinnedKeySha256});
  const folder=path.dirname(outputPath);
  const meta=await fs.lstat(folder);
  if(!meta.isDirectory()||meta.isSymbolicLink())fail('UNSAFE_EXPORT_DIRECTORY');
  const evidence={...decision,localExportSignatureVerified:true,
    ownerAuthenticated:false,ownerApproved:false,stage45Go:false};
  // Write no code, model prompt, customer data or arbitrary repository path.
  const handle=await fs.open(outputPath,FS.O_CREAT|FS.O_EXCL|FS.O_WRONLY|(FS.O_NOFOLLOW||0),0o600);
  try{
    await handle.writeFile(canonical(evidence)+'\n','utf8');
    await handle.sync();
  }finally{await handle.close();}
  return {status:'E7_LOCAL_REVIEW_TRACE_EXPORTED_NOT_AUTHORIZED',
    rootSha256:decision.rootSha256,ownerApproved:false,stage45Go:false};
}
