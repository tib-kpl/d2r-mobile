"""六种语言分两行排列，与宿主表顺序一致。
Six languages in two rows, in the same order as the host table."""
import json, re, sys
from pathlib import Path

root = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(root / 'ios/tools'))
import touch_layout as layout

out, _ = layout._custom_page('"name": "VideoOptionsPanel", "name": "OptionsTable"')
page = json.loads(re.sub(r',\s*([}\]])', r'\1', out))
nodes = {}
def walk(node):
    if isinstance(node, dict):
        if 'name' in node:
            assert node['name'] not in nodes, node['name']
            nodes[node['name']] = node
        for child in node.values():
            walk(child)
    elif isinstance(node, list):
        for child in node:
            walk(child)
walk(page)

opts = (('zh', 'Zh', 'dmCustomLangZh'), ('en', 'En', 'dmCustomLangEnOpt'), ('es', 'Es', 'dmCustomLangEsOpt'), ('ru', 'Ru', 'dmCustomLangRuOpt'), ('ko', 'Ko', 'dmCustomLangKoOpt'), ('fr', 'Fr', 'dmCustomLangFrOpt'))
for key, cap, label in opts:
    box = nodes['TouchCustom_lang_' + key]
    assert box['type'] == 'ToggleButtonWidget', key
    assert box['fields']['onClickMessage'].endswith('TouchCustom_lang_' + key), key
    assert nodes['TouchCustomLang%s_Text' % cap]['fields']['text'] == '@' + label, key
    assert nodes['TouchCustomLangHint%s' % cap]['fields']['text'] == '@dmCustomLangHint' + cap, key
assert nodes['TouchCustomLangHintKo']['fields']['rect']['x'] > 10000, 'hint starts off screen and the host moves it'
# 每项勾选框在前、字在后（字在勾选框右边 10），五项从左到右不重叠，全在各行勾选框那一列的右沿（1385）以内
# 文字框宽：实宽按俄语界面真包截图量（中文 99、English 166、Español 178、Русский 188；한국어按字体同比约 136），框比实宽多 30 不折行（180 宽时 English 折成「Englis h」）
right = 0
bottom = 0
for index, (key, cap, _) in enumerate(opts):
    if index % 3 == 0: right = 0
    t = nodes['TouchCustomLang%s_Text' % cap]['fields']['rect']
    b = nodes['TouchCustom_lang_' + key]['fields']['rect']
    assert t['width'] >= {'zh': 129, 'en': 196, 'es': 208, 'ru': 218, 'ko': 166, 'fr': 230}[key], (key, t)
    assert b['x'] >= right and t['x'] == b['x'] + 80, (key, b, t)
    right = t['x'] + t['width']
    bottom = max(bottom, t['y'] + t['height'])
    assert t['y'] == nodes['TouchCustomLangZh_Text']['fields']['rect']['y'] + (index // 3) * 112
assert right <= 1385, right
# 标签控件还在（宿主拿它定提示与手柄选中框），不写字、横跨整行
lab = nodes['TouchCustom_lang_Text']['fields']
assert lab['text'] == '' and lab['rect']['x'] == 0 and lab['rect']['width'] >= right, lab
assert lab['rect']['y'] + lab['rect']['height'] >= bottom
assert nodes['TouchCustomLangHintFr']['fields']['rect']['y'] >= bottom
host = (root / 'host/dm_custom.c').read_text(encoding='utf-8')
boxes = re.findall(r'"(TouchCustom_lang_[a-z]+)"', re.search(r'k_lang_box\[LANG_N\].*?=\s*\{(.*?)\}', host, re.S)[1])
assert boxes == ['TouchCustom_lang_' + k for k, _, _ in opts], boxes
assert re.search(r'#define LANG_N 6\b', host), 'LANG_N must be 6'
print('PASS language selector: six options in two rows, labels, hints and host order')
