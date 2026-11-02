/* W5-E: browser-measured text/background contrast on the actual DOM.
 * All source designs are clearly human-authored test doubles; no model is run.
 */
import assert from 'node:assert/strict';
import {readFile,mkdir,writeFile} from 'node:fs/promises';
import {dirname,join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {setTimeout as pause} from 'node:timers/promises';
import {chromium} from 'playwright';
import {compileAuthoredSite} from './w5c_restricted_css_compiler.mjs';
import {contrast} from './w5a_model_design_compiler.mjs';
import {syntheticDesign} from './w5b_simulated_provider.mjs';
import {EXAMPLE_CODES} from './w5c_test_doubles.mjs';
const here=dirname(fileURLToPath(import.meta.url));
const batch=JSON.parse(await readFile(join(here,'w5b_heldout_briefs.json'),'utf8'));
const trustedCss=await readFile(join(here,'w5a_base.css'),'utf8');
const output=join(here,'target/e6-w5e-contrast-browser-test-doubles');
const servers=[];let browser;
const toHex=css=>{
  const nums=String(css).match(/[\d.]+/g)?.slice(0,3).map(Number);
  assert.equal(nums?.length,3,'Not a CSS rgb triplet: '+css);
  assert(nums.every(x=>Number.isInteger(x)&&x>=0&&x<=255));
  return '#'+nums.map(x=>x.toString(16).padStart(2,'0')).join('');
};
async function listen(server,origin){
 for(let i=0;i<80;i++){
   if(server.exitCode!==null)throw Error('E6_W5E_PREVIEW_SERVER_EXIT');
   try{if((await fetch(origin+'/',{signal:AbortSignal.timeout(1200)})).status===200)return;}catch{}
   await pause(100);
 }
 throw Error('E6_W5E_SERVER_TIMEOUT');
}
try{
  await mkdir(output,{recursive:false});
  for(let i=0;i<3;i++){
    const brief=batch.briefs[i];
    const site=compileAuthoredSite(brief,syntheticDesign(brief,i),EXAMPLE_CODES[i],trustedCss);
    const root=join(output,String(i));await mkdir(root);
    for(const [path,body] of site.files){
      const file=join(root,path);await mkdir(dirname(file),{recursive:true});
      await writeFile(file,body,{flag:'wx'});
    }
  }
  browser=await chromium.launch({headless:true});
  for(let i=0;i<3;i++){
    const port=19290+i,origin='http://127.0.0.1:'+port;
    const server=spawn('python3',['-m','http.server',String(port),'--bind','127.0.0.1',
      '--directory',join(output,String(i))],{stdio:'ignore'});
    servers.push(server);await listen(server,origin);
    for(const [view,width,height] of [['compact',320,700],['mobile',390,844],['desktop',1440,900]]){
      const ctx=await browser.newContext({viewport:{width,height},
        javaScriptEnabled:false,reducedMotion:'reduce',serviceWorkers:'block'});
      const page=await ctx.newPage(),external=[],errors=[];
      try{
        await page.route('**/*',route=>{
          const req=route.request().url();
          if(!req.startsWith(origin+'/')){external.push(req);return route.abort();}
          return route.continue();
        });
        page.on('pageerror',e=>errors.push(String(e)));
        assert.equal((await page.goto(origin+'/',{waitUntil:'load'}))?.status(),200);
        const colors=await page.evaluate(()=>{
          const paintedBackground=element=>{
            for(let node=element;node;node=node.parentElement){
              const color=getComputedStyle(node).backgroundColor;
              if(/^rgb\\(/.test(color))return color;
              if(color==='rgba(0, 0, 0, 0)')continue;
              throw Error('Unsupported translucent or malformed background: '+color);
            }
            throw Error('No opaque painted ancestor background');
          };
          return [...document.querySelectorAll('section.section')].map(section=>({
            background:paintedBackground(section),
            eyebrow:getComputedStyle(section.querySelector('.eyebrow')).color,
            text:getComputedStyle(section.querySelector('.section__text')).color
          }));
        });
        assert.equal(colors.length,2);
        for(const c of colors){
          const background=toHex(c.background);
          assert(contrast(background,toHex(c.eyebrow))>=4.5,
            'Visible eyebrow low contrast: '+i+'/'+view+' '+JSON.stringify(c));
          assert(contrast(background,toHex(c.text))>=4.5,
            'Visible content low contrast: '+i+'/'+view+' '+JSON.stringify(c));
        }
        const overflow=await page.evaluate(()=>
          Math.max(0,document.documentElement.scrollWidth-innerWidth));
        assert(overflow<=2,'layout overflow '+i+'/'+view+' '+overflow);
        assert.equal(await page.locator('script').count(),0);
        assert.deepEqual(external,[]);
        assert.deepEqual(errors,[]);
        console.log('E6_W5E_COMPUTED_CONTRAST_BROWSER_PASS '+JSON.stringify({
          mock:i,viewport:view,sections:colors.length,minimumRatio:4.5,
          realModelUsed:false,ownerApproved:false,releaseGo:false
        }));
      }finally{await ctx.close();}
    }
  }
}finally{
 if(browser)await browser.close();
 for(const server of servers)if(server.exitCode===null&&server.signalCode===null){
   server.kill('SIGTERM');await once(server,'exit');
 }
}
