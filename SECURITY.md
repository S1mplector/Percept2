# Security policy

## Supported versions

JVN is pre-release, source-distributed software. Security fixes are made on the current `stable` branch. Older commits, development branches, forks, and third-party plugins are not maintained by the JVN project.

## Reporting a vulnerability

Do not open a public issue for a suspected vulnerability. Use GitHub's private vulnerability reporting feature for this repository when available. Otherwise contact the repository owner privately through the contact method listed on the GitHub profile.

Include:

- affected commit or version;
- platform and Java version;
- component and configuration;
- reproduction steps or a minimal proof of concept;
- realistic impact and required attacker access;
- suggested mitigation, if known.

Avoid accessing data you do not own, disrupting services, publishing exploit details before coordination, or including secrets and personal data in a report.

## Scope notes

Plugins execute JVM bytecode in the application process and are not sandboxed. Installing an untrusted plugin is equivalent to running untrusted local code; this is documented behavior, not a sandbox escape. Reports remain in scope when a trusted plugin or project can unexpectedly cross a documented security boundary, when package verification is bypassed, or when JVN exposes data without the user's action.

Project files and scripts should be treated as untrusted input. Vulnerabilities involving path traversal, unsafe extraction, command execution, credential exposure, or malicious packaged content are especially important.

## Disclosure

Maintainers will acknowledge a usable report when possible, investigate it, coordinate a fix and advisory, and credit the reporter unless anonymity is requested. Response timing is best-effort because the project does not currently offer a commercial support SLA.
