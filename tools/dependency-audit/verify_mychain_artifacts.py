#!/usr/bin/env python3
"""Offline release check. Reads artifacts only; never initializes SDKs."""
import argparse
import hashlib
import json
import struct
import zipfile
from pathlib import Path


def metadata(archive):
    result = {}
    for item in archive.infolist():
        if not (item.filename.startswith('META-INF/maven/')
                and item.filename.endswith('/pom.properties')):
            continue
        props = {}
        for line in archive.read(item).decode(errors='replace').splitlines():
            if '=' in line and not line.startswith('#'):
                key, value = line.split('=', 1)
                props[key.strip()] = value.strip()
        if props.get('groupId') and props.get('artifactId'):
            key = props['groupId'] + ':' + props['artifactId']
            result.setdefault(key, set()).add(props.get('version', ''))
    return {key: sorted(values) for key, values in result.items()}


def verify(path, expected):
    errors = []
    with zipfile.ZipFile(path) as archive:
        names = set(archive.namelist())
        actual = metadata(archive)
        for key in sorted(actual.keys() | expected['dependencies'].keys()):
            if actual.get(key) != expected['dependencies'].get(key):
                errors.append('dependency %s: expected %s, got %s' % (
                    key, expected['dependencies'].get(key), actual.get(key)))
        for name, digest in expected['classProbes'].items():
            if name not in names or hashlib.sha256(archive.read(name)).hexdigest() != digest:
                errors.append('class fingerprint mismatch: ' + name)
        for prefix, family in expected['classFamilies'].items():
            entries = sorted(n for n in names if n.startswith(prefix) and n.endswith('.class'))
            fingerprint = hashlib.sha256()
            for name in entries:
                fingerprint.update(name.encode() + b'\0' + hashlib.sha256(archive.read(name)).digest())
            if len(entries) != family['classes'] or fingerprint.hexdigest() != family['sha256']:
                errors.append('complete class-family mismatch: ' + prefix)
        if any(n.startswith('lombok/') and n.endswith('.class') for n in names):
            errors.append('compile-only Lombok was bundled')
        own = [n for n in names if n.startswith((
            'com/alipay/antchain/bridge/plugins/mychain/',
            'com/alipay/antchain/bridge/plugins/mychain020/')) and n.endswith('.class')]
        if not own:
            errors.append('plugin application classes missing')
        for name in own:
            if struct.unpack('>H', archive.read(name)[6:8])[0] > 52:
                errors.append('plugin class exceeds Java 8 target: ' + name)
    return {'artifact': str(path), 'passed': not errors, 'errors': errors}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jars', type=Path, nargs='+')
    parser.add_argument('--baseline', type=Path, default=Path(__file__).with_name('mychain-baseline.json'))
    parser.add_argument('--output', type=Path, help='New output file; existing files are never overwritten')
    args = parser.parse_args()
    baseline = json.loads(args.baseline.read_text())['artifacts']
    rows = []
    for path in args.jars:
        if path.name not in baseline:
            rows.append({'artifact': str(path), 'passed': False, 'errors': ['No matching baseline']})
            continue
        try:
            rows.append(verify(path, baseline[path.name]))
        except (OSError, zipfile.BadZipFile, KeyError) as error:
            rows.append({'artifact': str(path), 'passed': False, 'errors': [str(error)]})
    result = {'passed': all(row['passed'] for row in rows), 'results': rows}
    text = json.dumps(result, ensure_ascii=False, indent=2)
    if args.output:
        with args.output.open('x') as output:
            output.write(text + '\n')
    print(text)
    return 0 if result['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
