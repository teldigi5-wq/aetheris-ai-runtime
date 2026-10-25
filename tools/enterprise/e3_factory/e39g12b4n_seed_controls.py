#!/usr/bin/env python3
"""Seed only six synthetic B4N reviewed variants, with no external input."""
from __future__ import annotations
import json
import sys
import e39g12b4n_focus_semantic_policy as policy

ALLOWED=(
    ("zero",".skip:focus{top:0px;}"),
    ("six",".skip:focus{top:6px;}"),
    ("eight",".skip:focus{top:8px;}"),
    ("sixteen",".skip:focus{top:16px;}"),
    ("thirtytwo",".skip:focus{top:32px;}"),
    ("malicious",".skip:focus{top:8px;background:url(https://blocked.invalid);}")
)


def seed()->None:
    root=policy.ROOT
    if root.is_symlink() or (root/"incoming").exists() or (root/"results").exists():
        raise ValueError("B4N_SEED_REPLAY_OR_SYMLINK")
    source=policy.original()
    (root/"incoming").mkdir(parents=True,exist_ok=False)
    (root/"results").mkdir(exist_ok=False)
    for case,new in ALLOWED:
        assert case in policy.CASES
        obj={
            "caseId":case,"operation":"replace_one",
            "sourceSha256":policy.sha(source),
            "old":policy.OLD,"new":new
        }
        with (root/"incoming"/(case+".json")).open("x",encoding="utf-8") as f:
            json.dump(obj,f,sort_keys=True);f.write("\n")
    print("B4N_SIX_TRUSTED_OFFLINE_CSS_PROPOSALS_PREPARED")


if __name__=="__main__":
    if len(sys.argv)!=2 or sys.argv[1]!="seed":
        raise ValueError("B4N_FIXED_SEED_COMMAND_ONLY")
    seed()
