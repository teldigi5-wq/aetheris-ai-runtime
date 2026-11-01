/**
 * E6 W2-A: deterministic STATIC website compiler. NOT model authorship, 3D,
 * asset ingestion, publication authority, or an arbitrary-source code generator.
 */
import {createHash} from 'node:crypto';
import {evaluateOriginalExperienceContract} from './experience_contract.mjs';
import {STUDIO_CSS, STUDIO_JS} from './w2_site_assets.mjs';

const CATEGORIES = new Set(['identity', 'digital', 'spatial']);
const textFields = (value, min, max) => typeof value === 'string' &&
  value.length >= min && value.length <= max &&
  !/[\x00-\x1f\x7f]/.test(value) && value.trim().length >= min;

function record(value, keys) {
  if (value === null || typeof value !== 'object' || Array.isArray(value) ||
      (Object.getPrototypeOf(value) !== Object.prototype && Object.getPrototypeOf(value) !== null)) return false;
  const own = Reflect.ownKeys(value);
  return own.length === keys.length && own.every(k =>
    typeof k === 'string' && keys.includes(k) &&
    Object.hasOwn(value, k) &&
    Object.hasOwn(Object.getOwnPropertyDescriptor(value, k), 'value'));
}
function array(value, max) {
  if (!Array.isArray(value) || value.length > max ||
      Object.getPrototypeOf(value) !== Array.prototype) return false;
  const own = Reflect.ownKeys(value);
  if (own.some(k => k !== 'length' && (!/^(0|[1-9]\d*)$/.test(String(k)) || Number(k) >= value.length))) return false;
  for (let i = 0; i < value.length; i++) {
    if (!Object.hasOwn(Object.getOwnPropertyDescriptor(value, String(i)) || {}, 'value')) return false;
  }
  return true;
}
const esc = value => String(value).replace(/&/g,'&amp;').replace(/</g,'&lt;')
  .replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&#39;');
const sha = value => createHash('sha256').update(value,'utf8').digest('hex');
const fail = code => { throw new Error('E6_W2_' + code); };
const routeHref = path => path === '/' ? '/' : path + '/';
const pageOutput = path => path === '/' ? 'index.html' : path.slice(1) + '/index.html';
const itemKinds = new Set(['services','case-studies','gallery','process','team']);

function assertContent(plan, content) {
  if (!record(content,['siteName','siteDescription','contactEmail','pages']) ||
      !textFields(content.siteName,3,80) ||
      !textFields(content.siteDescription,15,180) ||
      typeof content.contactEmail !== 'string' ||
      !/^[a-zA-Z0-9._%+-]{1,64}@[a-zA-Z0-9.-]{1,100}\.[a-zA-Z]{2,24}$/.test(content.contactEmail) ||
      !array(content.pages,12) || content.pages.length !== plan.pages.length) fail('INVALID_CONTENT_ROOT');
  const byPath = new Map();
  for (const page of content.pages) {
    if (!record(page,['path','navLabel','sections']) ||
        !textFields(page.navLabel,2,32) || !array(page.sections,16) ||
        byPath.has(page.path)) fail('INVALID_CONTENT_PAGE');
    byPath.set(page.path,page);
  }
  const validRoutes = new Set(plan.pages.map(p=>p.path));
  for (const p of plan.pages) {
    const c = byPath.get(p.path);
    if (!c || c.sections.length !== p.sections.length) fail('PAGE_OR_SECTION_MISMATCH');
    for (let i = 0; i < p.sections.length; i++) {
      const section = p.sections[i], value = c.sections[i];
      if (!record(value,['id','eyebrow','title','body','items','ctaLabel','ctaPath']) ||
          value.id !== section.id || !textFields(value.eyebrow,2,42) ||
          !textFields(value.title,3,110) || !textFields(value.body,12,420) ||
          !textFields(value.ctaLabel,0,44) || typeof value.ctaPath !== 'string' ||
          (value.ctaPath !== '' && !validRoutes.has(value.ctaPath)) ||
          Boolean(value.ctaPath) !== Boolean(value.ctaLabel) ||
          !array(value.items,6)) fail('INVALID_SECTION_CONTENT');
      if (section.kind === 'scene3d') fail('SCENE3D_REQUIRES_W3_W4');
      if (section.assetSource !== 'none') fail('MEDIA_PROVENANCE_REQUIRES_W3');
      if (itemKinds.has(section.kind) && value.items.length === 0) fail('ITEMS_REQUIRED');
      if (!itemKinds.has(section.kind) && value.items.length !== 0) fail('ITEMS_NOT_ALLOWED');
      for (const item of value.items) {
        if (!record(item,['title','body','category']) ||
            !textFields(item.title,3,76) || !textFields(item.body,8,230) ||
            !CATEGORIES.has(item.category)) fail('INVALID_ITEM');
      }
    }
  }
  return byPath;
}
function card(item, index, filterable) {
  const art = (index % 3) + 1;
  return '<article class="project-card" data-category="' + esc(item.category) + '">' +
    '<div aria-hidden="true" class="art art-' + art + '"><span class="art-index">0' + (index+1) + '</span></div>' +
    '<div class="card-copy"><p class="meta">' + esc(item.category) + '</p><h3>' + esc(item.title) +
    '</h3><p>' + esc(item.body) + '</p></div></article>';
}
function sectionHtml(kind, sec, index, CONTACT_EMAIL) {
  const id=esc(sec.id), eyebrow=esc(sec.eyebrow), heading=esc(sec.title);
  const intro='<div class="section-heading"><p class="eyebrow">' + eyebrow +
    '</p><h2>' + heading + '</h2><p class="lead">' + esc(sec.body) + '</p></div>';
  const cta=sec.ctaPath ? '<a class="text-link" href="' + routeHref(sec.ctaPath) + '">' +
    esc(sec.ctaLabel) + '<span aria-hidden="true"> ↗</span></a>' : '';
  if (kind === 'hero') return '<section class="hero" id="' + id +
    '"><div class="hero-inner"><p class="eyebrow">' + eyebrow +
    '</p><h1>' + heading + '</h1><p class="hero-lead">' + esc(sec.body) +
    '</p>' + cta + '</div><div class="hero-art" aria-hidden="true"><div class="hero-arc"></div><div class="hero-disc"></div><span class="hero-art-label">FORM / FUNCTION</span></div></section>';
  if (kind === 'contact') return '<section class="section contact-section" id="' + id +
    '">' + intro + '<form class="contact-form" data-email-compose data-contact-email="' + esc(CONTACT_EMAIL) + '"><label>Name<input name="name" autocomplete="name" required minlength="2" maxlength="80"></label>' +
    '<label>Email<input name="email" type="email" autocomplete="email" required maxlength="180"></label>' +
    '<label>Message<textarea name="message" required minlength="10" maxlength="1500" rows="5"></textarea></label>' +
    '<button class="button" type="submit">Open email draft <span aria-hidden="true">↗</span></button>' +
    '<p class="form-note">This preview does not submit or store anything. Your email application opens a draft; you decide whether to send it.</p>' +
    '<p role="status" aria-live="polite" data-compose-status></p></form>' + cta + '</section>';
  const hasCards = itemKinds.has(kind);
  const buttons = (kind === 'gallery' || kind === 'case-studies') ?
    '<div class="filters" role="group" aria-label="Filter projects">' +
    ['all','identity','digital','spatial'].map(v=>'<button type="button" data-filter="' + v +
    '" aria-pressed="' + (v === 'all' ? 'true' : 'false') + '">' +
    v.charAt(0).toUpperCase() + v.slice(1) + '</button>').join('') + '<p class="visually-hidden" role="status" aria-live="polite" data-filter-status></p></div>' : '';
  const cards = hasCards ? '<div class="' + (buttons ? 'card-grid filter-grid' : 'card-grid') + '">' +
    sec.items.map((item,i)=>card(item,i,Boolean(buttons))).join('') + '</div>' : '';
  return '<section class="section section-' + esc(kind) + '" id="' + id + '">' +
    '<div class="section-index" aria-hidden="true">/' + String(index+1).padStart(2,'0') + '</div>' +
    intro + buttons + cards + cta + '</section>';
}
function makePage(plan, page, content, nav) {
  const title=page.path === '/' ? content.siteName : nav.get(page.path).navLabel + ' — ' + content.siteName;
  const sections=plan.sections.map((s,i)=>sectionHtml(s.kind,page.sections[i],i,content.contactEmail)).join('\n');
  const links=Array.from(nav,(entry)=>'<a href="' + routeHref(entry[0]) + '"' +
    (entry[0] === page.path ? ' aria-current="page"' : '') + '>' + esc(entry[1].navLabel) + '</a>').join('');
  return '<!doctype html>\n<html lang="en"><head><meta charset="utf-8">' +
    '<meta name="viewport" content="width=device-width, initial-scale=1">' +
    '<meta name="description" content="' + esc(content.siteDescription) + '">' +
    '<meta name="robots" content="noindex,nofollow">' +
    '<meta name="color-scheme" content="light">' +
    '<meta http-equiv="Content-Security-Policy" content="default-src &#39;none&#39;; style-src &#39;self&#39;; script-src &#39;self&#39;; img-src &#39;self&#39; data:; base-uri &#39;none&#39;; form-action &#39;none&#39;">' +
    '<title>' + esc(title) + '</title><link rel="stylesheet" href="/assets/studio.css">' +
    '<script src="/assets/studio.js" defer></script></head><body>' +
    '<a class="skip" href="#main">Skip to content</a>' +
    '<header class="site-header"><a class="wordmark" href="/" aria-label="' + esc(content.siteName) +
    ', home"><span class="wordmark-glyph" aria-hidden="true">◉</span>' + esc(content.siteName) +
    '</a><nav aria-label="Primary navigation">' + links + '</nav></header>' +
    '<main id="main">' + sections + '</main>' +
    '<footer class="footer"><div><strong>' + esc(content.siteName) +
    '</strong><p>' + esc(content.siteDescription) + '</p></div>' +
    '<div><span class="eyebrow">Independent studio prototype</span><a href="mailto:' +
    esc(content.contactEmail) + '">' + esc(content.contactEmail) + '</a>' +
    '<small>Demonstration only. No client work or outcomes are claimed.</small></div></footer></body></html>\n';
}
export function compileW2Site(brief, content) {
  const check=evaluateOriginalExperienceContract(brief);
  if (!check.valid || !check.plan) fail('W1_BRIEF_REJECTED');
  const plan=check.plan, nav=assertContent(plan,content);
  const files=new Map();
  files.set('assets/studio.css', STUDIO_CSS);
  files.set('assets/studio.js', STUDIO_JS);
  for(const p of plan.pages) files.set(pageOutput(p.path),makePage(p,nav.get(p.path),content,nav));
  const hashes=Object.fromEntries([...files].map(([name,bytes])=>[name,sha(bytes)]).sort((a,b)=>a[0].localeCompare(b[0])));
  const evidence={kind:'E6_W2_DETERMINISTIC_STATIC_SITE_V1',projectSlug:plan.projectSlug,
    routes:plan.pages.map(p=>p.path),fileSha256:hashes,
    deterministicCompiler:true,genuineModelAuthorshipProven:false,realBrowserVerified:false,
    ownerApproved:false,deploymentAuthorized:false,stage45Go:false,releaseGo:false,
    limitations:['Demonstration copy is authored locally; no AI model generated this website',
      'Email compose uses the visitor mail client; no backend receives messages',
      'Scene3D and external media require later W3/W4 validated pipelines',
      'Physical hardware, accessibility and human acceptance remain pending']};
  files.set('build-evidence.json',JSON.stringify(evidence,null,2)+'\n');
  return {files,evidence};
}
