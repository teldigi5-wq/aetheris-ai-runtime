#!/usr/bin/env python3
"""E3.9G-12B2 independently reviewed TYPED repair-scope gate (no model calls).

Four fixed public synthetic defects across two distinct sites. Exact source,
site, fault and old substring. One bounded replacement only. No model code
execution, imports, arbitrary paths or side effects; pure validation only.
Trusted evaluator-owned code MUST NOT be mounted into a future model caller.
"""
from __future__ import annotations

import hashlib
import json
import re
from typing import Mapping

import e39g12b_two_site_corpus as corpus

RULES={
 ("studynest","keyboard-focus-visibility"):(
    ".skip:focus{top:-120px;}","css-focus-top"),
 ("studynest","responsive-mobile-overflow"):(
    "grid-template-columns:1fr;min-width:960px;","css-grid-minwidth"),
 ("cityline","reduced-motion"):(
    "@media(prefers-reduced-motion:reduce){.ticker{animation:floatline 4s linear infinite;}}",
    "css-reduced-ticker"),
 ("cityline","safe-untrusted-text"):(
    "output.innerHTML = 'Station: '+name;","js-literal-only")
}
SCHEMA=frozenset(("site","fault","operation","old","new","sourceSha256"))


def sha(raw:bytes)->str:return hashlib.sha256(raw).hexdigest()


def literal_string(value):
    if type(value) is not str or not 1<=len(value)<=180:
        raise ValueError("E39G12B2_STRING_LENGTH_OR_TYPE")
    if "\n" in value or "\r" in value or "\x00" in value:
        raise ValueError("E39G12B2_LINE_BOUNDARY_OR_CONTROL")
    if any(ord(ch)<32 for ch in value):
        raise ValueError("E39G12B2_CONTROL_CHAR")
    return value


def decode_json(raw:str)->dict:
    if type(raw) is not str or len(raw.encode())>800:
        raise ValueError("E39G12B2_JSON_OVERSIZE")
    def object_hook(pairs):
        obj={}
        for k,v in pairs:
            if k in obj:raise ValueError("E39G12B2_DUPLICATE_UNTRUSTED_FIELD")
            obj[k]=v
        return obj
    obj=json.loads(raw,object_pairs_hook=object_hook)
    if type(obj) is not dict or set(obj)!=SCHEMA:
        raise ValueError("E39G12B2_UNTRUSTED_SCHEMA_WRONG")
    return obj


def check_expression(kind,new):
    literal_string(new)
    if kind=="css-focus-top":
        m=re.fullmatch(r"\.skip:focus\{top:([0-9]{1,2})px;\}",new)
        if m is None or int(m[1])>80:raise ValueError("E39G12B2_FOCUS_NOT_VISIBLE_BOUNDED")
    elif kind=="css-grid-minwidth":
        m=re.fullmatch(r"grid-template-columns:1fr;min-width:(0|[0-9]{1,3}px);",new)
        if m is None:raise ValueError("E39G12B2_MOBILE_GRID_SCOPE_REFUSED")
        if m[1]!="0" and int(m[1][:-2])>390:
            raise ValueError("E39G12B2_MOBILE_MINIMUM_EXCEEDS_VIEWPORT")
    elif kind=="css-reduced-ticker":
        if new not in (
            "@media(prefers-reduced-motion:reduce){.ticker{animation:none;}}",
            "@media(prefers-reduced-motion:reduce){.ticker{animation:none 0s;}}",
        ):raise ValueError("E39G12B2_MOTION_POLICY_NOT_DISABLED")
    elif kind=="js-literal-only":
        if new not in (
            "output.textContent = 'Station: '+name;",
            "output.innerText = 'Station: '+name;",
        ):raise ValueError("E39G12B2_DOM_HTML_SINK_NOT_REMOVED")
    else:raise ValueError("E39G12B2_UNKNOWN_POLICY_KIND")


def validate(proposal:Mapping,broken:bytes)->bytes:
    if type(proposal) is not dict or set(proposal)!=SCHEMA:
        raise ValueError("E39G12B2_PROPOSAL_SCHEMA_REFUSED")
    site=literal_string(proposal["site"])
    fault=literal_string(proposal["fault"])
    key=(site,fault)
    if key not in RULES or site not in corpus.SITES:
        raise ValueError("E39G12B2_UNREVIEWED_SITE_OR_FAULT")
    if proposal["operation"]!="replace_one":
        raise ValueError("E39G12B2_UNREVIEWED_OPERATION")
    if type(broken) is not bytes or not 0<len(broken)<=14000:
        raise ValueError("E39G12B2_UNTRUSTED_SOURCE_BYTES")
    expected=corpus.checked(corpus.HERE/site/"index.html",14000)
    if broken!=expected or proposal["sourceSha256"]!=sha(expected):
        raise ValueError("E39G12B2_SOURCE_NOT_EXACT_CANONICAL_BROKEN")
    old,kind=RULES[key]
    if proposal["old"]!=old or broken.count(old.encode())!=1:
        raise ValueError("E39G12B2_OLD_NOT_UNIQUE_AUTHORIZED_LITERAL")
    new=proposal["new"]
    if old==new:raise ValueError("E39G12B2_NOOP")
    check_expression(kind,new)
    edited=broken.replace(old.encode(),new.encode(),1)
    if edited==broken or edited.count(new.encode())!=1:
        raise ValueError("E39G12B2_NOT_SINGLE_LITERAL_EDIT")
    if len(edited)>14000:
        raise ValueError("E39G12B2_OUTPUT_OVER_BUDGET")
    return edited


def independently_test_all_cases()->dict:
    results={}
    for (site,fault),(old,kind) in RULES.items():
        broken=corpus.checked(corpus.HERE/site/"index.html",14000)
        reviewed={
         "css-focus-top":".skip:focus{top:16px;}",
         "css-grid-minwidth":"grid-template-columns:1fr;min-width:320px;",
         "css-reduced-ticker":"@media(prefers-reduced-motion:reduce){.ticker{animation:none;}}",
         "js-literal-only":"output.textContent = 'Station: '+name;",
        }[kind]
        item={"site":site,"fault":fault,"operation":"replace_one",
              "old":old,"new":reviewed,"sourceSha256":sha(broken)}
        result=validate(item,broken)
        if result==broken:raise ValueError("E39G12B2_POSITIVE_POLICY_NO_CHANGE")
        results[f"{site}:{fault}"]={"sourceSha256":sha(broken),
            "policyAcceptedDryRun":True,"modelCalls":0,
            "modelGeneratedBrowserRepair":False}
    print("E39G12B2_FOUR_TYPED_GATES_POSITIVE_ONLY_NOT_MODEL_REPAIRS",
          json.dumps({"contracts":len(results),"modelCalls":0},sort_keys=True))
    return results


if __name__=="__main__":
    import sys
    if len(sys.argv)!=2 or sys.argv[1]!="selftest":
        raise ValueError("E39G12B2_NO_UNTRUSTED_CLI_FILE_PATH")
    independently_test_all_cases()
