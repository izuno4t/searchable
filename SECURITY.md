# Security Policy

## Supported Versions

Security fixes are issued for the latest minor version of the `1.x` line
on the `main` branch. Older snapshots are not supported; please upgrade
before reporting an issue against a previous build.

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |
| < 1.0   | :x:                |

## Reporting a Vulnerability

Please **do not** open a public GitHub issue for security reports.

Use GitHub's private vulnerability reporting:

1. Open <https://github.com/izuno4t/searchable/security/advisories/new>.
2. Describe the issue, the affected module (`searchable-core`,
   `searchable-ai`, `examples/api`, `examples/mcp`, etc.), and a
   reproduction or proof of concept.
3. Include the commit hash or release you tested against.

You can expect:

- An acknowledgement within **5 business days**.
- A triage decision (accepted / needs-info / out-of-scope) within
  **10 business days** of acknowledgement.
- A fix or mitigation timeline once the report is accepted. Critical
  issues (remote unauthenticated impact on the core library) are
  prioritized over example-application issues.

## Scope

In scope:

- The core library modules: `searchable-core`, `searchable-ai`,
  `searchable-plugins`, `searchable-cli`, `searchable-admin`.
- The reference example applications under `examples/` **when the
  vulnerability is in code shipped from this repository** (not in their
  Spring Boot / third-party dependencies — please report those upstream).

Out of scope:

- Issues that only reproduce when the operator deliberately disables a
  documented safety default (for example, binding the admin UI to a
  non-loopback address without putting authentication in front of it).
- Denial-of-service caused by indexing pathological documents that
  exceed the configured size limits.
- Vulnerabilities in third-party dependencies that have not yet been
  picked up by our scheduled `dependency-check` run — please file those
  with the upstream project.

## Dependency Scanning

Every release runs OWASP `dependency-check` via
`./mvnw -B -Psecurity verify`. Any dependency with a CVSS score of 7.0
or higher fails the build. Reports are archived under
`target/dependency-check-report.html` on each module.

## Disclosure

After a fix is released we publish a GitHub Security Advisory that
includes the affected versions, the fixed version, and credit to the
reporter (unless anonymity is requested).
