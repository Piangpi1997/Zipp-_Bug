#!/usr/bin/env python3
"""Resolve Zip_Bug's FULL dependency graph (direct + transitive) and the
metadata of every module, for Open Source Licenses v2.

The walker implements Maven/Gradle-style resolution over published POMs:
  * roots = the app's implementation dependencies (+ Kotlin stdlib) and
    the generated-app template core (AppScaffolder) + Kotlin stdlib
  * parent POM inheritance (properties, dependencyManagement, licenses)
  * dependencyManagement pinning, including imported BOMs
  * compile/runtime scopes only; optional and test/provided deps skipped
  * per-dependency exclusions honoured
  * version conflicts resolved newest-first, iterated to a fixpoint

Outputs (committed; consumed by scripts/generate-licenses.py):
  * scripts/license-graph.json          {"app": [...], "generatedApps": [...],
                                         "extras": [...]}  ("g:a:v" strings)
  * scripts/license-pom-metadata.json   {"g:a:v": {name, author, licenseName,
                                         licenseUrl}}  (raw POM values)

Extras are the hand-kept build/test-only modules (Room compiler, JUnit,
Hamcrest, org.json): they are not on the runtime classpath but shipped
with the v1 list, so they stay, clearly noted as build/test-only.

The Gradle task `verifyLicenseGraph` in app/build.gradle.kts re-resolves
the same classpaths with real Gradle during CI builds and fails if this
committed graph drifts from Gradle's own resolution.

Usage: python3 scripts/resolve-license-graph.py
"""

import json
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CACHE = Path("/tmp/pomcache")
CACHE.mkdir(parents=True, exist_ok=True)

NS = {"m": "http://maven.apache.org/POM/4.0.0"}
GOOGLE_GROUPS = ("androidx.", "com.google.", "com.android.")

EXTRAS = [
    "androidx.room:room-compiler:2.6.1",
    "junit:junit:4.13.2",
    "org.hamcrest:hamcrest-core:1.3",
    "org.json:json:20240303",
]

COORD_RE = re.compile(
    r"([a-zA-Z][a-zA-Z0-9_.\-]*):([a-zA-Z0-9_.\-]+):([0-9][a-zA-Z0-9_.+\-]*)"
)


def pom_url(group, artifact, version):
    path = "/".join(group.split(".")) + f"/{artifact}/{version}/{artifact}-{version}.pom"
    if group.startswith(GOOGLE_GROUPS):
        return "https://maven.google.com/" + path
    return "https://repo1.maven.org/maven2/" + path


def _metadata_url(group, artifact):
    path = "/".join(group.split(".")) + f"/{artifact}/maven-metadata.xml"
    if group.startswith(GOOGLE_GROUPS):
        return "https://maven.google.com/" + path
    return "https://repo1.maven.org/maven2/" + path


def resolve_range(group, artifact, spec):
    """Resolve a Maven version range like [2.2.0] or [1.0,2.0) to the
    highest available version inside it (Gradle semantics)."""
    spec = spec.strip()
    if spec.startswith("[") and spec.endswith("]") and "," not in spec:
        return spec[1:-1].strip()
    lower_inc = spec.startswith("[")
    upper_inc = spec.endswith("]")
    body = spec[1:-1]
    lower_s, _, upper_s = body.partition(",")
    lower_s, upper_s = lower_s.strip(), upper_s.strip()
    req = urllib.request.Request(
        _metadata_url(group, artifact),
        headers={"User-Agent": "zipbug-license-walker"},
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        meta = ET.fromstring(resp.read())
    versions = [
        v.text.strip()
        for v in meta.findall(".//m:version", NS)
        if v.text
    ]
    def in_range(v):
        if lower_s:
            c = compare_versions(v, lower_s)
            if c < 0 or (c == 0 and not lower_inc):
                return False
        if upper_s:
            c = compare_versions(v, upper_s)
            if c > 0 or (c == 0 and not upper_inc):
                return False
        return True
    candidates = [v for v in versions if in_range(v)]
    if not candidates:
        raise ValueError(f"No version of {group}:{artifact} in range {spec}")
    return max(candidates, key=version_key)


def compare_versions(a, b):
    ka, kb = version_key(a), version_key(b)
    return (ka > kb) - (ka < kb)


def normalize_version(group, artifact, version):
    if version and (version.startswith("[") or version.startswith("(")):
        return resolve_range(group, artifact, version)
    return version


def fetch_pom(group, artifact, version):
    key = CACHE / f"{group}__{artifact}__{version}.pom"
    if key.exists():
        return key.read_bytes()
    url = pom_url(group, artifact, version)
    req = urllib.request.Request(url, headers={"User-Agent": "zipbug-license-walker"})
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            data = resp.read()
    except urllib.error.HTTPError as exc:
        if exc.code != 404:
            raise
        alt = url.replace("https://maven.google.com/", "https://repo1.maven.org/maven2/") \
            if "maven.google.com" in url else \
            url.replace("https://repo1.maven.org/maven2/", "https://maven.google.com/")
        print(f"404 at {url}; trying {alt}")
        req = urllib.request.Request(alt, headers={"User-Agent": "zipbug-license-walker"})
        with urllib.request.urlopen(req, timeout=30) as resp:
            data = resp.read()
    key.write_bytes(data)
    return data


def text(elem, tag):
    child = elem.find(f"m:{tag}", NS)
    return child.text.strip() if child is not None and child.text else None


def interpolate(value, props, depth=0):
    if value is None or depth > 10:
        return value
    def repl(m):
        return props.get(m.group(1), m.group(0))
    out = re.sub(r"\$\{([^}]+)\}", repl, value)
    return interpolate(out, props, depth + 1) if "${" in out else out


class Pom:
    """Effective (parent-merged) view of one POM."""

    def __init__(self, group, artifact, version):
        self.group, self.artifact, self.version = group, artifact, version
        raw = fetch_pom(group, artifact, version)
        self.root = ET.fromstring(raw)
        self.parent = None
        parent_el = self.root.find("m:parent", NS)
        if parent_el is not None:
            pg = text(parent_el, "groupId")
            pa = text(parent_el, "artifactId")
            pv = text(parent_el, "version")
            if pg and pa and pv:
                self.parent = get_pom(pg, pa, normalize_version(pg, pa, pv))
        self.props = {}
        if self.parent:
            self.props.update(self.parent.props)
        self.props["project.groupId"] = group
        self.props["pom.groupId"] = group
        self.props["project.artifactId"] = artifact
        self.props["project.version"] = version
        self.props["pom.version"] = version
        props_el = self.root.find("m:properties", NS)
        if props_el is not None:
            for child in props_el:
                tag = child.tag.split("}")[-1]
                if child.text:
                    self.props[tag] = child.text.strip()
        for key in list(self.props):
            self.props[key] = interpolate(self.props[key], self.props)

    def managed_versions(self):
        """dependencyManagement GA -> version, parents first, BOMs merged."""
        managed = {}
        if self.parent:
            managed.update(self.parent.managed_versions())
        dm = self.root.find("m:dependencyManagement/m:dependencies", NS)
        if dm is not None:
            for dep in dm.findall("m:dependency", NS):
                g = interpolate(text(dep, "groupId"), self.props)
                a = interpolate(text(dep, "artifactId"), self.props)
                v = interpolate(text(dep, "version"), self.props)
                dtype = text(dep, "type") or "jar"
                scope = text(dep, "scope")
                if dtype == "pom" and scope == "import" and g and a and v:
                    bom = get_pom(g, a, normalize_version(g, a, v))
                    for ga, ver in bom.managed_versions().items():
                        managed.setdefault(ga, ver)
                elif g and a and v:
                    managed[f"{g}:{a}"] = v
        return managed

    def dependencies(self):
        """Declared dependencies (own + inherited), raw fields."""
        deps = []
        if self.parent:
            deps.extend(self.parent.inherited_dependencies())
        deps.extend(self._own_dependencies())
        return deps

    def inherited_dependencies(self):
        deps = []
        if self.parent:
            deps.extend(self.parent.inherited_dependencies())
        deps.extend(self._own_dependencies())
        return deps

    def _own_dependencies(self):
        deps = []
        container = self.root.find("m:dependencies", NS)
        if container is None:
            return deps
        for dep in container.findall("m:dependency", NS):
            g = interpolate(text(dep, "groupId"), self.props)
            a = interpolate(text(dep, "artifactId"), self.props)
            v = interpolate(text(dep, "version"), self.props)
            scope = text(dep, "scope") or "compile"
            optional = (text(dep, "optional") or "false") == "true"
            exclusions = []
            ex_el = dep.find("m:exclusions", NS)
            if ex_el is not None:
                for ex in ex_el.findall("m:exclusion", NS):
                    eg = text(ex, "groupId")
                    ea = text(ex, "artifactId")
                    if eg and ea:
                        exclusions.append(f"{eg}:{ea}")
            if g and a:
                deps.append(
                    {
                        "ga": f"{g}:{a}",
                        "version": v,
                        "scope": scope,
                        "optional": optional,
                        "exclusions": exclusions,
                    }
                )
        return deps

    def metadata(self):
        name = text(self.root, "name")
        author = None
        org = self.root.find("m:organization/m:name", NS)
        if org is not None and org.text:
            author = org.text.strip()
        if not author:
            dev = self.root.find("m:developers/m:developer/m:name", NS)
            if dev is not None and dev.text:
                author = dev.text.strip()
        licenses = []
        lic_container = self.root.find("m:licenses", NS)
        if lic_container is not None:
            for lic in lic_container.findall("m:license", NS):
                licenses.append(
                    {
                        "name": text(lic, "name"),
                        "url": text(lic, "url"),
                    }
                )
        if (not name or not licenses) and self.parent:
            parent_meta = self.parent.metadata()
            if not name:
                name = parent_meta.get("name")
            if not author:
                author = parent_meta.get("author")
            if not licenses:
                licenses = parent_meta.get("licenses", [])
        return {"name": name, "author": author, "licenses": licenses}


_POM_CACHE = {}


def get_pom(group, artifact, version):
    key = (group, artifact, version)
    if key not in _POM_CACHE:
        _POM_CACHE[key] = Pom(group, artifact, version)
    return _POM_CACHE[key]


_QUALIFIER_RANK = {
    "alpha": 0, "a": 0, "beta": 1, "b": 1, "milestone": 2, "m": 2,
    "rc": 3, "cr": 3, "snapshot": 4, "": 5, "final": 5, "release": 5,
    "sp": 6,
}


def version_key(version):
    tokens = re.split(r"[.\-_]", version.lower())
    key = []
    for tok in tokens:
        m = re.match(r"^(\d+)([a-z]*)$", tok)
        if m:
            key.append((int(m.group(1)), _QUALIFIER_RANK.get(m.group(2), 5), m.group(2)))
        elif tok.isdigit():
            key.append((int(tok), 5, ""))
        else:
            mm = re.match(r"^([a-z]+)(\d*)$", tok)
            if mm:
                key.append(
                    (0, _QUALIFIER_RANK.get(mm.group(1), 5), mm.group(1))
                )
            else:
                key.append((0, 5, tok))
    while len(key) < 6:
        key.append((0, 5, ""))
    return key


def resolve(roots):
    """Newest-wins fixpoint resolution. roots: list of 'g:a:v'.

    Each round walks from the roots over the currently selected module
    versions (honouring per-edge exclusions), collects the version every
    edge requests (dependencyManagement of the declaring POM wins over
    the declared version), and selects the maximum per module. Rounds
    repeat until the selection is stable.
    """
    root_versions = {}
    for root in roots:
        g, a, v = root.split(":")
        ga = f"{g}:{a}"
        v = normalize_version(g, a, v)
        if ga not in root_versions or version_key(v) > version_key(root_versions[ga]):
            root_versions[ga] = v
    selected = dict(root_versions)
    for _ in range(30):
        candidates = {}
        visited = set()
        stack = [(ga, set()) for ga in root_versions]
        while stack:
            ga, excluded = stack.pop()
            if ga in visited or ga in excluded:
                continue
            visited.add(ga)
            version = selected.get(ga)
            if version is None:
                continue
            g, a = ga.split(":")
            pom = get_pom(g, a, version)
            managed = pom.managed_versions()
            for dep in pom.dependencies():
                if dep["scope"] not in ("compile", "runtime"):
                    continue
                if dep["optional"]:
                    continue
                dga = dep["ga"]
                if dga in excluded:
                    continue
                dv = managed.get(dga) or dep["version"]
                if not dv:
                    continue
                dg, da = dga.split(":")
                dv = normalize_version(dg, da, dv)
                if dga not in candidates or \
                        version_key(dv) > version_key(candidates[dga]):
                    candidates[dga] = dv
                stack.append((dga, excluded | set(dep["exclusions"])))
        new_selected = dict(root_versions)
        for ga, v in candidates.items():
            if ga not in new_selected or \
                    version_key(v) > version_key(new_selected[ga]):
                new_selected[ga] = v
        if new_selected == selected:
            break
        selected = new_selected
    return sorted(f"{ga}:{v}" for ga, v in selected.items())


def parse_app_roots():
    text = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    block = text[text.index("dependencies {"):]
    block = block[: block.index("\n}")]
    roots = []
    for m in re.finditer(r"implementation\(\"([^\"]+)\"\)", block):
        if COORD_RE.fullmatch(m.group(1)):
            roots.append(m.group(1))
    root_gradle = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
    kv = re.search(
        r'id\("org\.jetbrains\.kotlin\.android"\)\s+version\s+"([^"]+)"',
        root_gradle,
    ).group(1)
    roots.append(f"org.jetbrains.kotlin:kotlin-stdlib:{kv}")
    return roots


def parse_generated_roots():
    text = (
        ROOT / "app/src/main/java/com/zipbug/base/creator/AppScaffolder.kt"
    ).read_text(encoding="utf-8")
    coords = {}
    for line in text.splitlines():
        if "implementation" in line or "dependency ==" in line:
            for m in COORD_RE.finditer(line):
                coords[f"{m.group(1)}:{m.group(2)}"] = m.group(3)
    root_gradle = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
    kv = re.search(
        r'id\("org\.jetbrains\.kotlin\.android"\)\s+version\s+"([^"]+)"',
        root_gradle,
    ).group(1)
    coords["org.jetbrains.kotlin:kotlin-stdlib"] = kv
    return [f"{ga}:{v}" for ga, v in sorted(coords.items())]


def main():
    app_roots = parse_app_roots()
    generated_roots = parse_generated_roots()
    print(f"App roots: {len(app_roots)}  Generated roots: {len(generated_roots)}")
    app_graph = resolve(app_roots)
    generated_graph = resolve(generated_roots)
    print(f"App graph: {len(app_graph)}  Generated graph: {len(generated_graph)}")

    graph = {
        "app": app_graph,
        "generatedApps": generated_graph,
        "extras": EXTRAS,
    }
    (ROOT / "scripts/license-graph.json").write_text(
        json.dumps(graph, indent=2) + "\n", encoding="utf-8"
    )

    metadata = {}
    all_modules = sorted(set(app_graph) | set(generated_graph) | set(EXTRAS))
    failures = []
    for coord in all_modules:
        g, a, v = coord.split(":")
        try:
            meta = get_pom(g, a, v).metadata()
            lic = meta["licenses"][0] if meta["licenses"] else {}
            metadata[coord] = {
                "name": meta.get("name"),
                "author": meta.get("author"),
                "licenseName": lic.get("name"),
                "licenseUrl": lic.get("url"),
            }
        except Exception as exc:  # noqa: BLE001 - reported, then review-required
            failures.append(f"{coord}: {exc}")
            metadata[coord] = {
                "name": None,
                "author": None,
                "licenseName": None,
                "licenseUrl": None,
            }
    (ROOT / "scripts/license-pom-metadata.json").write_text(
        json.dumps(metadata, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    print(f"Metadata: {len(metadata)} modules; fetch failures: {len(failures)}")
    for failure in failures:
        print("  FAIL", failure)
    return 0


if __name__ == "__main__":
    sys.exit(main())
