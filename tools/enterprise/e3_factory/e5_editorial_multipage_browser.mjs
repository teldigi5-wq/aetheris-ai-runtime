/**
 * E5 genuine Chromium check for extracted, offline, 3-page editorial ZIP.
 * No model, external network, unverifiable photos or production deployment.
 */
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import crypto from "node:crypto";
import {fileURLToPath, pathToFileURL} from "node:url";
import {chromium} from "playwright";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../../..");
const base = path.join(root, "target", "e5");
const sha = bytes => crypto.createHash("sha256").update(bytes).digest("hex");
const variants = [
  {name:"paper", background:"rgb(246, 240, 231)"},
  {name:"charcoal", background:"rgb(17, 24, 33)"}
];
const viewports = [
  {name:"desktop",width:1280,height:800,motion:"no-preference"},
  {name:"mobile",width:390,height:844,motion:"no-preference"},
  {name:"reduced",width:390,height:844,motion:"reduce"}
];

const browser = await chromium.launch({headless:true,args:[
  "--enable-webgl","--enable-unsafe-swiftshader","--use-gl=angle","--use-angle=swiftshader"
]});
try {
  for(const variant of variants) {
    const folder = path.join(base,"editorial-"+variant.name);
    const manifest = JSON.parse(await fs.readFile(
      path.join(folder,"preview-manifest.json"),"utf8"));
    assert.equal(manifest.artifactClass,"E5_THREE_PAGE_HUMAN_CONTENT_EDITORIAL_REVIEW");
    assert.equal(manifest.ownerApproved,false);
    assert.equal(manifest.productionReady,false);
    assert.equal(manifest.siteCodeModelAuthored,false);
    assert.equal(manifest.stage45Go,false);
    assert.deepEqual(manifest.pagePaths,["index.html","work.html","contact.html"]);
    for(const entry of manifest.files) {
      const bytes=await fs.readFile(path.join(folder,entry.path));
      assert.equal(bytes.length,entry.bytes);
      assert.equal(sha(bytes),entry.sha256);
    }
    for(const view of viewports) {
      const ctx=await browser.newContext({
        viewport:{width:view.width,height:view.height},
        reducedMotion:view.motion,
        serviceWorkers:"block"
      });
      try {
        const page=await ctx.newPage();
        const errors=[],external=[];
        page.on("pageerror",error=>errors.push(error.message));
        page.on("console",message=>{
          if(message.type()==="error") errors.push(message.text());
        });
        page.on("request",request=>{
          if(!request.url().startsWith("file:"))external.push(request.url());
        });
        await page.route("**/*",route=>route.request().url().startsWith("file:")
          ?route.continue():route.abort());
        const file = name => pathToFileURL(path.join(folder,name)).href;

        await page.goto(file("index.html"),{waitUntil:"load"});
        assert.equal(await page.locator('nav[aria-label="Editorial pages"]').count(),1);
        assert.equal(await page.locator('a[href="./work.html"]').count(),1);
        assert.equal(await page.locator('a[href="./contact.html"]').count(),1);
        assert.equal(await page.locator("main#main").count(),1);
        await page.locator('nav[aria-label="Editorial pages"] a[href="./work.html"]').click();
        assert.equal(page.url(),file("work.html"));
        assert.equal(await page.locator("h1").innerText(),"Material and Motion");
        assert.equal(await page.locator('nav[aria-label="Editorial pages"] a[aria-current="page"]').innerText(),"Project");
        assert.equal(await page.locator("main#main").count(),1);
        const bg = await page.evaluate(()=>getComputedStyle(document.body).backgroundColor);
        assert.equal(bg,variant.background);
        const columns=await page.evaluate(()=>getComputedStyle(
          document.querySelector(".e5-editorial-columns")).gridTemplateColumns);
        if(view.width<700) assert.equal(columns.split(" ").length,1);
        else assert.equal(columns.split(" ").length,2);
        await page.locator('nav[aria-label="Editorial pages"] a[href="./contact.html"]').click();
        assert.equal(page.url(),file("contact.html"));
        assert.equal(await page.locator("h1").innerText(),"Discuss a creative brief");
        assert.equal(await page.locator('a[href="mailto:studio@example.invalid"]').count(),1);
        assert.equal(await page.locator("form").count(),0);
        assert.equal(await page.locator("script").count(),0);
        await page.locator(".e5-editorial-skip").focus();
        assert.equal(await page.evaluate(()=>document.activeElement?.textContent?.trim()),
                     "Skip to main content");
        assert.equal(await page.locator("main#main").count(),1);
        const widths=await page.evaluate(()=>({
          scroll:document.documentElement.scrollWidth,viewport:window.innerWidth
        }));
        assert(widths.scroll<=widths.viewport+1,"Horizontal overflow");
        assert.deepEqual(errors,[]);
        assert.deepEqual(external,[]);
        console.log("E5_THREE_PAGE_OFFLINE_CHROMIUM_PASS",variant.name,view.name);
      } finally {
        await ctx.close();
      }
    }
  }
} finally {
  await browser.close();
}
