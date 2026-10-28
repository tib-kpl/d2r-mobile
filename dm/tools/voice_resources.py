#!/usr/bin/env python3
# 从玩家清单确定法语音频档案，不硬编码不同版本的分块编号。
# Identify French audio archives from the player's manifest, without hard-coding version-specific chunk numbers.
from pathlib import Path


def french_archives(data):
    result = set()
    with (Path(data) / 'build_manifest.psv').open(encoding='utf-8') as manifest:
        for line in manifest:
            fields = line.rstrip('\n').split('|')
            if len(fields) < 10:
                continue
            logical = fields[9].lower().split(':', 1)[-1].lstrip('/')
            if logical.startswith('data/'): logical = logical[5:]
            if not logical.startswith('locales/audio/frfr/'): continue
            chunk, archive = int(fields[2]), int(fields[3])
            if not 0 <= chunk <= 99 or not 0 <= archive <= 99999999:
                raise ValueError('Invalid French audio archive ID')
            result.add(f'{chunk:02d}-{archive:08d}.data')
    if not result:
        raise ValueError('The game manifest contains no French audio')
    return sorted(result)


def append_french_voice(derived, data):
    archives = french_archives(data)
    with Path(derived).open('a', encoding='utf-8', newline='\n') as target:
        for name in archives:
            target.write('VOICE\t3\t' + name + '\n')
    return archives
