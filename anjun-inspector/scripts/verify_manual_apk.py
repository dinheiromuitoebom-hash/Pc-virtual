"""Check the capabilities of the compiled manual APK, not just its source manifest."""
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import zipfile


def main():
    apk = Path(sys.argv[1]).resolve()
    sdk = Path(os.environ['ANDROID_HOME'])
    aapt = sdk / 'build-tools' / '34.0.0' / 'aapt'
    manifest = subprocess.check_output(
        [str(aapt), 'dump', 'xmltree', str(apk), 'AndroidManifest.xml'], text=True
    )
    badging = subprocess.check_output([str(aapt), 'dump', 'badging', str(apk)], text=True)
    elements = re.findall(r'^\s*E: ([\w-]+)', manifest, flags=re.MULTILINE)
    forbidden = [e for e in elements if e.startswith('uses-permission') or e in {
        'service', 'receiver', 'provider', 'instrumentation', 'permission'
    }]
    if forbidden:
        raise SystemExit(f'Manual APK contains forbidden manifest components: {forbidden}')
    if "package: name='com.anjunauto.inspector'" not in badging:
        raise SystemExit('Unexpected application ID')
    if "versionName='0.2.0-manual'" not in badging:
        raise SystemExit('Unexpected manual version')
    for marker in ('BIND_ACCESSIBILITY_SERVICE', 'SYSTEM_ALERT_WINDOW', 'MediaProjection'):
        if marker in manifest:
            raise SystemExit(f'Unexpected capability in manifest: {marker}')
    with zipfile.ZipFile(apk) as archive:
        for name in archive.namelist():
            if name.endswith('.dex'):
                data = archive.read(name)
                for marker in (b'Lcom/anjunauto/inspector/InspectorService;',
                               b'Landroid/accessibilityservice/AccessibilityService;',
                               b'Landroid/media/projection/MediaProjection;'):
                    if marker in data:
                        raise SystemExit(f'Unexpected automatic screen access code: {marker!r}')
    report = {
        'applicationId': 'com.anjunauto.inspector',
        'version': '0.2.0-manual',
        'declared_permissions': [],
        'background_services': [],
        'accessibility_service_present': False,
        'automatic_screen_capture_present': False,
        'scope': 'Compiled APK manifest and DEX capability check; not a Play Protect approval',
    }
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
