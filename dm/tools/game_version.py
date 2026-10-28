#!/usr/bin/env python3
# 在耗时打包前检查 NSO 构建号；不把版本字符串当成二进制兼容性证明。
# Check NSO build IDs before expensive packaging; a version label is not proof of binary compatibility.
from pathlib import Path
import sys

SUPPORTED = {
    'main': 'db0507f36b84200ac05f75d76ed5026f',
    'sdk': '2d369d9999e3e10e665106f840ffff9b',
    'subsdk0': '507f19d1240d09d00992248e8a0fd77e',
}
PREVIOUS_MAIN = '194361731c7b9f30b38425b244429e25'
ORIGINAL_133 = dict(SUPPORTED, main=PREVIOUS_MAIN)


def inspect_update(update):
    result = {}
    for name in SUPPORTED:
        with (Path(update) / 'ExeFS' / name).open('rb') as source:
            header = source.read(0x60)
        if len(header) < 0x60 or header[:4] != b'NSO0':
            raise ValueError(f'{name}: not a complete NSO program')
        result[name] = header[0x40:0x50].hex()
    return result


def require_supported(update):
    found = inspect_update(update)
    if found == SUPPORTED:
        return found
    if found == ORIGINAL_133:
        return found
    raise ValueError('Unsupported program build IDs: ' + ', '.join(f'{name}={value}' for name, value in found.items()))


def program_profile(update):
    return 'original133' if require_supported(update) == ORIGINAL_133 else 'enhanced134'


if __name__ == '__main__':
    if len(sys.argv) != 2: raise SystemExit('Usage: game_version.py <Update directory>')
    try:
        found = inspect_update(sys.argv[1])
        for name, value in found.items(): print(f'{name}: {value}')
        require_supported(sys.argv[1])
        print('Supported profile: ' + program_profile(sys.argv[1]))
    except (OSError, ValueError) as error:
        raise SystemExit(str(error))
