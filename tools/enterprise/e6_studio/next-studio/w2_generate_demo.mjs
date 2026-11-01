/**
 * Local-only W2 demonstration generator. Fixed source fixture and fixed output.
 * Never overwrites, uploads, deploys, or reads arbitrary user-selected paths.
 */
import {readFile, mkdir, lstat, writeFile} from 'node:fs/promises';
import {dirname,join,relative,isAbsolute} from 'node:path';
import {fileURLToPath} from 'node:url';
import {compileW2Site} from './w2_compiler.mjs';

const here=dirname(fileURLToPath(import.meta.url));
const parent=join(here,'target');
const output=join(parent,'e6-w2-multipage-site');

async function main() {
  if(process.argv.length!==2) throw Error('UNEXPECTED_ARGUMENTS');
  const data=JSON.parse(await readFile(join(here,'w2_demo_brief.json'),'utf8'));
  const built=compileW2Site(data.brief,data.content);
  try {
    const stat=await lstat(parent);
    if(!stat.isDirectory() || stat.isSymbolicLink()) throw Error('TARGET_PARENT_NOT_REAL_DIRECTORY');
  }catch(e){
    if(e.code!=='ENOENT') throw e;
    await mkdir(parent,{recursive:false});
  }
  // O_EXCL on the output root refuses an existing preview or link.
  await mkdir(output,{recursive:false});
  for(const [name,bytes] of built.files) {
    if(name.startsWith('/') || name.includes('..') || name.includes('\\')) throw Error('UNSAFE_GENERATED_PATH');
    const path=join(output,name), rel=relative(output,path);
    if(rel.startsWith('..') || isAbsolute(rel) || rel==='') throw Error('ESCAPED_OUTPUT_ROOT');
    await mkdir(dirname(path),{recursive:true});
    await writeFile(path,bytes,{flag:'wx',mode:0o644});
  }
  process.stdout.write(JSON.stringify({
    kind:'E6_W2_LOCAL_PREVIEW_CREATED',output,files:built.files.size,
    routes:built.evidence.routes,modelCalled:false,stage45Go:false,releaseGo:false
  })+'\n');
}
main().catch(error=>{
  process.stderr.write('E6_W2_GENERATION_BLOCKED '+String(error?.message||'UNKNOWN')+'\n');
  process.exitCode=1;
});
