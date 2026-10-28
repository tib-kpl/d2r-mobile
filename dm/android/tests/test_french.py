#!/usr/bin/env python3
# 用虚拟资源验证法语覆盖层与手势文字。 | Verify French overlays and gesture text with synthetic resources.
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

DM = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(DM / 'ios/tools'))
import dm_i18n
import touch_layout
import touch_strings


class FrenchTests(unittest.TestCase):
    def test_game_overlay_uses_french_column(self):
        with tempfile.TemporaryDirectory() as out:
            original = json.dumps([{'id': 1, 'Key': 'fixture'}])
            with patch.object(touch_layout, 'read_layout', return_value=('ui.json', original)):
                paths, count = dm_i18n.build('fixture', out, {})
            rows = {r['Key']: r for r in json.loads(Path(paths['ui.json']).read_text(encoding='utf-8-sig'))}
            self.assertEqual(count, len(dm_i18n.rows()))
            self.assertEqual(rows['dmCancel']['frFR'], 'Annuler')
            self.assertEqual(rows['dmCancel']['enUS'], 'Cancel')
            self.assertEqual(rows['dmHudLife']['frFR'], 'Vie : %d / %d')

    def test_french_gestures_preserve_controller_wording(self):
        original = 'synthetic-controller-original'
        digest = hashlib.sha1(original.encode()).hexdigest()[:8]
        source = json.dumps([{'Key': 'UseItemController', 'zhCN': original, 'frFR': original}])
        table = {'ui-controller.json': {'UseItemController': (digest, 'synthetic-touch')}}
        with patch.object(touch_layout, 'read_layout', return_value=('ui-controller.json', source)), \
             patch.multiple(touch_strings, TABLE=table, EXTRA=(), FILE_ONLY=set()):
            sidecar = touch_strings.build_sidecar('fixture')
            self.assertIn(('UseItemController', digest, 'Touchez deux fois pour utiliser'), sidecar)
            with tempfile.TemporaryDirectory() as out:
                replacements, count = touch_strings.build('fixture', out)
            self.assertEqual(replacements, {})
            self.assertEqual(count, 0)


if __name__ == '__main__':
    unittest.main()
