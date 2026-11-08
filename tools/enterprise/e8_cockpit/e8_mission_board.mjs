/** E8 offline Mission Review: compile E7 typed review evidence into local pages.
 * Read-only artifact, not authenticated Mission Control or permissioned tenant API.
 */
import {createHash} from 'node:crypto';
import fs from 'node:fs/promises';
import {constants as FS} from 'node:fs';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {parseArgs} from 'node:util';
import {canonical,verifyTrace} from '../e7_agents/e7_offline_delegation.mjs';

const SHA=/^[a-f0-9]{64}$/;
const UUID=/^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/;
const FILE=/^review-([a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12})\.json$/;
const CHECKS=Object.freeze(['mainLandmark','skipLink','reducedMotion',
  'fixedLocalJs','fixedLocalCss','noInlineEvent',
  'noUnreviewedResource','noRemoteCss','noRemoteJs']);
const EVENT_TYPES=Object.freeze(['PLAN','INTEGRITY_AGENT','SAFETY_AGENT','REVIEWER']);
const FILES=Object.freeze(['index.html','scene.js','styles.css']);
const QUOTA=12;
const OPT='AETHERIS_E8_OFFLINE_DASHBOARD_OPT_IN';
const sha=value=>createHash('sha256').update(value).digest('hex');
const deny=code=>{throw Error('E8_'+code);};
const keys=(obj,want)=>obj!==null&&typeof obj==='object'&&!Array.isArray(obj)&&
  Object.keys(obj).sort().join('|')===want.slice().sort().join('|');
const exactFalse=['ownerAuthenticated','ownerApproved','productionReady','stage45Go'];
const TEMPLATE=Object.freeze({
  version:1,source:'E7 local self-hashed review evidence',
  authenticatedTenantAccess:false,ownerAuthenticated:false,
  liveMissionAccess:false,ownerApproved:false,stage45Go:false
});
const ASSET_PATH=fileURLToPath(new URL('.',import.meta.url));

export function plan(){
  return {status:'PLAN_ONLY_NO_MISSION_DATA_READ',
    maxCases:QUOTA,readOnly:true,noNetwork:true,noModelInference:true,
    authenticatedTenantAccess:false,ownerApproved:false,stage45Go:false};
}
/** A hash chain can be reconstructed by a malicious source; this is a
 * consistency check, not proof of identity, consent, provenance or audit custody.
 */
export function normalizeReview(raw){
  if(!keys(raw,['schema','status','requestId','inputSha256',
      'workerProcessCount','workerExecutionAttempts','workerRetries',
      'evidenceOnly','staticHeuristicOnly','modelCalls','ownerAuthenticated',
      'ownerApproved','productionReady','stage45Go','rootSha256','trace'])||
      raw.schema!==1||typeof raw.requestId!=='string'||!UUID.test(raw.requestId)||
      !SHA.test(raw.inputSha256)||!SHA.test(raw.rootSha256)||
      raw.workerProcessCount!==2||raw.workerExecutionAttempts!==2||
      raw.workerRetries!==0||raw.modelCalls!==0||
      !verifyTrace(raw.trace)||raw.rootSha256!==raw.trace[3].sha256||
      !['STATIC_REVIEW_DENIED','REVIEW_CANDIDATE_NOT_APPROVED'].includes(raw.status))
    deny('INVALID_REVIEW_SCHEMA');
  if(exactFalse.some(key=>raw[key]!==false)||
     raw.evidenceOnly!==true||raw.staticHeuristicOnly!==true)
    deny('UNTRUSTED_OWNER_AUTHORITY');
  if(EVENT_TYPES.some((type,i)=>raw.trace[i].type!==type))
    deny('TRACE_TYPE_ORDER');
  const [start,integrity,safety,decision]=raw.trace.map(x=>x.payload);
  if(!keys(start,['requestId','roles','scopedTools','maxAttempts'])||
     start.requestId!==raw.requestId||start.maxAttempts!==2||
     canonical(start.roles)!==canonical(['integrity','safety'])||
     canonical(start.scopedTools)!==canonical(['sha256','bounded-static-pattern-review'])||
     !keys(integrity,['identity','evidence'])||
     integrity.identity!=='local-integrity-process'||
     !keys(integrity.evidence,['files','totalBytes'])||
     !Array.isArray(integrity.evidence.files)||integrity.evidence.files.length!==3||
     !keys(safety,['identity','evidence'])||
     safety.identity!=='local-safety-process'||
     !keys(safety.evidence,['checks','staticHeuristicOnly'])||
     safety.evidence.staticHeuristicOnly!==true||
     !keys(safety.evidence.checks,CHECKS)||
     !keys(decision,['candidate','policy','independentlyAudited',
        'ownerApproved','stage45Go'])||
     decision.policy!=='E7_STATIC_HEURISTIC_REVIEW_V1'||
     decision.independentlyAudited!==false||
     decision.ownerApproved!==false||decision.stage45Go!==false)
    deny('EVIDENCE_FIELDS_DENIED');
  const flags=safety.evidence.checks;
  if(CHECKS.some(k=>typeof flags[k]!=='boolean'))deny('EVIDENCE_FLAGS_DENIED');
  let total=0;
  for(let i=0;i<FILES.length;i++){
    const e=integrity.evidence.files[i];
    if(!keys(e,['path','bytes','sha256'])||e.path!==FILES[i]||
       !Number.isSafeInteger(e.bytes)||e.bytes<1||e.bytes>65000||
       typeof e.sha256!=='string'||!SHA.test(e.sha256))
      deny('EVIDENCE_HASH_DENIED');
    total+=e.bytes;
  }
  if(total>65000||total!==integrity.evidence.totalBytes||
     sha(canonical(integrity.evidence.files))!==raw.inputSha256)
    deny('EVIDENCE_DIGEST_MISMATCH');
  const clean=CHECKS.every(x=>flags[x]);
  if(decision.candidate!==clean||
     raw.status!==(clean?'REVIEW_CANDIDATE_NOT_APPROVED':'STATIC_REVIEW_DENIED'))
    deny('CONTRADICTORY_EVIDENCE');
  return {
    id:raw.requestId,inputSha256:raw.inputSha256,
    rootSha256:raw.rootSha256,
    status:clean?'NEEDS_HUMAN_REVIEW':'STATIC_DENIAL',
    flags:CHECKS.map(name=>({name,pass:flags[name]})),
    audit:raw.trace.map(x=>({sequence:x.sequence,type:x.type,
      previousHash:x.previousHash,sha256:x.sha256})),
    trustedIdentity:false,ownerApproved:false
  };
}
export function summarize(inputs){
  if(!Array.isArray(inputs)||inputs.length<1||inputs.length>QUOTA)
    deny('CASE_LIMIT');
  const cases=inputs.map(normalizeReview).sort((a,b)=>a.id.localeCompare(b.id));
  if(new Set(cases.map(x=>x.id)).size!==cases.length)
    deny('DUPLICATE_REVIEW_ID');
  return {...TEMPLATE,summary:{
    total:cases.length,
    pending:cases.filter(x=>x.status==='NEEDS_HUMAN_REVIEW').length,
    denied:cases.filter(x=>x.status==='STATIC_DENIAL').length,
    failedChecks:cases.reduce((n,c)=>n+c.flags.filter(f=>!f.pass).length,0)
  },cases};
}

function page(data){
  // Strictly normalized metadata (UUID, SHA, fixed labels, Booleans) only.
  // Defensive JSON escaping: a source cannot terminate the data script.
  const embedded=JSON.stringify(data).replace(/</g,'\\u003c')
    .replace(/>/g,'\\u003e').replace(/&/g,'\\u0026');
  return [
    '<!doctype html><html lang="en"><head><meta charset="utf-8">',
    '<meta name="viewport" content="width=device-width, initial-scale=1">',
    '<meta name="robots" content="noindex,nofollow">',
    '<meta http-equiv="Content-Security-Policy" content="default-src &#39;none&#39;; ',
    'script-src &#39;self&#39;; style-src &#39;self&#39;; connect-src &#39;none&#39;; ',
    'img-src &#39;none&#39;; object-src &#39;none&#39;; frame-src &#39;none&#39;; ',
    'base-uri &#39;none&#39;; form-action &#39;none&#39;">',
    '<title>Aetheris / Offline Mission Review</title>',
    '<link rel="stylesheet" href="./styles.css"></head><body>',
    '<a class="skip" href="#main">Skip to case review</a>',
    '<div class="shell"><aside class="side" aria-label="Workspace information">',
    '<span class="mark" aria-hidden="true">A</span><span class="brand">Aetheris</span>',
    '<p class="side-label">Mission review / local</p>',
    '<span class="capsule">LOCAL EVIDENCE ONLY</span>',
    '<p class="side-note">No authenticated tenants, missions, or operational actions.',
    ' This is not a live cockpit or security certification.</p></aside>',
    '<main class="main" id="main">',
    '<p class="eyebrow">REVIEW WORKSPACE · OFFLINE</p>',
    '<h1>Evidence, without<br>assumed authority.</h1>',
    '<p class="intro">Inspect locally generated E7 specialist reviews, ',
    'trace continuity, and rejected static checks. No remote services or ',
    'production mission records are accessed.</p>',
    '<div class="warning" role="note"><strong>Review only.</strong> ',
    'Self-hashed input is not authenticated owner or tenant evidence. ',
    'No approvals, deployments or agent actions are available.</div>',
    '<section class="stat-grid" aria-label="Review counters">',
    '<div class="stat"><b id="stat-total">0</b><span>Total cases</span></div>',
    '<div class="stat"><b id="stat-pending">0</b><span>Human review required</span></div>',
    '<div class="stat"><b id="stat-denied">0</b><span>Static denials</span></div>',
    '<div class="stat"><b id="stat-findings">0</b><span>Failed static checks</span></div>',
    '</section><section aria-label="Review cases">',
    '<h2 class="section-title">Case investigations</h2>',
    '<div class="toolbar" role="group" aria-label="Case filters">',
    '<button class="filter" data-filter="all" aria-pressed="true">All</button>',
    '<button class="filter" data-filter="pending" aria-pressed="false">Needs review</button>',
    '<button class="filter" data-filter="denied" aria-pressed="false">Denied</button>',
    '<label for="search">Find case</label>',
    '<input class="search" id="search" type="search"',
    ' placeholder="Search request ID" autocomplete="off"></div>',
    '<p class="caption"><span id="match-count">0</span> matching cases</p>',
    '<div class="columns"><div class="items" id="items" aria-label="Cases"></div>',
    '<div class="detail" id="detail" aria-label="Case details" aria-live="polite"></div></div>',
    '<p id="empty" class="empty" hidden>No matching cases.</p>',
    '</section><footer>Read-only, synthetic/local evidence. ',
    'Stage 45: NO_GO. All external attestations remain separate.</footer>',
    '</main></div>',
    '<script type="application/json" id="e8-data">',embedded,'</script>',
    '<script src="./app.js" defer></script></body></html>\n'
  ].join('');
}
export async function build(inputs){
  const dataset=summarize(inputs);
  const [css,js]=await Promise.all([
    fs.readFile(path.join(ASSET_PATH,'e8_board.css')),
    fs.readFile(path.join(ASSET_PATH,'e8_board_client.js'))
  ]);
  const files={'index.html':Buffer.from(page(dataset),'utf8'),
    'styles.css':css,'app.js':js};
  const manifest={
    formatVersion:1,artifactClass:'E8_OFFLINE_MISSION_REVIEW_ONLY',
    inputCaseCount:dataset.summary.total,
    ownerAuthenticated:false,tenantIsolationVerified:false,
    deploymentAuthorized:false,stage45Go:false,
    files:Object.keys(files).sort().map(name=>({
      name,bytes:files[name].length,sha256:sha(files[name])
    }))
  };
  files['manifest.json']=Buffer.from(canonical(manifest)+'\n');
  if(Object.values(files).reduce((n,v)=>n+v.length,0)>180000)
    deny('OUTPUT_SIZE_BUDGET');
  return {files,manifest};
}
async function safeReadInputs(dir){
  if(typeof dir!=='string'||!path.isAbsolute(dir))deny('ABSOLUTE_SOURCE_REQUIRED');
  const root=await fs.lstat(dir);
  if(!root.isDirectory()||root.isSymbolicLink())deny('UNSAFE_SOURCE_DIRECTORY');
  const names=(await fs.readdir(dir)).sort();
  if(names.length<1||names.length>QUOTA||names.some(n=>!FILE.test(n)))
    deny('SOURCE_FILE_ALLOWLIST');
  const rootReal=await fs.realpath(dir);
  const same=(x,y)=>process.platform==='win32'?
    path.resolve(x).toLowerCase()===path.resolve(y).toLowerCase():
    path.resolve(x)===path.resolve(y);
  const result=[];
  for(const name of names){
    const target=path.join(dir,name);
    const lstat=await fs.lstat(target);
    if(!lstat.isFile()||lstat.isSymbolicLink()||
       lstat.nlink!==1||lstat.size<1||lstat.size>24000)
      deny('UNSAFE_SOURCE_FILE');
    if(!same(await fs.realpath(target),path.join(rootReal,name)))
      deny('SOURCE_LINK_DENIED');
    const handle=await fs.open(target,FS.O_RDONLY|(FS.O_NOFOLLOW||0));
    let bytes;
    try{
      const actual=await handle.stat();
      if(actual.dev!==lstat.dev||actual.ino!==lstat.ino||
         actual.size!==lstat.size)deny('SOURCE_READ_RACE');
      bytes=await handle.readFile();
      const after=await fs.lstat(target);
      if(!after.isFile()||after.isSymbolicLink()||
         after.dev!==lstat.dev||after.ino!==lstat.ino||
         after.size!==bytes.length)deny('SOURCE_READ_RACE');
    }finally{await handle.close();}
    let review;
    try{review=JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(bytes));}
    catch{deny('BAD_SOURCE_JSON');}
    if('review-'+review.requestId+'.json'!==name)deny('SOURCE_NAME_ID_MISMATCH');
    result.push(review);
  }
  return result;
}
async function safeWrite(out,files){
  if(typeof out!=='string'||!path.isAbsolute(out))deny('ABSOLUTE_OUTPUT_REQUIRED');
  const parent=path.dirname(out);
  const root=await fs.lstat(parent);
  if(!root.isDirectory()||root.isSymbolicLink())deny('UNSAFE_OUTPUT_PARENT');
  await fs.mkdir(out,{recursive:false,mode:0o700});
  // Exclusive files only. Partial failure leaves the directory, preventing
  // retry from overwriting evidence or unreviewed files.
  for(const [name,content] of Object.entries(files)){
    const fd=await fs.open(path.join(out,name),FS.O_WRONLY|FS.O_CREAT|FS.O_EXCL|
      (FS.O_NOFOLLOW||0),0o600);
    try{await fd.writeFile(content);await fd.sync();}
    finally{await fd.close();}
  }
}
export async function run(args=process.argv.slice(2),env=process.env){
  let flags;
  try{
    flags=parseArgs({args,strict:true,allowPositionals:false,options:{
      'run-local':{type:'boolean',default:false},
      'source-dir':{type:'string'},'output-dir':{type:'string'}
    }}).values;
  }catch{return {exit:2,result:{status:'E8_INVALID_ARGS'}};}
  if(!flags['run-local'])return {exit:0,result:plan()};
  if(env[OPT]!=='yes')return {exit:2,result:{status:'E8_OPT_IN_REQUIRED',stage45Go:false}};
  try{
    const input=await safeReadInputs(flags['source-dir']);
    const result=await build(input);
    await safeWrite(flags['output-dir'],result.files);
    return {exit:0,result:{
      status:'E8_OFFLINE_READ_ONLY_BOARD_CREATED',caseCount:result.manifest.inputCaseCount,
      ownerAuthenticated:false,stage45Go:false,
      manifestSha256:sha(result.files['manifest.json'])}};
  }catch{
    return {exit:2,result:{status:'E8_OFFLINE_BOARD_DENIED',
      ownerAuthenticated:false,stage45Go:false}};
  }
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){
  const answer=await run();
  process.stdout.write(canonical(answer.result)+'\n');
  process.exitCode=answer.exit;
}
