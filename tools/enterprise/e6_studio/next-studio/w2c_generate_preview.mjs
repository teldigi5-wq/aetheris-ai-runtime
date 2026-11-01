/* Fixed-fixture W2-C CLI: one local demonstration, fail-closed and write-once. */
import {readFile,mkdir,lstat,writeFile} from 'node:fs/promises';
import {dirname,join,relative,isAbsolute} from 'node:path';
import {fileURLToPath} from 'node:url';
import {compileNarrativeW2Site} from './w2c_narrative_compiler.mjs';

const here=dirname(fileURLToPath(import.meta.url));
async function main(){
  if(process.argv.length!==2)throw Error('NO_USER_SELECTED_PATHS_ALLOWED');
  const [data,spec]=await Promise.all([
    readFile(join(here,'w2b_arcloom_brief.json'),'utf8').then(JSON.parse),
    readFile(join(here,'w2c_arcloom_studies.json'),'utf8').then(JSON.parse)
  ]);
  const built=compileNarrativeW2Site(data.brief,data.content,data.direction,spec);
  const parent=join(here,'target');
  try{
    const stat=await lstat(parent);
    if(stat.isSymbolicLink()||!stat.isDirectory())throw Error('INVALID_OUTPUT_PARENT');
  }catch(e){
    if(e.code!=='ENOENT')throw e;
    await mkdir(parent,{recursive:false});
  }
  const output=join(parent,'e6-w2c-arcloom');
  await mkdir(output,{recursive:false});
  for(const [name,body] of built.files){
    if(name.startsWith('/')||name.includes('..')||name.includes('\\'))throw Error('UNSAFE_RELATIVE_PATH');
    const file=join(output,name),rel=relative(output,file);
    if(!rel||rel.startsWith('..')||isAbsolute(rel))throw Error('ESCAPE_OUTPUT_ROOT');
    await mkdir(dirname(file),{recursive:true});
    await writeFile(file,body,{flag:'wx',mode:0o644});
  }
  process.stdout.write(JSON.stringify({kind:'E6_W2C_LOCAL_NARRATIVE_PREVIEW',
    output,caseStudies:built.evidence.caseStudyCount,routes:built.evidence.routes,
    modelCalled:false,stage45Go:false,releaseGo:false})+'\n');
}
main().catch(e=>{process.stderr.write('E6_W2C_GENERATION_BLOCKED '+String(e.message)+'\n');process.exitCode=1});
