import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from version import version


class Versions(unittest.TestCase):
    def test_numeric_increment_and_rerun(self):
        self.assertEqual(version('2.2', '7'), '2.2.7')
        self.assertEqual(version('2.2', '7'), version('2.2', 7))
        self.assertEqual(version('2.2', 8), '2.2.8')

    def test_invalid_inputs(self):
        for prefix, run in [('2.2.0', 1), ('02.2', 1), ('2.x', 1), ('2.2', 0), ('2.2', -1), ('2.2', 1_000_000)]:
            with self.subTest(prefix=prefix, run=run), self.assertRaises(ValueError):
                version(prefix, run)

    def test_workflow_outputs(self):
        root = Path(__file__).resolve().parents[1]
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / 'outputs'
            subprocess.run([sys.executable, str(root / 'ci/version.py')], cwd=root,
                           env={**os.environ, 'GITHUB_RUN_NUMBER': '7', 'GITHUB_OUTPUT': str(output)}, check=True, capture_output=True)
            values = dict(line.split('=', 1) for line in output.read_text().splitlines())
            self.assertEqual(values['version'], (root / 'VERSION').read_text().strip() + '.7')
            self.assertEqual(values['tag'], 'v' + values['version'])
            self.assertGreater(int(values['code']), 20116)


if __name__ == '__main__':
    unittest.main()
