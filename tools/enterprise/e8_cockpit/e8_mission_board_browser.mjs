/** Genuine offline Chromium E8 mission review board acceptance.
 * Uses only synthetic actual E7 reviews compiled by CI, never live missions.
 */
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {chromium} from 'playwright';
import {createHash} from 'node:crypto';

const ROOT=path.dirname(fileURLToPath(import.meta.url));
const BOARD=path.resolve(ROOT,'../../../target/e8/review-board');
const file=pathToFileURL(path.join(BOARD,'index.html')).href;
const manifest=JSON.parse(await fs.readFile(path.join(BOARD,'manifest.json'),'utf8'));
assert.equal(manifest.artifactClass,'E8_OFFLINE_MISSION_REVIEW_ONLY');
assert.equal(manifest.inputCaseCount,2);
assert.equal(manifest.ownerAuthenticated,false);
assert.equal(manifest.deploymentAuthorized,false);
assert.equal(manifest.stage45Go,false);
for(const entry of manifest.files){
  const content=await fs.readFile(path.join(BOARD,entry.name));
  assert.equal(content.length,entry.bytes);
  assert.equal(createHash('sha256').update(content).digest('hex'),entry.sha256);
}
const views=[
  {name:'desktop',width:1440,height:900,motion:'no-preference'},
  {name:'mobile',width:390,height:844,motion:'no-preference'},
  {name:'reduced',width:390,height:844,motion:'reduce'}
];
const browser=await chromium.launch({headless:true});
try{
  for(const view of views){
    const ctx=await browser.newContext({
      viewport:{width:view.width,height:view.height},reducedMotion:view.motion,
      serviceWorkers:'block'
    });
    try{
      const page=await ctx.newPage();
      const exceptions=[],external=[];
      page.on('pageerror',e=>exceptions.push(e.message));
      page.on('console',m=>{if(m.type()==='error')exceptions.push(m.text());});
      page.on('request',q=>{if(!q.url().startsWith('file:'))external.push(q.url());});
      await page.route('**/*',route=>route.request().url().startsWith('file:')?
        route.continue():route.abort());
      await page.goto(file,{waitUntil:'load'});
      await page.waitForFunction(()=>document.documentElement.dataset.e8Ready==='true');
      assert.equal(await page.locator('main#main').count(),1);
      assert.equal(await page.locator('#stat-total').innerText(),'2');
      assert.equal(await page.locator('#stat-pending').innerText(),'1');
      assert.equal(await page.locator('#stat-denied').innerText(),'1');
      assert.equal(await page.locator('#stat-findings').innerText(),'1');
      assert.equal(await page.locator('#items button').count(),2);
      assert.equal(await page.locator('form').count(),0);
      const renderedText=await page.locator('body').innerText();
      assert(renderedText.includes('No authenticated tenants, missions, or operational actions.'));
      assert(renderedText.includes('Stage 45: NO_GO'));
      await page.locator('.skip').focus();
      assert.equal(await page.evaluate(()=>document.activeElement?.getAttribute('href')),'#main');

      await page.locator('button[data-filter="denied"]').click();
      assert.equal(await page.locator('button[data-filter="denied"]').getAttribute('aria-pressed'),'true');
      assert.equal(await page.locator('#items button').count(),1);
      assert.equal(await page.locator('#detail h3').innerText(),'Static checks denied');
      assert.equal(await page.getByText('Denied · noRemoteJs').count(),1);
      await page.locator('button[data-filter="pending"]').click();
      assert.equal(await page.locator('#items button').count(),1);
      assert.equal(await page.locator('#detail h3').innerText(),'Awaiting human review');
      await page.locator('#search').fill('no-such-request-id');
      assert.equal(await page.locator('#items button').count(),0);
      assert.equal(await page.locator('#empty').isVisible(),true);
      await page.locator('#search').fill('');
      await page.locator('button[data-filter="all"]').click();
      assert.equal(await page.locator('#items button').count(),2);
      // Exercise real keyboard selection, not only pointer clicks. The
      // selected case button is recreated by render() and must retain focus.
      await page.locator('#items button').last().focus();
      await page.keyboard.press('Enter');
      assert.equal(await page.evaluate(()=>document.activeElement?.matches(
        '#items button.item[aria-pressed="true"]')),true);
      assert.equal(await page.locator('#detail .timeline li').count(),4);
      const width=await page.evaluate(()=>({
        content:document.documentElement.scrollWidth,viewport:innerWidth
      }));
      assert(width.content<=width.viewport+1,
        'E8 dashboard horizontal overflow: '+JSON.stringify(width));
      assert.deepEqual(external,[]);
      assert.deepEqual(exceptions,[]);
      const css=await page.evaluate(()=>{
        return getComputedStyle(document.body).backgroundColor;
      });
      assert.equal(css,'rgb(16, 19, 21)');
      console.log('E8_REAL_CHROMIUM_MISSION_REVIEW_PASS',view.name);
    }finally{await ctx.close();}
  }
}finally{await browser.close();}
