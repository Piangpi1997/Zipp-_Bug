# Zip_Bug // Antigravity Studio — RC1 Status Report

**Repository**: `https://github.com/Piangpi1997/Zipp-_Bug`  
**Target Branch**: `feat/antigravity-studio-v2`  
**Status Date**: 2026-10-11  
**Truth Rule**: no simulated build/runtime success.

## Current classification

`BUILD VERIFIED / PHYSICAL PROOF PENDING`

The former RC snapshot at `cb56740` is historical. The branch has advanced beyond it.

- **Latest code-bearing verified commit**: `820e779a4ae9f83399055b347bea8c6de4a0d90b`
- **Current documentation sync commit**: `7a84cca74f99a6bf174722b8aa18568c766fe4a1`
- The documentation-only commit has fresh CI queued at the time of this update.
- The verified code baseline `820e779` passed Architecture Check and Android CI.

## Verified build evidence for 820e779

| Scope | Status | Evidence |
| :--- | :--- | :--- |
| Architecture Check | PASS | GitHub Actions push run `37821761253` |
| Unit tests | PASS | Android CI push run `37821761312` |
| Debug APK build | PASS | Android CI push run `37821761312` |
| APK signature verification | PASS | `apksigner verify --verbose`: APK Signature Scheme v2 = true |
| Artifact upload | PASS | Android CI artifact `Zip_Bug-debug` |
| Physical Android runtime | PENDING | Requires user device proof |
| Termux controlled runtime/build | PENDING | Requires on-device stage/output proof |
| OpenRouter physical response | PENDING | Requires real provider response from phone |

## Current CI APK fingerprint

The APK below is the GitHub CI artifact for verified code commit `820e779`.

- **APK name**: `app-debug.apk`
- **APK size**: `7,060,645` bytes
- **APK SHA-256**: `5416aa5548b457a3ebf0e020c70b53724abca8735f01b64857b20f87cfdcd8ba`
- **Signature verification**: APK Signature Scheme v2 PASS
- **Artifact ZIP digest reported by GitHub**: `sha256:1a59b5b91654232a3e19c941fa5857a9e4402209207a59ddc1f928c89fdef7b8`

The artifact ZIP digest is not the APK SHA-256.

### Debug signer warning

Do not treat a Windows debug signer fingerprint as the permanent release signer for the CI APK. Windows, GitHub Actions, and Termux debug builds may use different debug certificates. Stable cross-environment update signing remains a separate release-engineering task.

## Functional changes included after cb56740

The verified code baseline now also includes:

- grouped Home dashboard: Build / Design / Media / Tools;
- centralized slash command registry and searchable `/help`;
- canonical `/appcreator` plus backward-compatible `/apcreator` alias;
- factual unavailable state for reserved `/aizipper`;
- hardened mini-app ZIP path validation;
- project-type-aware web runtime vs Android build actions;
- source ZIP reopen/validation before success;
- source ZIP Open/Share actions;
- controlled debug APK workflow with Validation / Dependencies / Build / Packaging stages;
- timeout and cancel-token support;
- APK size/SHA/export verification;
- release-signing boundary that does not create/overwrite credentials;
- artifact Open/Share actions only after proof is complete;
- block on misleading Android builds from app-private storage that Termux cannot access.

## Physical-device proof still required

Follow `docs/DEVICE_TEST.md` from the latest branch HEAD.

Physical verification requires:

1. real Device & Engine Proof log;
2. controlled Termux debug build with stage records, exit code 0, and artifact proof;
3. real OpenRouter response `ZIP_BUG_OPENROUTER_OK`;
4. for App Creator APK claims, transfer/export to a Termux-accessible project root before invoking the controlled builder.

Do not use the older instruction that App Creator can directly dispatch a Gradle build from Zip_Bug app-private storage. That path is intentionally blocked because it is not a valid Termux workspace.

## Promotion rule

RC1 may be promoted to `DEVICE VERIFIED` only after the physical evidence above is returned and reviewed. CI/build verification alone is not sufficient.
