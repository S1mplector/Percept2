# Security Policy

## Supported versions

JVN is pre-release, source-first software and does not currently publish an
official supported binary release line.

| Version or branch | Security support |
| --- | --- |
| Current `stable` branch | Supported on a best-effort basis |
| Older commits and tags | Not supported |
| Topic or development branches | Not supported |
| Forks and modified builds | Maintained by their distributors |
| Third-party games and plugins | Maintained by their authors |

Security fixes land on `stable`. Reporters should identify the affected commit,
because behavior can change between source revisions.

## Report a vulnerability privately

Do not open a public issue, pull request, or discussion for a suspected
vulnerability.

The preferred channel, when private vulnerability reporting is enabled, is
GitHub's private vulnerability reporting form:

**https://github.com/S1mplector/Java-Vector-Nexus/security/advisories/new**

If GitHub reports that private vulnerability reporting is unavailable, contact
the repository owner,
[@S1mplector](https://github.com/S1mplector), through a private contact method
published on the GitHub profile. If no private contact method is published,
open a minimal public issue asking the owner to establish a private security
channel. Do not include vulnerability details, affected paths, logs, or proof
of concept material in that issue.

Include:

- the affected commit, tag, or packaged build;
- operating system, architecture, Java version, and launch command;
- affected module, feature, and configuration;
- prerequisites and realistic attacker access;
- minimal reproduction steps or a proof of concept;
- expected and actual security boundary;
- impact on confidentiality, integrity, availability, or user consent;
- logs with credentials, tokens, private game content, and personal data
  removed;
- a suggested mitigation or disclosure timeline, if known.

Use one report per unrelated vulnerability. A clear report can be investigated
faster than a large collection of speculative findings.

## JVN's trust model

JVN is a local engine and authoring environment, not a sandbox.

The following features can intentionally execute code or commands with the
permissions of the JVN process:

- installed plugins and JVM bytecode;
- VNS inline Java and Java integration;
- project-provided runtime integrations;
- configured signing, notarization, and release-profile hooks;
- games or tools the user explicitly builds or runs.

Installing a plugin or running/building an untrusted project is comparable to
running other untrusted local code. Malicious behavior performed by that code
after explicit execution is not, by itself, a JVN sandbox escape.

This does not remove JVN's security responsibilities. A report remains relevant
when JVN crosses a documented boundary unexpectedly, executes project code
during an operation represented as passive, escapes an intended project or
output directory, bypasses an explicit trust decision, or exposes data without
the user's action.

## In-scope examples

Examples that are especially useful to report include:

- path traversal or arbitrary file overwrite during archive extraction,
  packaging, project import, save handling, or asset processing;
- command or code execution that occurs without the documented run, build,
  plugin-install, or release-hook action;
- package or runtime verification bypasses;
- unsafe argument construction in launchers or platform integrations;
- loading a plugin from a location outside the documented discovery scope;
- credentials or private project data written to logs, reports, artifacts, or
  another game's storage;
- cross-game save, settings, or persistent-data access caused by JVN;
- denial of service from a reasonably sized, untrusted project file when the
  affected operation is expected to parse it safely;
- a vulnerable dependency with a demonstrated, reachable JVN attack path.

Reports about passive editor or validation operations should state whether any
project code was intentionally executed before the issue occurred.

## Usually out of scope

The following normally do not qualify without an additional JVN vulnerability:

- behavior of an intentionally installed malicious plugin;
- arbitrary actions in VNS inline Java, release hooks, or other project code the
  user explicitly chose to run;
- unsupported forks, old commits, or locally modified packages;
- social engineering or physical access attacks;
- a dependency advisory without a reachable JVN code path or meaningful impact;
- crashes that require enormous local input and do not cross a trust boundary;
- missing security headers or availability issues on third-party websites;
- reports generated only by an automated scanner without reproduction or
  impact analysis.

When uncertain, report privately. Maintainers would rather triage a
good-faith boundary question than have a potentially valid issue disclosed
publicly.

## Safe research guidelines

Good-faith research should:

- use systems, accounts, projects, and data you own or are authorized to test;
- minimize access and stop when private data or an unexpected third-party
  system is reached;
- avoid persistence, destructive changes, service disruption, or lateral
  movement;
- retain only the evidence needed to explain the issue;
- give maintainers a reasonable opportunity to investigate before disclosure;
- comply with applicable law.

To the extent JVN's maintainers control the response, the project will not
initiate legal action against good-faith research that follows this policy and
avoids privacy violations and harm. This statement cannot authorize testing of
third-party systems or waive rights held by others.

JVN does not currently operate a bug-bounty program. Do not incur costs or
assume payment in expectation of a reward.

## What happens after a report

The project aims to:

1. acknowledge a usable report within seven days;
2. confirm scope and request missing reproduction details;
3. assess severity, affected revisions, and mitigations;
4. prepare a fix and regression coverage in a private advisory when practical;
5. coordinate release notes, attribution, and disclosure with the reporter;
6. publish an advisory when users need information to protect themselves.

These are response goals, not a commercial support SLA. Complex issues,
maintainer availability, and upstream coordination may change the timeline.
Maintainers may ask the reporter to verify a candidate fix without disclosing
unrelated private repository information.

## Coordinated disclosure

Do not publish exploit details, open a fixing pull request, or notify unrelated
parties before a disclosure plan is agreed, unless immediate disclosure is
necessary to prevent greater harm.

The advisory should credit reporters who want attribution. Reporters may remain
anonymous. If a report affects an upstream dependency or downstream
distributor, maintainers will coordinate the minimum necessary information and
avoid exposing the reporter's identity without permission.

Once a fix is public, users should update to the fixed `stable` revision and
rebuild any affected source or packaged artifacts.
