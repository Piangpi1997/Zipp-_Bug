from pathlib import Path
import xml.etree.ElementTree as ET
root=Path(__file__).parent
for p in (root/'app/src/main/res').rglob('*.xml'):
    ET.parse(p)
ET.parse(root/'app/src/main/AndroidManifest.xml')
print('XML validation: OK')
print('Kotlin files:', len(list((root/'app/src/main/java').rglob('*.kt'))))
print('Project files:', len([p for p in root.rglob('*') if p.is_file()]))
