# MCP outbound endpoint hardening — draft

This security slice applies a shared endpoint policy to MCP server discovery and tool calls.

- Local MCP: HTTP/HTTPS and exact configured host only (`AETHERIS_MCP_ALLOWED_LOCAL_HOSTS`).
- Remote MCP: HTTPS, exact explicit host allowlist `AETHERIS_MCP_ALLOWED_REMOTE_HOSTS` (empty by default, so all remote MCP disabled).
- No URL userinfo, fragments, invalid ports, local-domain suffixes or IP-literal remote destinations.
- Redirects disabled for MCP HTTP requests through a JDK HttpClient request factory.
- User-facing network endpoints, provider-generated URLs, and tools other than MCP are **not** protected by this particular policy.

A remote MCP integration which previously worked with only HTTPS now deliberately requires an operator-reviewed exact DNS name. This is a compatibility-breaking security cutover; inventory and approve each endpoint before rollout.

**Remaining risks:** DNS rebinding, DNS answers resolving to private/link-local IPs, proxies, service identity, transport and network egress enforcement, other connectors, service-to-service auth. A source-level exact-host allowlist is not a complete SSRF defense. Enforce network egress/firewall and DNS-pinned outbound proxies before production use. No production or owner-PC use is certified.
