"""Rename an apktool-decoded APK so it installs next to the original.

usage: rename_package.py <decoded-dir> <old-package> <new-package> <new-label>

Changes:
- apktool.yml: renameManifestPackage (aapt2 --rename-manifest-package keeps class names intact)
- AndroidManifest.xml: provider authorities and app-defined permission names that start with the old
  package (two installed apps may not share these)
- res/values/strings.xml: app_name, so the two apps can be told apart in the library
"""
import pathlib
import re
import sys

root, old, new, label = pathlib.Path(sys.argv[1]), sys.argv[2], sys.argv[3], sys.argv[4]

yml = root / "apktool.yml"
text = yml.read_text()
text, n = re.subn(r"renameManifestPackage:.*", f"renameManifestPackage: {new}", text)
if n == 0:
    text = text.replace("packageInfo:", f"packageInfo:\n  renameManifestPackage: {new}", 1)
yml.write_text(text)

manifest = root / "AndroidManifest.xml"
xml = manifest.read_text()
prefix = re.escape(old)


def fix_authorities(match):
    value = re.sub(rf"(^|;){prefix}(?=[.;]|$)", lambda m: m.group(1) + new, match.group(2))
    return match.group(1) + value + '"'


xml = re.sub(r'(android:authorities=")([^"]*)"', fix_authorities, xml)
xml = re.sub(rf'(<(?:permission|uses-permission)[^>]*android:name="){prefix}\.', rf"\g<1>{new}.", xml)
manifest.write_text(xml)

strings = root / "res" / "values" / "strings.xml"
if strings.exists():
    s = strings.read_text()
    s = re.sub(r'(<string name="app_name">)[^<]*(</string>)', rf"\g<1>{label}\g<2>", s)
    strings.write_text(s)
else:
    xml = manifest.read_text()
    xml = re.sub(r'(<application[^>]*android:label=")[^"@]*(")', rf"\g<1>{label}\g<2>", xml)
    manifest.write_text(xml)

print(f"renamed {old} -> {new}; label '{label}'")
