"""Prepare the original controller UI profile from the owner's 1.0.33 files."""
import hashlib
import os
from pathlib import Path
import re
import shutil
import sys
import time

from d2rm_pack import ROOT, PY, MODS, say, run, step
from game_version import program_profile
from voice_resources import append_french_voice

DM = Path(__file__).resolve().parent.parent
TOOLS = DM / 'tools'
GFX = DM / 'gfx/tools'


def shader_index(packs, image, output):
    """Locate shaders by their control hash, never by another build's addresses."""
    locations = {}
    for path in sorted(Path(packs).glob('*.bin')):
        data = path.read_bytes()
        starts = [match.start() for match in re.finditer(b'BTCDBTCD', data)]
        for index, start in enumerate(starts):
            record = data[start:starts[index + 1] if index + 1 < len(starts) else len(data)]
            control = record.find(bytes.fromhex('34127698'))
            if control < 0 or control + 0x880 > len(record):
                raise ValueError(f'Incomplete shader control: {path.name}:{index}')
            key = hashlib.sha256(record[control:control + 0x880]).hexdigest()[:16]
            locations.setdefault(key, (path.name, index, path.name[:2]))
    embedded = {}
    for line in (DM / 'gfx/nvn/shader_map.tsv').read_text(encoding='utf-8').splitlines():
        fields = line.split('\t')
        if len(fields) > 4 and fields[0].startswith('main.rodata@'):
            embedded[fields[3]] = fields[2]
    for match in re.finditer(bytes.fromhex('34127698'), image):
        offset = match.start()
        key = hashlib.sha256(image[offset:offset + 0x880]).hexdigest()[:16]
        if key in embedded:
            locations.setdefault(key, (f'main.rodata@{offset:x}', 0, embedded[key]))
    facts = {line.split('\t')[0] for line in (DM / 'gfx/nvn/shader_facts.tsv').read_text(encoding='utf-8').splitlines() if not line.startswith('#')}
    missing = set(locations) - facts
    if missing or len(locations) != 6927:
        raise ValueError(f'Unsupported shader set: {len(locations)} shaders, {len(missing)} missing interfaces')
    Path(output).write_text(''.join('\t'.join((name, str(index), stage, key, '-')) + '\n'
                           for key, (name, index, stage) in sorted(locations.items())), encoding='utf-8')
    return len(locations)


def prepare(game, destination, dotnet):
    game, destination = Path(game), Path(destination)
    if program_profile(game) != 'original133':
        raise ValueError('This generator requires the recognized 1.0.33 program set')
    product = (DM / 'PRODUCT').read_text().splitlines()[0]
    # Include the actual NSO hashes: two locally modified copies can share a build ID.
    fingerprint = hashlib.sha256()
    for path in [TOOLS / 'patch_nso.py'] + [game / 'ExeFS' / module for module in MODS]:
        fingerprint.update(path.read_bytes())
    work = Path(ROOT) / 'build/original133' / fingerprint.hexdigest()[:24]
    out, packs = work / 'out', work / 'packs'
    out.mkdir(parents=True, exist_ok=True)
    for module in MODS:
        def patch(module=module):
            run([PY, str(TOOLS / 'patch_nso.py'), str(game / 'ExeFS' / module), str(out), module],
                env={'DM_TARGET': 'android', 'DM_RULESET': 'native', 'DM_PRIVATE_GATES': '0'},
                log=str(work / (module + '.log')), what='Rewrite ' + module)
            (work / (module + '.done')).write_text('ok', encoding='ascii')
        step('Rewrite ' + module, lambda m=module: (work / (m + '.done')).is_file() and all(
            (out / (m + '.' + suffix)).is_file() for suffix in ('patched.bin', 'gates.tsv', 'fixups.tsv', 'cuts.tsv', 'obs.tsv', 'sites.tsv')), patch)

    sys.path.insert(0, str(DM / 'ios/tools'))
    from touch_layout import _read_entry
    packs.mkdir(exist_ok=True)
    romfs = game / 'RomFS'
    for line in (romfs / 'data/build_manifest.psv').read_text(encoding='utf-8').splitlines():
        fields = line.split('|')
        if len(fields) > 9 and '/hd/shaders/' in fields[9].lower() and fields[9].lower().endswith('.bin'):
            path = packs / Path(fields[9]).name
            if not path.exists():
                path.write_bytes(_read_entry(str(romfs), fields))
    sys.path.insert(0, str(DM / 'verify'))
    import nsoimg
    index = work / 'shader_map.tsv'
    say('Indexing original shaders: %d' % shader_index(packs, nsoimg.load(str(game / 'ExeFS/main')), index))
    xlate_dir = Path(ROOT) / 'build/gfx/dmsh_xlate'
    executable = xlate_dir / ('dmsh_xlate.exe' if os.name == 'nt' else 'dmsh_xlate')
    step('Build shader translator', lambda: executable.is_file(),
         lambda: run([dotnet, 'publish', str(GFX / 'dmsh_xlate'), '-c', 'Release', '-o', str(xlate_dir), '-v', 'quiet']))
    shaders = work / 'shaders'
    (work / 'shader-work').mkdir(exist_ok=True)
    run([PY, str(GFX / 'dmsh_vk.py'), str(packs), str(shaders)],
        env={'XLATE': str(executable), 'MAIN': str(game / 'ExeFS/main'), 'DM_SHADER_MAP': str(index), 'WORK': str(work / 'shader-work')},
        log=str(work / 'shaders.log'), what='Translate original shaders')
    queries = work / 'queries'
    run([PY, str(GFX / 'pack_builtin_queries.py'), '--output', str(queries)], what='Build query kernels')
    for path in queries.glob('*.xgs'):
        shutil.copyfile(path, shaders / path.name)
    if len(list(shaders.glob('*.xgs'))) != 6931:
        raise ValueError('Incomplete shader output')
    destination.mkdir(parents=True, exist_ok=True)
    final = destination / 'android'
    part = destination / 'android.part'
    shutil.rmtree(part, ignore_errors=True)
    part.mkdir()
    for source in (out, shaders):
        shutil.copytree(source, part / source.name)
    derived = part / 'derived.tsv'
    derived.write_text('', encoding='utf-8')
    say('French voice archives to keep in data/: ' + ', '.join(append_french_voice(derived, romfs / 'data')))
    (part / 'pack_info.txt').write_text(f'PRODUCT={product}\nTARGET=android\nPROFILE=original133\nMADE={time.strftime("%Y-%m-%dT%H:%M:%S")}\n', encoding='utf-8')
    shutil.rmtree(final, ignore_errors=True)
    os.replace(part, final)
    say(f'Ready: {final}. Keep it beside RomFS/data on the SD card. Requires a controller; original UI, offline play only. Keep game content private.')
