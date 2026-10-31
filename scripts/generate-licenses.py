#!/usr/bin/env python3
"""Generate open-source license metadata for Zip_Bug from REAL dependencies.

Sources of truth (parsed, never hand-listed):
  * app/build.gradle.kts        -> the app's own implementation/kapt/test deps
  * build.gradle.kts (root)     -> Kotlin plugin version (kotlin-stdlib)
  * AppScaffolder.kt            -> the fixed core deps of every generated app

License metadata (display name / author / license / URL) is curated in
METADATA below for exactly those coordinates. Any parsed dependency that is
missing from METADATA aborts generation (and fails --check), so a new
dependency can never silently ship without license review.

Outputs:
  * app/src/main/assets/open_source_licenses.json
  * app/src/main/assets/third_party_notices.txt
  * THIRD_PARTY_NOTICES.txt            (repository root)

Usage:
  python3 scripts/generate-licenses.py           # regenerate outputs
  python3 scripts/generate-licenses.py --check   # fail if outputs are stale
                                                 # or metadata is missing
"""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
APP_GRADLE = ROOT / "app" / "build.gradle.kts"
ROOT_GRADLE = ROOT / "build.gradle.kts"
SCAFFOLDER = (
    ROOT
    / "app/src/main/java/com/zipbug/base/creator/AppScaffolder.kt"
)
JSON_OUT = ROOT / "app/src/main/assets/open_source_licenses.json"
NOTICES_ASSET = ROOT / "app/src/main/assets/third_party_notices.txt"
NOTICES_ROOT = ROOT / "THIRD_PARTY_NOTICES.txt"

APACHE = "Apache License 2.0"
APACHE_URL = "https://www.apache.org/licenses/LICENSE-2.0"
AOSP = "The Android Open Source Project"

# coordinate (group:artifact) -> curated metadata.
# "category" drives the UI filters: apache / mit / bsd / google_sdk / other.
METADATA = {
    "androidx.core:core-ktx": {
        "displayName": "Core KTX",
        "author": AOSP,
        "license": APACHE,
        "licenseUrl": APACHE_URL,
        "category": "apache",
    },
    "androidx.appcompat:appcompat": {
        "displayName": "AppCompat",
        "author": AOSP,
        "license": APACHE,
        "licenseUrl": APACHE_URL,
        "category": "apache",
    },
    "androidx.activity:activity-ktx": {
        "displayName": "Activity KTX",
        "author": AOSP,
        "license": APACHE,
        "licenseUrl": APACHE_URL,
        "category": "apache",
    },
    "androidx.fragment:fragment-ktx": {
        "displayName": "Fragment KTX",
        "author": AOSP,
        "license": APACHE,
        "licenseUrl": APACHE_URL,
        "category": "apache",
    },
    "androidx.lifecycle:lifecycle-runtime-ktx": {
        "displayName": "Lifecycle Runtime KTX",
        "author": AOSP,
        "license": APACHE,
        "licenseUrl": APACHE_URL,
        "category": "apache",
    },
    "androidx.lifecycle:lifecycle-viewmodel-ktx": {
        "displayName": "Lifecycle ViewModel KTX",
        "author": AOSP,
        "license": APACHE,
        "licenseUrl": APACHE_URL,
        "category": "apache",
    },
    "com.google.android.material:material": {
        "displayName": "Material Components for Android",
        "author": "Google LLC",
        "license": APACHE,
        "licenseUrl": APACHE_URL,
        "category": "apache",
    },
    "androidx.room:room-runtime": {
        "displayName": "Room Runtime",
        "author": AOSP,
        "license": APACHE,
        "licenseUrl": APACHE_URL,
        "category": "apache",
    },
    "androidx.room:room-ktx": {
        "displayName": "Room KTX",
        "author": AOSP,
        "license": APACHE,
        "licenseUrl": APACHE_URL,
        "category": "apache",
    },
    "androidx.room:room-compiler": {
        "displayName": "Room Compiler",
        "author": AOSP,
        "license": APACHE,
        "licenseUrl": APACHE_URL,
        "category": "apache",
        "notes": "Build-time annotation processor (kapt); not shipped in the APK.",
    },
    "com.squareup.okhttp3:okhttp": {
        "displayName": "OkHttp",
        "author": "Square, Inc.",
        "license": APACHE,
        "licenseUrl": APACHE_URL,
        "category": "apache",
    },
    "com.github.bumptech.glide:glide": {
        "displayName": "Glide",
        "author": "Bumptech",
        "license": "BSD 3-Clause License",
        "licenseUrl": "https://opensource.org/licenses/BSD-3-Clause",
        "category": "bsd",
    },
    "org.jetbrains.kotlinx:kotlinx-coroutines-android": {
        "displayName": "Kotlinx Coroutines Android",
        "author": "JetBrains s.r.o.",
        "license": APACHE,
        "licenseUrl": APACHE_URL,
        "category": "apache",
    },
    "org.jetbrains.kotlin:kotlin-stdlib": {
        "displayName": "Kotlin Standard Library",
        "author": "JetBrains s.r.o.",
        "license": APACHE,
        "licenseUrl": APACHE_URL,
        "category": "apache",
    },
    "junit:junit": {
        "displayName": "JUnit 4",
        "author": "JUnit Team",
        "license": "Eclipse Public License 1.0",
        "licenseUrl": "https://www.eclipse.org/legal/epl-v10.html",
        "category": "other",
        "notes": "Unit tests only; not included in the APK.",
    },
    "org.json:json": {
        "displayName": "JSON in Java (org.json)",
        "author": "JSON.org",
        "license": "The JSON License",
        "licenseUrl": "https://www.json.org/license.html",
        "category": "other",
        "notes": "Unit tests only; not included in the APK.",
    },
}

NOTICE_BY_LICENSE = {
    APACHE: (
        "Licensed under the Apache License, Version 2.0. You may obtain a "
        "copy of the License at the license URL above. Unless required by "
        "applicable law or agreed to in writing, software distributed under "
        "the License is distributed on an \"AS IS\" BASIS, WITHOUT "
        "WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied."
    ),
    "BSD 3-Clause License": (
        "Redistribution and use in source and binary forms, with or without "
        "modification, are permitted provided that the copyright notice, "
        "conditions and disclaimer in the license text are retained. See "
        "the license URL above for the full text."
    ),
    "Eclipse Public License 1.0": (
        "Distributed under the Eclipse Public License 1.0. See the license "
        "URL above for the full text."
    ),
    "The JSON License": (
        "Permission is granted to use, copy, modify and distribute this "
        "software, subject to the terms at the license URL above, including "
        "retention of the copyright notice."
    ),
}

COORD_RE = re.compile(
    r"([a-zA-Z][a-zA-Z0-9_.\-]*):([a-zA-Z0-9_.\-]+):([0-9][a-zA-Z0-9_.+\-]*)"
)


def parse_app_dependencies():
    """Return {coordinate: version} from app/build.gradle.kts."""
    text = APP_GRADLE.read_text(encoding="utf-8")
    block = text[text.index("dependencies {"):]
    block = block[: block.index("\n}")]
    deps = {}
    for m in re.finditer(
        r"(?:implementation|kapt|testImplementation)\(\"([^\"]+)\"\)", block
    ):
        coord = m.group(1)
        cm = COORD_RE.fullmatch(coord)
        if not cm:
            raise SystemExit(f"Unparseable dependency coordinate: {coord}")
        deps[f"{cm.group(1)}:{cm.group(2)}"] = cm.group(3)
    return deps


def parse_kotlin_version():
    text = ROOT_GRADLE.read_text(encoding="utf-8")
    m = re.search(
        r'id\("org\.jetbrains\.kotlin\.android"\)\s+version\s+"([^"]+)"', text
    )
    if not m:
        raise SystemExit("Kotlin plugin version not found in build.gradle.kts")
    return m.group(1)


def parse_generated_core():
    """Coordinates hardcoded in AppScaffolder's generated app/build.gradle."""
    text = SCAFFOLDER.read_text(encoding="utf-8")
    coords = {}
    for line in text.splitlines():
        if "implementation" in line or "dependency ==" in line:
            for m in COORD_RE.finditer(line):
                coords[f"{m.group(1)}:{m.group(2)}"] = m.group(3)
    return coords


def build_entries():
    app_deps = parse_app_dependencies()
    kotlin_version = parse_kotlin_version()
    app_deps["org.jetbrains.kotlin:kotlin-stdlib"] = kotlin_version
    generated = parse_generated_core()
    generated["org.jetbrains.kotlin:kotlin-stdlib"] = kotlin_version

    unknown = [c for c in app_deps if c not in METADATA]
    unknown += [c for c in generated if c not in METADATA]
    if unknown:
        raise SystemExit(
            "License metadata missing for: "
            + ", ".join(sorted(set(unknown)))
            + " — add curated metadata to scripts/generate-licenses.py "
            "before shipping this dependency."
        )

    entries = []
    for coord in sorted(app_deps):
        meta = METADATA[coord]
        scope = ["app"]
        if coord in generated:
            if generated[coord] != app_deps[coord]:
                raise SystemExit(
                    f"Version mismatch for {coord}: app uses "
                    f"{app_deps[coord]}, generated apps use "
                    f"{generated[coord]} — one entry cannot describe both."
                )
            scope.append("generated_apps")
        entry = {
            "name": coord,
            "displayName": meta["displayName"],
            "version": app_deps[coord],
            "author": meta["author"],
            "license": meta["license"],
            "licenseUrl": meta["licenseUrl"],
            "category": meta["category"],
            "scope": scope,
        }
        if "notes" in meta:
            entry["notes"] = meta["notes"]
        entries.append(entry)
    return entries


def build_notices(entries):
    lines = [
        "THIRD-PARTY NOTICES — Zip_Bug",
        "",
        "Zip_Bug is built with open source software. Generated apps include",
        "only the libraries required by the features selected for that app;",
        "the scope line under each entry states where it is used.",
        "",
        f"Libraries listed: {len(entries)}",
        "",
    ]
    for e in entries:
        scope = " + ".join(
            "App" if s == "app" else "Generated apps" for s in e["scope"]
        )
        lines += [
            "=" * 72,
            f"{e['displayName']} ({e['name']}:{e['version']})",
            f"Author: {e['author']}",
            f"License: {e['license']}",
            f"License URL: {e['licenseUrl']}",
            f"Scope: {scope}",
        ]
        if "notes" in e:
            lines.append(f"Notes: {e['notes']}")
        notice = NOTICE_BY_LICENSE.get(e["license"])
        if notice:
            lines += ["", notice]
        lines.append("")
    return "\n".join(lines).rstrip() + "\n"


def main():
    check = "--check" in sys.argv[1:]
    entries = build_entries()
    json_text = json.dumps(entries, indent=2, ensure_ascii=False) + "\n"
    notices_text = build_notices(entries)
    outputs = {
        JSON_OUT: json_text,
        NOTICES_ASSET: notices_text,
        NOTICES_ROOT: notices_text,
    }
    if check:
        stale = [
            str(p.relative_to(ROOT))
            for p, content in outputs.items()
            if not p.exists() or p.read_text(encoding="utf-8") != content
        ]
        if stale:
            print("License metadata STALE: " + ", ".join(stale))
            print("Run: python3 scripts/generate-licenses.py")
            return 1
        print(f"License metadata validation: OK ({len(entries)} libraries)")
        return 0
    for path, content in outputs.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        print(f"Wrote {path.relative_to(ROOT)}")
    unknown = sum(
        1 for e in entries if e["license"] == "License review required"
    )
    print(f"Libraries: {len(entries)}  Unknown: {unknown}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
