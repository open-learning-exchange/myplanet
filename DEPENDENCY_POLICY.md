# Dependency Update Policy

This document outlines the policy and procedures for managing dependencies in the myPlanet Android project.

## Schedule

- **Automated Checks**: Daily
- **Report Generation**: Weekly (Monday morning)

## Grouping Strategy

To reduce noise, related dependencies are grouped into single Pull Requests:
- **AndroidX**: All `androidx.*` libraries.
- **Kotlin**: Kotlin standard libraries and tools (`org.jetbrains.kotlin.*`, `com.google.devtools.ksp`).
- **Hilt**: Dependency injection libraries (`com.google.dagger.*`, `androidx.hilt.*`).

## Automated Workflows

### 1. Build and Test
All dependency updates trigger a validation workflow (`dependabot-checks.yml`) that runs:
- `assembleDebug`
- `lintDebug`

### 2. Security Scanning
A weekly security scan (`security-scan.yml`) runs using Trivy to detect vulnerabilities in dependencies. High and Critical severity issues will be reported.

### 3. Automated Merging
Patch version updates (e.g., `1.0.0` -> `1.0.1`) are automatically merged if:
- They pass all CI checks (Build & Lint).
- They are classified as `semver-patch` by Dependabot.

## Manual Review Process

- **Minor/Major Updates**: Require manual review and approval. Developers should verify:
  - Changelogs for breaking changes.
  - Functionality related to the updated library.
- **Security Vulnerabilities**: Must be addressed immediately.

## Reporting

A weekly report lists available dependency updates and is uploaded as a build artifact in the `Weekly Dependency Report` workflow.
