# Aetheris Global Extension Research Index

**Current state: 14 external GitHub ecosystem references + zero installations or active plugins.**

This module supports the goal of discovering useful skills, libraries, MCP plugins and automation tools from across the world without blindly copying untrusted code into Aetheris. External repositories are **research candidates**, not guaranteed free-for-every-use, trustworthy, compatible, local, actively maintained or legally approved.

## What is now cataloged

The current curated index, in `sources.json`, includes:

- **Open standards & MCP:** Agent Skills specification, official MCP Registry, MCP reference examples
- **Developer & browser tools:** GitHub MCP Server, Microsoft Playwright MCP
- **Local AI & media:** Ollama, ComfyUI, faster-whisper, Piper TTS
- **Memory and retrieval:** Qdrant, Chroma, LangChain, LlamaIndex
- **Workflow design:** n8n (**fair-code/source-available licensing**, not assumed unrestricted open-source).

The listed `reported_repository_license` reflects current GitHub metadata, **not** legal approval to integrate or redistribute dependencies, packages, voice assets, prompts, checkpoints or model weights. `NOASSERTION` needs explicit license review; GPL and n8n fair-code terms require case-specific analysis. APIs, hosting and model providers may carry additional costs despite an openly available repository.

## Six global discovery hubs (reference-only)

The same catalog also identifies **six non-executing directory references**: [Agent Skills](https://agentskills.io), [MCP Registry](https://registry.modelcontextprotocol.io), [PyPI](https://pypi.org), [npm](https://www.npmjs.com), [Hugging Face models](https://huggingface.co/models) and [Ollama library](https://ollama.com/library). These are links for independently authorized research, not connected tools, automated downloads, subscription guarantees or endorsement of individual entries.

Show the directory list offline:

~~~text
python scripts/search_extension_catalog.py --hubs
~~~

## Three independent approval boundaries

1. **Discover:** record an exact source URL and proposed capability. Never use a public registry ranking as a trust signal.
2. **Quarantine and review:** inspect the exact immutable source commit, author, license and transitive dependencies; audit injected instructions, executable hooks, credentials, filesystem and network permissions, vulnerabilities, model weights, package reproducibility and compatibility. Native MCP stdio servers, npm/pip installs and Comfy custom nodes are **code execution**.
3. **Owner-authorized activation:** a separate explicitly approved implementation must enforce the preexisting Aetheris deterministic owner policy and MCP capability grants at tool call time. This stage has **not** been implemented by this change.

Every entry defaults to `REFERENCE_ONLY`, `UNTRUSTED_EXTERNAL`, `NOT_REVIEWED` license, `source_revision: null`, `installed: false`, `enabled: false`, `execution_enabled: false`, `auto_update: false`, and no automatic fetching, paid providers or network requests.

## Inspection commands (offline, no PC or account required)

The source index itself can be verified using Python stdlib:

~~~text
python scripts/validate_extension_ecosystem.py
python -m unittest discover -s tests -p 'test_extension_ecosystem.py' -v
~~~

To search locally across the 14 curated sources by category, topic or reported GitHub license:

~~~text
python scripts/search_extension_catalog.py --category local-speech
python scripts/search_extension_catalog.py --query GitHub
python scripts/search_extension_catalog.py --reported-license Apache-2.0
~~~

Search returns reference-only metadata and **does not** query the internet, start a service, verify an installed binary or request an API key.

For **a pre-existing local** third-party Agent Skill folder, the offline static quarantine checker is:

~~~text
python scripts/review_external_skill_bundle.py --folder /path/to/existing/skill
~~~

It never downloads, installs, imports or executes a skill, and it never writes a new file. Passing its conservative format check only means `STATIC_CONSTRAINTS_PASSED_NOT_APPROVED`: security/legal/ownership reviews remain mandatory. Suspicious content is flagged for manual review; scanners cannot prove that arbitrary natural-language instructions are safe.

## Relationship to existing services

The Aetheris runtime repository already owns MCP registry, connector, tool-calling, owner policies and incident investigations. This index does **not** create a second orchestrator, bypass those services, add HTTP endpoints or enable external networks. The read-only Agent Skills catalog in `../skills/` is a separate local **TEMPLATE_ONLY** library and likewise is not a runtime plugin loader.

**Physical-PC status: `BLOCKED_PENDING_HARDWARE`.** No GPU or video functionality has been enabled. Do not merge unverified features to protected main or claim that research entries are installed tools.
