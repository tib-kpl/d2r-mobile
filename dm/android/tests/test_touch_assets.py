"""用自造小文件验证安卓导入资源合同，不读游戏内容。
Validate Android imported-asset contracts with synthetic tiny files, without game content.
"""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

TOOLS = Path(__file__).resolve().parents[1] / 'tools'


def load(name):
    spec = importlib.util.spec_from_file_location(name, TOOLS / (name + '.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


stage = load('stage_touch_assets')
strings = load('gen_strings')


class AssetContract(unittest.TestCase):
    def test_complete_payload_and_tamper(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); native = root / 'art'; hud = root / 'hud'; overlay = root / 'overlay'
            native.mkdir(); hud.mkdir(); (overlay / 'data').mkdir(parents=True)
            for name in [f'dm_native_skill_{i}.png' for i in range(9)] + ['dm_native_circle.png', 'dm_native_map.png', 'dm_native_menu.png']:
                (native / name).write_bytes(b'synthetic-art')
            for name in ['base_full.png', 'lvl_0.png', 'lvl_1.png', 'lvl_d0.png', 'lvl_d1.png', 'slot.png', 'glyph.png'] + [f'diff_{i}.png' for i in range(1, 9)] + [f'sil_dm_native_skill_{i}.png' for i in range(9)]:
                (hud / name).write_bytes(b'synthetic-hud')
            ui = {key: {'glyphs': {'0': {'file': 'glyph.png'}}} for key in ('lv', 'badge', 'vital')}
            for group in ui.values():
                group['glyphs'][' '] = {'file': None, 'adv': 7}
            (hud / 'ui.json').write_text(json.dumps(ui), encoding='utf-8')
            for name in ('ctrl_touch', 'touch_strings.tsv', 'data/.build.config', 'data/test.data'):
                (overlay / name).write_bytes(b'synthetic-overlay')
            (hud / 'glyph.png').unlink()
            with self.assertRaisesRegex(ValueError, 'missing or unsafe HUD asset'):
                stage.stage(native, hud, overlay)
            self.assertFalse((overlay / 'touchart').exists())
            (hud / 'glyph.png').write_bytes(b'synthetic-hud')
            stage.stage(native, hud, overlay)
            fingerprint = stage.overlay_contract.source_fingerprint(Path(__file__).resolve().parents[3])
            entries = stage.overlay_contract.verify_manifest(overlay, fingerprint)
            self.assertIn('touchart/dm_native_skill_8.png', entries)
            self.assertIn('hud_v4_ui/glyph.png', entries)
            (overlay / 'touchart/dm_native_skill_8.png').write_bytes(b'changed')
            with self.assertRaises(stage.overlay_contract.ContractError):
                stage.overlay_contract.verify_manifest(overlay, fingerprint)

    def test_android_controls_available_in_each_language(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); strings.generate(root)
            for folder in ('values', 'values-zh', 'values-fr'):
                text = (root / folder / 'strings.xml').read_text(encoding='utf-8')
                names = {node.attrib['name'] for node in ET.fromstring(text)}
                for key in ('dmAndroidAttack', 'dmCancel', 'dmAndroidChooseFolder', 'dmAndroidMissingData'):
                    self.assertIn(key, names)


if __name__ == '__main__':
    unittest.main()
