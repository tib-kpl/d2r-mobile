#!/usr/bin/env python3
# 用合成 NSO 头验证版本拒绝，不需要游戏内容。
# Test version rejection with synthetic NSO headers, without game content.
from pathlib import Path
import sys
import tempfile
import unittest
DM = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(DM / 'tools'))
from game_version import PREVIOUS_MAIN, SUPPORTED, ORIGINAL_133, require_supported, program_profile


class Versions(unittest.TestCase):
    def test_supported_and_previous_programs(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); (root / 'ExeFS').mkdir()
            for name, value in SUPPORTED.items():
                data = bytearray(0x60); data[:4] = b'NSO0'; data[0x40:0x50] = bytes.fromhex(value)
                (root / 'ExeFS' / name).write_bytes(data)
            self.assertEqual(require_supported(root), SUPPORTED)
            self.assertEqual(program_profile(root), 'enhanced134')
            main = root / 'ExeFS/main'; data = bytearray(main.read_bytes()); data[0x40:0x50] = bytes.fromhex(PREVIOUS_MAIN); main.write_bytes(data)
            self.assertEqual(require_supported(root), ORIGINAL_133)
            self.assertEqual(program_profile(root), 'original133')
            sdk = root / 'ExeFS/sdk'; original_sdk = sdk.read_bytes()
            sdk.write_bytes(original_sdk[:0x40] + bytes(16) + original_sdk[0x50:])
            with self.assertRaisesRegex(ValueError, 'Unsupported'): require_supported(root)
            sdk.write_bytes(original_sdk)
            data[0x40:0x50] = b'\0' * 16; main.write_bytes(data)
            with self.assertRaisesRegex(ValueError, 'Unsupported'): require_supported(root)


if __name__ == '__main__': unittest.main()
