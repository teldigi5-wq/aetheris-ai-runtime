/** E7 deterministic specialist worker, isolated OS process, no tool/FS/network imports.
 * This is not an LLM agent, a container sandbox, or a signed workload identity.
 */
import {createHash} from 'node:crypto';

const FILES=['index.html','scene.js','styles.css'];
const ROLES=new Set(['integrity','safety']);
function deny(){throw Error('E7_WORKER_UNTRUSTED_REQUEST');}
const hash=buf=>createHash('sha256').update(buf).digest('hex');
const validKeys=(a,b)=>a&&typeof a==='object'&&!Array.isArray(a)&&
  Object.keys(a).sort().join('|')===b.slice().sort().join('|');

async function run(){
  if(process.argv.length!==3||!ROLES.has(process.argv[2]))deny();
  const role=process.argv[2];
  let bytes=0;const chunks=[];
  for await(const chunk of process.stdin){
    bytes+=chunk.length;
    if(bytes>190000)deny();
    chunks.push(chunk);
  }
  if(bytes===0)deny();
  let payload;
  try{payload=JSON.parse(Buffer.concat(chunks).toString('utf8'));}
  catch{deny();}
  if(!validKeys(payload,['version','role','files'])||
     payload.version!==1||payload.role!==role||!validKeys(payload.files,FILES))deny();
  const files={};
  for(const name of FILES){
    const encoded=payload.files[name];
    if(typeof encoded!=='string'||encoded.length>95000||
       !/^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(encoded))deny();
    const bin=Buffer.from(encoded,'base64');
    if(bin.toString('base64')!==encoded||bin.length===0||bin.length>65000)deny();
    files[name]=bin;
  }
  let result;
  if(role==='integrity'){
    result={files:FILES.map(name=>({path:name,bytes:files[name].length,
      sha256:hash(files[name])})),totalBytes:FILES.reduce((sum,n)=>sum+files[n].length,0)};
  }else{
    const html=files['index.html'].toString('utf8');
    const css=files['styles.css'].toString('utf8');
    const js=files['scene.js'].toString('utf8');
    const checks={
      mainLandmark:/<main\b/i.test(html),
      skipLink:/class=["']skip-link["'][^>]*href=["']#main["']/i.test(html),
      reducedMotion:/prefers-reduced-motion/i.test(css),
      fixedLocalJs:/<script\s+src=["']\.\/scene\.js["'][^>]*><\/script>/i.test(html),
      fixedLocalCss:/<link\s+rel=["']stylesheet["']\s+href=["']\.\/styles\.css["'][^>]*>/i.test(html),
      noInlineEvent:!/<[^>]+\son[a-z]+\s*=/i.test(html),
      noUnreviewedResource:!/(?:https?:\/\/|javascript:|data:|<iframe\b|<form\b|\bsrcdoc\s*=)/i.test(html),
      noRemoteCss:!/(?:@import\b|url\s*\(\s*['"]?(?:https?:|\/\/|data:))/i.test(css),
      noRemoteJs:!/(?:\bfetch\s*\(|\bXMLHttpRequest\b|\bWebSocket\b|\bEventSource\b|\bsendBeacon\s*\(|\bimport\s*\()/i.test(js)
    };
    result={checks,staticHeuristicOnly:true};
  }
  process.stdout.write(JSON.stringify({schema:1,role,result})+'\n');
}
run().catch(()=>{process.stderr.write('E7_WORKER_DENIED\n');process.exitCode=2;});
