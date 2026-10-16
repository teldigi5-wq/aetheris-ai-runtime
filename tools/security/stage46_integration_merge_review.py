#!/usr/bin/env python3
"""Stage 46.20 read-only Git merge-tree integration review.

May create temporary *local Git tree objects*, but NEVER creates a commit,
checks out a merge, changes a ref, writes the worktree, pushes or deploys.
The result is a REVIEW of merge applicability, not a merged integration test
or authorization of a production/owner-PC deployment. Failure to run Git is
not silently treated as a conflict-free merge.
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
from pathlib import Path

HEX40 = re.compile(r"^[a-f0-9]{40}$")
CONFLICT_LINE = re.compile(r"^CONFLICT \([^\n]{1,120}\):[^\n]{0,1024}$")
BASE = {
    "schema_version": 1,
    "integration_simulation_valid": False,
    "merge_conflict_free": False,
    "merge_status": "UNVERIFIED",
    "main_is_ancestor_of_candidate": False,
    "main_sha": None,
    "candidate_sha": None,
    "conflict_message_count": 0,
    "conflict_messages": [],
    "changed_path_count": 0,
    "worktree_unchanged": False,
    "main_branch_ref_unchanged": False,
    "actual_merged_candidate_tested": False,
    "reviewed_integration_candidate_signed": False,
    "physical_owner_pc_pilot_authorized": False,
    "microphone_authorized": False,
    "agent_command_execution_authorized": False,
    "production_authorized": False,
    "decision": "NO_GO",
}


def assess(*, expected_candidate_sha: object, candidate_sha: object,
           main_sha: object, merge_exit: object, merge_stdout: object,
           before_status: object, after_status: object,
           main_sha_after: object, main_ancestor: object,
           changed_paths: object) -> dict[str, object]:
    result = dict(BASE)
    if (not all(isinstance(h, str) and HEX40.fullmatch(h) for h in
                (expected_candidate_sha, candidate_sha, main_sha, main_sha_after))
        or expected_candidate_sha != candidate_sha
        or main_sha != main_sha_after
        or type(merge_exit) is not int or merge_exit not in (0, 1)
        or not isinstance(merge_stdout, str)
        or len(merge_stdout) > 350_000
        or before_status != "" or after_status != ""
        or type(main_ancestor) is not bool
        or not isinstance(changed_paths, list)
        or len(changed_paths) > 12000
        or any(not isinstance(p, str) or not p or len(p) > 2048 or
               "\n" in p or "\r" in p or "\x00" in p
               for p in changed_paths)):
        return result
    # git merge-tree --write-tree prints a merge result tree ID first,
    # including when the simulation has conflicts. No *commit* is created.
    lines = merge_stdout.splitlines()
    if not lines or not HEX40.fullmatch(lines[0]):
        return result
    conflicts = [line for line in lines if line.startswith("CONFLICT ")]
    if len(conflicts) > 500 or any(CONFLICT_LINE.fullmatch(s) is None for s in conflicts):
        return result
    if merge_exit == 0 and conflicts:
        return result

    result["integration_simulation_valid"] = True
    result["main_sha"] = main_sha
    result["candidate_sha"] = candidate_sha
    result["main_is_ancestor_of_candidate"] = main_ancestor
    result["merge_conflict_free"] = merge_exit == 0
    result["merge_status"] = (
        "CONFLICT_FREE_LOCAL_TREE_SIMULATION"
        if merge_exit == 0 else "CONFLICTS_OBSERVED_LOCAL_TREE_SIMULATION"
    )
    result["conflict_message_count"] = len(conflicts)
    # File paths are taken from the Git repository only, and never interpreted
    # as commands or accessed. Limit logs to useful context.
    result["conflict_messages"] = conflicts[:25]
    result["changed_path_count"] = len(changed_paths)
    result["worktree_unchanged"] = True
    result["main_branch_ref_unchanged"] = True
    return result


def _git(*args: str, ok: tuple[int, ...] = (0,)) -> tuple[int, str]:
    # Fixed git subcommands only. No shell expansion, user command, hooks,
    # ref mutation or arbitrary executable invocation.
    process = subprocess.run(
        ["git", *args], capture_output=True, text=True,
        timeout=65, check=False, encoding="utf-8", errors="replace",
    )
    if process.returncode not in ok:
        raise ValueError("UNEXPECTED_GIT_COMMAND_RESULT")
    if len(process.stdout) > 350_000:
        raise ValueError("GIT_OUTPUT_TOO_LARGE")
    return process.returncode, process.stdout


def review_checkout(expected_head: str) -> dict[str, object]:
    invalid = dict(BASE)
    try:
        _, head = _git("rev-parse", "--verify", "HEAD")
        _, main = _git("rev-parse", "--verify", "refs/remotes/origin/main")
        _, before = _git("status", "--porcelain=v1", "--untracked-files=no")
        ancestor_code, _ = _git(
            "merge-base", "--is-ancestor",
            "refs/remotes/origin/main", "HEAD", ok=(0, 1),
        )
        _, files = _git(
            "diff", "--name-only", "refs/remotes/origin/main...HEAD",
        )
        merge_code, merge_output = _git(
            "merge-tree", "--write-tree", "--messages",
            "refs/remotes/origin/main", "HEAD", ok=(0, 1),
        )
        _, after = _git("status", "--porcelain=v1", "--untracked-files=no")
        _, main_after = _git("rev-parse", "--verify", "refs/remotes/origin/main")
        return assess(
            expected_candidate_sha=expected_head,
            candidate_sha=head.strip(),
            main_sha=main.strip(),
            merge_exit=merge_code,
            merge_stdout=merge_output,
            before_status=before,
            after_status=after,
            main_sha_after=main_after.strip(),
            main_ancestor=ancestor_code == 0,
            changed_paths=files.splitlines(),
        )
    except (OSError, ValueError, subprocess.TimeoutExpired):
        return invalid


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--expected-head", required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    report = review_checkout(args.expected_head)
    printed = json.dumps(report, sort_keys=True, indent=2) + "\n"
    print(printed, end="")
    if args.output:
        args.output.write_text(printed, encoding="utf-8")
    # A legitimate conflicting dry-run is a valid review result, but is
    # NEVER described as ready. Both conflict and clean outcomes are NO_GO.
    return 0 if report["integration_simulation_valid"] and report["decision"] == "NO_GO" else 1


if __name__ == "__main__":
    raise SystemExit(main())
