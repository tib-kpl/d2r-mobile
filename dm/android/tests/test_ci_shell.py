#!/usr/bin/env python3
# 检查 CI 空壳的运行时表，不需要游戏文件。 | Test CI shell runtime tables without game files.
import importlib.util
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

DM = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('ci_shell', DM / 'android/tools/ci_shell.py')
ci = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ci)


class ShellTests(unittest.TestCase):
    def test_runtime_tables(self):
        compiler = shutil.which('cc') or shutil.which('clang')
        if not compiler:
            self.skipTest('Native C compiler required')
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            ci.generate(root)
            data = root / 'derived.tsv'
            data.write_text(''.join('EXP\t' + '\t'.join(map(str, range(i, i + 10))) + '\n' for i in range(0, 100, 10))
                            + ''.join(f'VTPART\tpart{i}\n' for i in range(11))
                            + 'VTROW\t1\t0\t0\t2\t3\t-1\tfixture\tfixture-icon\n'
                            + 'VTSET\tfixture-set\nBRIGHT\t1\t0\t2\t1\n', encoding='utf-8')
            source = root / 'test.c'
            source.write_text('''#include "dm_hud_exp.h"
#include "dm_vault_tab.h"
#include "dm_shell_bright.h"
int main(void) {
    return dm_derived_reload() || k_exp[99] != 99 || VT_NROW != 1 || VT_NSET != 1 ||
        strcmp(vt_rows[0].key, "fixture") || k_lv_count != 1 || k_lv[0].drlg != 2;
}
''', encoding='utf-8')
            exe = root / 'test'
            subprocess.run([compiler, '-D__ANDROID__', '-I' + str(root), '-I' + str(DM / 'host'),
                            str(source), '-o', str(exe)], check=True, capture_output=True)
            subprocess.run([str(exe)], env={**os.environ, 'DM_DERIVED': str(data)}, check=True)
            # 超过容量必须报错，不能静默截断。 | Reject overflow instead of silently truncating.
            with data.open('a', encoding='utf-8') as f:
                f.write('VTSET\ttoo-many\n' * ci.CAPACITY)
            self.assertNotEqual(subprocess.run([str(exe)], env={**os.environ, 'DM_DERIVED': str(data)},
                                               capture_output=True).returncode, 0)


if __name__ == '__main__':
    unittest.main()
