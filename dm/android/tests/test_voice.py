#!/usr/bin/env python3
# 用虚拟清单与档案测试法语配音判定。 | Test French voice detection with synthetic manifests and archives.
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

DM = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(DM / 'tools'))
from voice_resources import append_french_voice


class VoiceTests(unittest.TestCase):
    def test_manifest_lists_all_french_archives(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / 'build_manifest.psv').write_text(
                '1|0|9|1|fixture|1|fixture|1|n|data:locales/audio/frFR/a|0\n'
                '2|0|9|2|fixture|1|fixture|1|n|data:data/locales/audio/frFR/b|0\n'
                '3|0|8|1|fixture|1|fixture|1|n|data:data/locales/audio/esES/a|0\n', encoding='utf-8')
            derived = root / 'derived.tsv'
            self.assertEqual(append_french_voice(derived, root), ['09-00000001.data', '09-00000002.data'])
            self.assertNotIn('08-', derived.read_text())

    def test_host_requires_every_french_archive(self):
        cc = shutil.which('cc') or shutil.which('clang')
        if not cc: self.skipTest('Native C compiler required')
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); data = root / 'data'; data.mkdir()
            derived = root / 'derived.tsv'
            derived.write_text('VOICE\t3\t09-00000001.data\nVOICE\t3\t09-00000002.data\n')
            source = root / 'voice.c'
            source.write_text('#include "dm_voice.h"\nint main(int n,char **v){return dm_voice_fr_available(v[1]) ? 0 : 1;}\n')
            exe = root / 'voice'
            subprocess.run([cc, '-I' + str(DM / 'host'), str(source), '-o', str(exe)], check=True)
            env = {**os.environ, 'DM_DERIVED': str(derived)}
            self.assertEqual(subprocess.run([str(exe), str(root)], env=env).returncode, 1)
            (data / '09-00000001.data').write_bytes(b'fixture')
            self.assertEqual(subprocess.run([str(exe), str(root)], env=env).returncode, 1)
            (data / '09-00000002.data').write_bytes(b'fixture')
            self.assertEqual(subprocess.run([str(exe), str(root)], env=env).returncode, 0)
            derived.write_text('VOICE\t3\t../outside.data\n')
            self.assertEqual(subprocess.run([str(exe), str(root)], env=env).returncode, 1)


if __name__ == '__main__': unittest.main()
