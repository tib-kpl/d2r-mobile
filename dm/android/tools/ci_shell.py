#!/usr/bin/env python3
# 从公开接口生成空壳表；实际数据由玩家的 derived.tsv 在启动时加载。
# Generate shell tables from public interfaces; the player's derived.tsv supplies runtime data.
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile

DM = Path(__file__).resolve().parents[2]
CAPACITY = 4096


def generate(destination):
    destination.mkdir(parents=True, exist_ok=True)
    spec = importlib.util.spec_from_file_location('shell_stubs', DM / 'ios/tools/shell_stubs.py')
    stubs = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(stubs)
    # 仅用空值调用已有替身模板，不需要或复制任何游戏数据。
    # Exercise the existing stand-in templates with empty values, without any game data.
    with tempfile.TemporaryDirectory() as tmp:
        gen = Path(tmp)
        (gen / 'dm_hud_exp.h').write_text('static uint32_t k_exp[100] = {' + ','.join(['0'] * 100) + '};', encoding='utf-8')
        (gen / 'dm_vault_tab.h').write_text(
            '#define VT_NPART 11\nstatic const char *vt_part_name[VT_NPART] = {' + ','.join(['""'] * 11) + '};\n'
            + '\n'.join(['    { 0, 0, 0, 0, 0, -1, "", "" },'] * CAPACITY)
            + '\nstatic const char *vt_set_key[] = {' + ','.join(['""'] * CAPACITY) + '};\n', encoding='utf-8')
        art_dir = DM / 'ios/Sources/TouchArt'
        manifest = json.loads((art_dir / 'native_art_manifest.json').read_text(encoding='utf-8'))
        atlases = {a['asset']: a for a in manifest['assets']}
        lines = ['#include "dm_touch_native_art.h"', '#include "dm_input.h"', '#include <stddef.h>',
                 'const dm_ta_atlas dm_ta_atlases[DM_TA_ATLASES]={']
        for i in range(9):
            a = atlases[f'dm_native_skill_{i}.png']
            lines.append(' {"%s",%d,%d,%d,%d,%d},' % (a['asset'][:-4], *a['atlas_px'], a['frame_width'], a['frame_pitch'], a['frame_count']))
        lines.append('};')
        for name, path in [('manifest', art_dir / 'native_art_manifest.json'),
                           ('geometry', DM / 'ios/docs/native_art_v1_geometry.json'),
                           ('binding', DM / 'ios/docs/touch_layout_spec_native_binding_v5.json')]:
            lines.append(f'const char dm_ta_{name}_sha[]="{hashlib.sha256(path.read_bytes()).hexdigest()}";')
        lines += ['static const struct { uint32_t id; int atlas,frame; } rows[]={',
                  '\n'.join([' {0,-1,-1},'] * CAPACITY), '};',
                  (DM / 'ios/tools/native_art/implementation.c.inc').read_text(encoding='utf-8')]
        (gen / 'dm_touch_native_art.c').write_text('\n'.join(lines), encoding='utf-8')
        stubs.stubs(None, str(gen))
        vault = (gen / 'dm_vault_tab.h').read_text(encoding='utf-8')
        vault = vault.replace('#define VT_NROW (int)(sizeof vt_rows / sizeof vt_rows[0])', 'static int vt_nrow;\n#define VT_NROW vt_nrow')
        vault = vault.replace('#define VT_NSET (int)(sizeof vt_set_key / sizeof vt_set_key[0])', 'static int vt_nset;\n#define VT_NSET vt_nset')
        vault = vault.replace('*i < VT_NSET', f'*i < {CAPACITY}').replace('*i >= VT_NROW', f'*i >= {CAPACITY}')
        vault = vault.replace('i < VT_NSET', f'i < {CAPACITY}').replace('i < VT_NROW', f'i < {CAPACITY}')
        vault = vault.replace('if (a == VT_NPART && b == VT_NROW && c == VT_NSET) return 0;',
                              f'vt_nrow = b; vt_nset = c;\n    if (a == VT_NPART && b > 0 && c > 0 && '
                              f'dm_ci_count("VTPART") == a && dm_ci_count("VTROW") == b && dm_ci_count("VTSET") == c) return 0;')
        vault = vault.replace('#include "dm_derived_rt.h"', '#include "dm_derived_rt.h"\n#include "dm_ci_count.h"')
        (gen / 'dm_vault_tab.h').write_text(vault, encoding='utf-8')
        art = (gen / 'dm_touch_native_art.c').read_text(encoding='utf-8')
        art = art.replace('if (i == (int)(sizeof rows / sizeof rows[0])) return 0;',
                          'if (i > 0 && dm_ci_count("TAROW") == i) return 0;')
        art = art.replace('#include "dm_derived_rt.h"', '#include "dm_derived_rt.h"\n#include "dm_ci_count.h"')
        (gen / 'dm_touch_native_art.c').write_text(art, encoding='utf-8')
        for name in ('dm_hud_exp.h', 'dm_vault_tab.h', 'dm_touch_native_art.c'):
            (destination / name).write_bytes((gen / name).read_bytes())
    (destination / 'dm_ci_count.h').write_text('''// 运行时条数检查。 | Runtime row-count validation.
#pragma once
#include "dm_derived_rt.h"
static void dm_ci_count_cb(char **f, int n, void *ctx) { (void)f; (void)n; (void)ctx; }
static int dm_ci_count(const char *tag) { return dm_derived_each(tag, dm_ci_count_cb, NULL); }
''', encoding='utf-8')
    (destination / 'dm_shell_bright.h').write_text('''// 昼夜表在导入时提供。 | Day/night data comes from the import pack.
#include "dm_derived_rt.h"
static struct { uint16_t lv; uint8_t act, drlg; int8_t varies; } k_lv[4096];
static unsigned k_lv_count;
static void bright_row(char **f, int n, void *ctx) {
    (void)ctx;
    if (n != 4 || k_lv_count >= 4096) return;
    unsigned i = k_lv_count++;
    k_lv[i].lv = atoi(f[0]); k_lv[i].act = atoi(f[1]);
    k_lv[i].drlg = atoi(f[2]); k_lv[i].varies = atoi(f[3]);
}
static int bright_load(void) {
    k_lv_count = 0; int n = dm_derived_each("BRIGHT", bright_row, NULL);
    return n > 0 && n == (int)k_lv_count ? 0 : -1;
}
__attribute__((constructor)) static void bright_reg(void) { dm_derived_register(bright_load); }
''', encoding='utf-8')
    # CMake 要求这一路径存在；空壳通过上面的运行时头加载实际数据。
    # CMake requires this path; the shell loads the actual data through the runtime header above.
    (destination / 'dm_bright_levels.inc').write_text('// Runtime: dm_shell_bright.h\n', encoding='utf-8')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('output', type=Path)
    generate(parser.parse_args().output)
