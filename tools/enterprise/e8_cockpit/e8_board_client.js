/* E8 read-only operator interface. Never executes source content or posts data. */
"use strict";
const evidence=JSON.parse(document.getElementById("e8-data").textContent);
const list=document.getElementById("items");
const detail=document.getElementById("detail");
const empty=document.getElementById("empty");
const search=document.getElementById("search");
const filters=[...document.querySelectorAll("button[data-filter]")];
let active="all",selected=evidence.cases[0]?.id;
const element=(tag,cls,content)=>{
  const n=document.createElement(tag);
  if(cls)n.className=cls;
  if(content!==undefined)n.textContent=String(content);
  return n;
};
function showCase(item){
  detail.replaceChildren();
  if(!item){detail.append(element("p","caption","Choose a review case."));return;}
  detail.append(element("p","eyebrow","Case evidence / no approval"));
  detail.append(element("h3","",item.status==="STATIC_DENIAL"?"Static checks denied":"Awaiting human review"));
  detail.append(element("p","caption","Untrusted local review metadata. Not an authenticated operational task or tenant record."));
  for(const [label,value] of [
    ["Request ID",item.id],["Input SHA-256",item.inputSha256],
    ["Trace root SHA-256",item.rootSha256]
  ]){
    const card=element("div","evidence");
    card.append(element("strong","",label),element("div","mono",value));
    detail.append(card);
  }
  detail.append(element("h4","","Static review checks"));
  const checklist=element("div","checks");
  for(const flag of item.flags){
    const line=element("div","check");
    const dot=element("span","dot"+(flag.pass?"":" bad"));
    dot.setAttribute("aria-hidden","true");
    line.append(dot,element("span","",(flag.pass?"Pass · ":"Denied · ")+flag.name));
    checklist.append(line);
  }
  detail.append(checklist);
  detail.append(element("h4","","Hash-linked event sequence"));
  const chain=element("ol","timeline");
  for(const event of item.audit)
    chain.append(element("li","","#"+event.sequence+" · "+event.type+" · "+event.sha256.slice(0,12)+"…"));
  detail.append(chain);
}
function render(){
  filters.forEach(b=>b.setAttribute("aria-pressed",String(b.dataset.filter===active)));
  const keyword=search.value.toLowerCase().trim();
  const visible=evidence.cases.filter(c=>
    (active==="all"||(active==="denied"?c.status==="STATIC_DENIAL":c.status==="NEEDS_HUMAN_REVIEW"))&&
    (c.id.includes(keyword)||c.status.toLowerCase().includes(keyword)));
  if(!visible.some(c=>c.id===selected))selected=visible[0]?.id;
  list.replaceChildren();
  for(const item of visible){
    const button=element("button","item");
    button.type="button";
    button.setAttribute("aria-pressed",String(item.id===selected));
    button.setAttribute("aria-controls","detail");
    button.append(element("span","id",item.id),element("small","","Local E7 specialist evidence"));
    button.append(element("span","status"+(item.status==="STATIC_DENIAL"?" deny":""),
       item.status==="STATIC_DENIAL"?"Static denied":"Needs review"));
    button.addEventListener("click",()=>{selected=item.id;render();});
    list.append(button);
  }
  empty.hidden=visible.length>0;
  document.getElementById("match-count").textContent=String(visible.length);
  showCase(evidence.cases.find(c=>c.id===selected));
}
for(const button of filters)button.addEventListener("click",()=>{
  active=button.dataset.filter;render();
});
search.addEventListener("input",render);
for(const [key,value] of [
  ["total",evidence.summary.total],["pending",evidence.summary.pending],
  ["denied",evidence.summary.denied],["findings",evidence.summary.failedChecks]
])document.getElementById("stat-"+key).textContent=String(value);
render();
document.documentElement.dataset.e8Ready="true";
