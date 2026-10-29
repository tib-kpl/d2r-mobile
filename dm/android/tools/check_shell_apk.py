#!/usr/bin/env python3
# 检查公开空壳 APK 的内容边界。 | Check the content boundary of the public shell APK.
import sys
import zipfile


def check(path, require_movies=False):
    with zipfile.ZipFile(path) as apk:
        names = apk.namelist()
        if 'lib/arm64-v8a/libdm.so' not in names:
            raise SystemExit('Missing ARM64 native library')
        if require_movies:
            for component in ('avformat', 'avcodec', 'avutil', 'swscale'):
                if f'lib/arm64-v8a/lib{component}.so' not in names:
                    raise SystemExit(f'Missing movie decoder library: {component}')
            # AAPT may shorten raw resource filenames in release APKs.
            notices = [apk.read(n) for n in names if n.startswith('res/') and n.endswith('.txt')]
            for marker in (b'This application uses FFmpeg 9.0.2', b'GNU LESSER GENERAL PUBLIC LICENSE'):
                if not any(marker in content for content in notices):
                    raise SystemExit(f'Missing FFmpeg notice: {marker.decode()}')
        for name in names:
            if name.startswith('assets/') or name.endswith(('.xgs', '.data', '.patched.bin', 'derived.tsv')):
                raise SystemExit(f'Unexpected game content in shell: {name}')
    print('PASS: ARM64 shell APK, no packaged game assets')


if __name__ == '__main__':
    check(sys.argv[1], '--require-movies' in sys.argv[2:])
