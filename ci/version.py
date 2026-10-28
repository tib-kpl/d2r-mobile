"""Numeric Android release version; reruns keep the same version and build code."""
from pathlib import Path
import os
import re


def version(prefix, run):
    if not re.fullmatch(r'(0|[1-9]\d*)\.(0|[1-9]\d*)', prefix) or not 1 <= int(run) < 1_000_000:
        raise ValueError('Expected major.minor and a positive workflow run number below 1000000')
    return f'{prefix}.{int(run)}'


if __name__ == '__main__':
    value = version(Path('VERSION').read_text().strip(), os.environ['GITHUB_RUN_NUMBER'])
    print(value)
    if os.environ.get('GITHUB_OUTPUT'):
        with open(os.environ['GITHUB_OUTPUT'], 'a', encoding='utf-8') as output:
            output.write(f'version={value}\ntag=v{value}\ncode={20110 + int(os.environ["GITHUB_RUN_NUMBER"])}\n')
