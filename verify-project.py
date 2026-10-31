from pathlib import Path
import xml.etree.ElementTree as ET
root=Path(__file__).parent
for p in (root/'app/src/main/res').rglob('*.xml'):
    ET.parse(p)
ET.parse(root/'app/src/main/AndroidManifest.xml')
print('XML validation: OK')
print('Kotlin files:', len(list((root/'app/src/main/java').rglob('*.kt'))))
print('Project files:', len([p for p in root.rglob('*') if p.is_file()]))

# --- Open Source Licenses validation (CI gate) ---
import json, subprocess, sys
_entries = json.loads(
    (root / 'app/src/main/assets/open_source_licenses.json').read_text(encoding='utf-8')
)
_names = [e['name'] for e in _entries]
assert len(_names) == len(set(_names)), 'duplicate license entries'
for _e in _entries:
    for _f in ('name', 'displayName', 'version', 'author', 'license', 'licenseUrl', 'scope'):
        assert _e.get(_f), f'missing {_f} in {_e.get("name")}'
    assert _e['scope'] and set(_e['scope']) <= {'app', 'generated_apps'}, f'bad scope in {_e["name"]}'
_notices = (root / 'app/src/main/assets/third_party_notices.txt').read_text(encoding='utf-8')
for _n in _names:
    assert _n in _notices, f'{_n} missing from third_party_notices.txt'
_manifest_src = (
    root / 'app/src/main/java/com/zipbug/base/licenses/GeneratedLicenseManifest.kt'
).read_text(encoding='utf-8')
for _n in _names:
    assert f'"{_n}"' in _manifest_src, f'{_n} missing from GeneratedLicenseManifest.KNOWN'
_r = subprocess.run(
    [sys.executable, str(root / 'scripts/generate-licenses.py'), '--check'],
    capture_output=True, text=True
)
assert _r.returncode == 0, _r.stdout + _r.stderr
print(f'License metadata validation: OK ({len(_entries)} libraries)')
