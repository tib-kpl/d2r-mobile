#!/usr/bin/env python3
# 检查公开空壳 APK 的内容边界。 | Check the content boundary of the public shell APK.
import sys
import zipfile


def check(path):
    with zipfile.ZipFile(path) as apk:
        names = apk.namelist()
        if 'lib/arm64-v8a/libdm.so' not in names:
            raise SystemExit('Missing ARM64 native library')
        for name in names:
            if name.startswith('assets/') or name.endswith(('.xgs', '.data', '.patched.bin', 'derived.tsv')):
                raise SystemExit(f'Unexpected game content in shell: {name}')
    print('PASS: ARM64 shell APK, no packaged game assets')


if __name__ == '__main__':
    check(sys.argv[1])
