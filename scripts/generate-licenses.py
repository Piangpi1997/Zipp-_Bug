#!/usr/bin/env python3
"""Generate open-source license metadata for Zip_Bug — v2 (full graph).

Inputs (committed, produced by scripts/resolve-license-graph.py):
  * scripts/license-graph.json         resolved modules ("g:a:v") for the
                                       app classpath, the generated-app
                                       template classpath, and the kept
                                       build/test-only extras
  * scripts/license-pom-metadata.json  per-module name/author/license as
                                       published in the module's own POM

License names from POMs are canonicalized (Apache variants, BSD
variants, EPL, MIT, ...). Google SDK groups are always labelled with the
Android Software Development Kit License, never Apache. Anything that
cannot be mapped from evidence is emitted as "License review required".

Outputs:
  * app/src/main/assets/open_source_licenses.json
  * app/src/main/assets/third_party_notices.txt
  * THIRD_PARTY_NOTICES.txt            (repository root)
  * the GENERATED-KNOWN block inside
    app/src/main/java/com/zipbug/base/licenses/GeneratedLicenseManifest.kt

Usage:
  python3 scripts/generate-licenses.py           # regenerate outputs
  python3 scripts/generate-licenses.py --check   # fail if outputs stale
"""

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GRAPH = ROOT / "scripts/license-graph.json"
POM_META = ROOT / "scripts/license-pom-metadata.json"
JSON_OUT = ROOT / "app/src/main/assets/open_source_licenses.json"
NOTICES_ASSET = ROOT / "app/src/main/assets/third_party_notices.txt"
NOTICES_ROOT = ROOT / "THIRD_PARTY_NOTICES.txt"
KOTLIN_MANIFEST = (
    ROOT
    / "app/src/main/java/com/zipbug/base/licenses/GeneratedLicenseManifest.kt"
)

APACHE = "Apache License 2.0"
APACHE_URL = "https://www.apache.org/licenses/LICENSE-2.0"
BSD2 = "BSD 2-Clause License"
BSD2_URL = "https://opensource.org/licenses/BSD-2-Clause"
BSD3 = "BSD 3-Clause License"
BSD3_URL = "https://opensource.org/licenses/BSD-3-Clause"
REVIEW_REQUIRED = "License review required"

# Groups whose artifacts are governed by Google's SDK terms even when a
# POM declares something else. (None are in Zip_Bug's current graph; the
# rule exists so a future generated-app feature can never mislabel them.)
GOOGLE_SDK_PREFIXES = (
    "com.google.android.gms:",
    "com.google.firebase:",
    "com.google.android.play:",
    "com.google.android.ump:",
    "com.google.ads:",
)
ANDROID_SDK_LICENSE = "Android Software Development Kit License"
ANDROID_SDK_URL = "https://developer.android.com/studio/terms"

# Curated corrections where a published POM is known to be wrong or
# incomplete. Each entry must be defensible from the artifact itself.
OVERRIDES = {
    # The org.json POM declares "Public Domain", but the artifact ships
    # the JSON License text (see its LICENSE file); v1 decision kept.
    "org.json:json": {
        "license": "The JSON License",
        "licenseUrl": "https://www.json.org/license.html",
        "category": "other",
    },
    # The POM carries no organization/developer; Error Prone is a
    # Google project.
    "com.google.errorprone:error_prone_annotations": {
        "author": "Google LLC",
    },
    # FFmpegKit was removed from Maven Central when the project was
    # retired; the AAR is fetched from Google's read-only mirror of
    # Central with a pinned SHA-256 (see app/build.gradle.kts).
    "com.arthenica:ffmpeg-kit-full": {
        "displayName": "FFmpegKit (full) — in-app FFmpeg engine",
    },
}

EXTRA_NOTES = {
    "androidx.room:room-compiler":
        "Build-time annotation processor (kapt); not shipped in the APK.",
    "junit:junit": "Unit tests only; not included in the APK.",
    "org.hamcrest:hamcrest-core": "Unit tests only; not included in the APK.",
    "org.json:json": "Unit tests only; not included in the APK.",
    "com.arthenica:ffmpeg-kit-full": (
        "In-app FFmpeg engine; native libraries bundled in the APK "
        "(arm64-v8a). The FFmpeg libraries are LGPL-2.1+ and the "
        "FFmpegKit wrapper is LGPL-3.0. This 'full' build enables "
        "dav1d, fontconfig, freetype, libass, libvpx, opus and other "
        "LGPL libraries; it includes no GPL libraries (no x264/x265)."
    ),
}

NOTICE_BY_LICENSE = {
    APACHE: (
        "Licensed under the Apache License, Version 2.0. You may obtain a "
        "copy of the License at the license URL above. Unless required by "
        "applicable law or agreed to in writing, software distributed under "
        "the License is distributed on an \"AS IS\" BASIS, WITHOUT "
        "WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied."
    ),
    BSD2: (
        "Redistribution and use in source and binary forms, with or without "
        "modification, are permitted provided that the copyright notice and "
        "disclaimer in the license text are retained. See the license URL "
        "above for the full text."
    ),
    BSD3: (
        "Redistribution and use in source and binary forms, with or without "
        "modification, are permitted provided that the copyright notice, "
        "conditions and disclaimer in the license text are retained. See "
        "the license URL above for the full text."
    ),
    "Eclipse Public License 1.0": (
        "Distributed under the Eclipse Public License 1.0. See the license "
        "URL above for the full text."
    ),
    "LGPL 3.0": (
        "Distributed under the GNU Lesser General Public License "
        "v3.0. See the license URL above for the full text."
    ),
    "The JSON License": (
        "Permission is granted to use, copy, modify and distribute this "
        "software, subject to the terms at the license URL above, including "
        "retention of the copyright notice."
    ),
}

GENERIC_NOTICE = (
    "Distributed under the license named above. See the license URL "
    "above for the full text."
)


def canonical_license(ga, pom_license_name, pom_license_url):
    """Map a POM-declared license to (name, url, category) or review."""
    if any(ga.startswith(p) for p in GOOGLE_SDK_PREFIXES):
        return ANDROID_SDK_LICENSE, ANDROID_SDK_URL, "google_sdk"
    name = (pom_license_name or "").strip()
    low = name.lower()
    if "apache" in low:
        return APACHE, APACHE_URL, "apache"
    if "simplified bsd" in low or "bsd 2" in low or "2-clause" in low:
        return BSD2, BSD2_URL, "bsd"
    if "new bsd" in low or "bsd 3" in low or "3-clause" in low:
        return BSD3, BSD3_URL, "bsd"
    if low == "bsd license" or low == "the bsd license":
        # Ambiguous BSD variant in the POM: use the URL to disambiguate
        # when possible, otherwise require a human review.
        return REVIEW_REQUIRED, pom_license_url or "", "other"
    if "mit" in low:
        return "MIT License", "https://opensource.org/licenses/MIT", "mit"
    if "eclipse public license" in low and "1.0" in low:
        return (
            "Eclipse Public License 1.0",
            "https://www.eclipse.org/legal/epl-v10.html",
            "other",
        )
    if "eclipse public license" in low and "2.0" in low:
        return (
            "Eclipse Public License 2.0",
            "https://www.eclipse.org/legal/epl-2.0/",
            "other",
        )
    if "mozilla public license" in low:
        return (
            "Mozilla Public License 2.0",
            "https://www.mozilla.org/en-US/MPL/2.0/",
            "other",
        )
    if "lesser general public license" in low or "lgpl" in low:
        if "3" in low:
            return (
                "LGPL 3.0",
                "https://www.gnu.org/licenses/lgpl-3.0.txt",
                "other",
            )
        if "2.1" in low:
            return (
                "LGPL 2.1",
                "https://www.gnu.org/licenses/lgpl-2.1.txt",
                "other",
            )
        return REVIEW_REQUIRED, pom_license_url or "", "other"
    if "open font license" in low or "sil open font" in low:
        return (
            "SIL Open Font License 1.1",
            "https://opensource.org/licenses/OFL-1.1",
            "other",
        )
    if "android software development kit" in low:
        return ANDROID_SDK_LICENSE, ANDROID_SDK_URL, "google_sdk"
    if "play core" in low and "terms of service" in low:
        return (
            "Play Core Software Development Kit Terms of Service",
            ANDROID_SDK_URL,
            "google_sdk",
        )
    if not name:
        return REVIEW_REQUIRED, "", "other"
    return REVIEW_REQUIRED, pom_license_url or "", "other"


def split_coord(coord):
    ga, _, version = coord.rpartition(":")
    return ga, version


def build_entries():
    graph = json.loads(GRAPH.read_text(encoding="utf-8"))
    pom_meta = json.loads(POM_META.read_text(encoding="utf-8"))

    app = dict(split_coord(c) for c in graph["app"])
    generated = dict(split_coord(c) for c in graph["generatedApps"])
    extras = dict(split_coord(c) for c in graph["extras"])

    entries = []
    all_gas = sorted(set(app) | set(generated) | set(extras))
    for ga in all_gas:
        # Metadata comes from the app's resolved version when present,
        # else the generated template's, else the extra's own POM.
        meta_coord = None
        for source in (app, generated, extras):
            if ga in source:
                meta_coord = f"{ga}:{source[ga]}"
                break
        meta = pom_meta.get(meta_coord)
        if meta is None:
            raise SystemExit(
                f"POM metadata missing for {meta_coord}; re-run "
                "scripts/resolve-license-graph.py"
            )
        display_name = meta.get("name") or ga.split(":")[1]
        author = meta.get("author") or ga.split(":")[0]
        license_name, license_url, category = canonical_license(
            ga, meta.get("licenseName"), meta.get("licenseUrl")
        )
        override = OVERRIDES.get(ga, {})
        display_name = override.get("displayName", display_name)
        author = override.get("author", author)
        license_name = override.get("license", license_name)
        license_url = override.get("licenseUrl", license_url)
        category = override.get("category", category)

        if ga in app and ga in generated and app[ga] != generated[ga]:
            version = f"{app[ga]} · {generated[ga]}"
        elif ga in app:
            version = app[ga]
        elif ga in generated:
            version = generated[ga]
        else:
            version = extras[ga]

        scope = []
        if ga in app or ga in extras:
            scope.append("app")
        if ga in generated:
            scope.append("generated_apps")

        entry = {
            "name": ga,
            "displayName": display_name,
            "version": version,
            "author": author,
            "license": license_name,
            "licenseUrl": license_url,
            "category": category,
            "scope": scope,
        }
        if ga in EXTRA_NOTES:
            entry["notes"] = EXTRA_NOTES[ga]
        entries.append(entry)

    entries.sort(key=lambda e: (e["displayName"].lower(), e["name"]))
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
        notice = NOTICE_BY_LICENSE.get(e["license"], GENERIC_NOTICE)
        lines += ["", notice, ""]
    return "\n".join(lines).rstrip() + "\n"


def kotlin_escape(value):
    return (
        value.replace("\\", "\\\\")
        .replace('"', '\\"')
        .replace("$", "\\$")
    )


def build_kotlin_block(entries):
    lines = [
        "    // GENERATED-KNOWN-BEGIN — produced by scripts/generate-licenses.py",
        "    // from scripts/license-graph.json + scripts/license-pom-metadata.json.",
        "    // Do not edit by hand; run the generator instead.",
        "    val KNOWN: Map<String, KnownLicense> = mapOf(",
    ]
    items = []
    for e in entries:
        items.append(
            "        \"{ga}\" to KnownLicense(\"{name}\", \"{author}\", "
            "\"{license}\", \"{url}\", \"{category}\")".format(
                ga=kotlin_escape(e["name"]),
                name=kotlin_escape(e["displayName"]),
                author=kotlin_escape(e["author"]),
                license=kotlin_escape(e["license"]),
                url=kotlin_escape(e["licenseUrl"]),
                category=kotlin_escape(e["category"]),
            )
        )
    lines.append(",\n".join(items))
    lines.append("    )")
    lines.append("    // GENERATED-KNOWN-END")
    return "\n".join(lines) + "\n"


def replace_kotlin_block(old_text, block):
    begin_marker = "    // GENERATED-KNOWN-BEGIN"
    end_marker = "    // GENERATED-KNOWN-END"
    begin = old_text.index(begin_marker)
    end = old_text.index(end_marker) + len(end_marker)
    return old_text[:begin] + block.rstrip("\n") + old_text[end:]


def main():
    check = "--check" in sys.argv[1:]
    entries = build_entries()
    json_text = json.dumps(entries, indent=2, ensure_ascii=False) + "\n"
    notices_text = build_notices(entries)
    kotlin_old = KOTLIN_MANIFEST.read_text(encoding="utf-8")
    kotlin_new = replace_kotlin_block(kotlin_old, build_kotlin_block(entries))
    outputs = {
        JSON_OUT: json_text,
        NOTICES_ASSET: notices_text,
        NOTICES_ROOT: notices_text,
        KOTLIN_MANIFEST: kotlin_new,
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
        1 for e in entries if e["license"] == REVIEW_REQUIRED
    )
    print(f"Libraries: {len(entries)}  Unknown: {unknown}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
