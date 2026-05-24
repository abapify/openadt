# Security Policy

OpenADT is an open-source Java CLI that bridges SAP ABAP Development Tools (ADT) access on Windows, Linux, and macOS. This policy covers vulnerabilities in **OpenADT project code and its delivery pipeline**, not in SAP vendor software or credentials stored in your local environment.

## Supported Versions

Security fixes are published for supported release lines. Install the latest patch on your line when available.

| Version    | Supported   | Notes                                                                                                                                       |
| ---------- | ----------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| `v1.1.x`   | Yes         | Current release line ([latest: v1.1.0](https://github.com/abapify/openadt/releases/tag/v1.1.0))                                             |
| `v1.0.x`   | Best effort | Last release: [v1.0.11](https://github.com/abapify/openadt/releases/tag/v1.0.11); critical fixes may be backported at maintainer discretion |
| `< v1.0.0` | No          | Upgrade to a supported release                                                                                                              |

Development builds from `main` or open pull requests are not supported release lines. Reproduce issues against the latest tagged release when possible.

## Scope

**In scope**

- The `openadt` CLI (`setup`, `proxy`, `fetch`, and related commands)
- Localhost proxy behavior, authentication modes, header handling, and credential redaction
- Configuration parsing and validation in OpenADT
- Packaging (winget, Homebrew) and install/bootstrap scripts maintained in this repository
- GitHub Actions workflows and release automation in this repository

**Out of scope**

- SAP JCo, SAP CryptoLib (`sapcrypto`), SAP Secure Login Client, or other SAP binaries you install separately — report those to [SAP Product Security Response Center](https://www.sap.com/about/trust-center/security/incident-management.html)
- Vulnerabilities in SAP NetWeaver, ABAP, or ADT server-side behavior
- Leaked SAP credentials, tickets, or landscape details found in a user's `~/.openadt/config.toml`, environment variables, or local Secure Login / SECUDIR material — rotate credentials with your SAP administrator; do not file a public or private OpenADT advisory for config you control locally
- IDE, Eclipse ADT plugin, or third-party tool issues unrelated to OpenADT code
- Misconfiguration of localhost exposure (for example, binding the proxy to `0.0.0.0` without `--local-auth`) when OpenADT documented the safer defaults

## Reporting a Vulnerability

**Do not** report security vulnerabilities through public GitHub issues, discussions, or pull requests.

### Preferred: GitHub private vulnerability reporting

Use [GitHub private vulnerability reporting](https://github.com/abapify/openadt/security/advisories/new) for this repository. Reports stay private until maintainers publish a coordinated advisory.

### What to include

Please provide as much of the following as you can:

- Affected version, tag, or commit SHA (prefer a released tag such as `v1.1.0`)
- Description of the issue and why it is security-sensitive
- Steps to reproduce or a proof of concept
- Potential impact (confidentiality, integrity, availability)
- Any suggested mitigations or fixes
- Relevant logs or screenshots with **secrets redacted** (no SAP passwords, cookies, tickets, SNC tokens, or authorization headers)

## Response Expectations

OpenADT is maintained on a **best-effort** basis by volunteers. We aim to:

- Acknowledge receipt within **5 business days**
- Provide an initial triage assessment within **10 business days** when possible
- Keep reporters informed of significant status changes

Timelines may slip for complex issues or holiday periods. We appreciate responsible disclosure and will coordinate publication timing when a fix is ready.

If a report is accepted, we will work on a remediation and may publish a [GitHub Security Advisory](https://github.com/abapify/openadt/security/advisories) with credit to the reporter when desired. Declined reports will include a brief explanation when feasible.

## Safe Harbor

We support good-faith security research on in-scope components. Do not access systems or SAP landscapes you do not own or lack permission to test. Do not degrade service, exfiltrate real user data, or publicly disclose an issue before we have had a reasonable chance to address it.
