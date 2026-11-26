import re
import sys

# The diag file path from the installer output ("만든 파일: <path>"). The installer wraps long lines at a space, so a
# long desktop path (OneDrive "바탕 화면") can run over two lines: join them back with that space.
t = re.sub(r'\x1b\[[0-9;]*m', '', open(sys.argv[1], encoding='utf-8', errors='replace').read()).replace('\r', '')
m = re.search(r'만든 파일: (.*?\.txt)', t, re.S)
print(m.group(1).replace('\n', ' ') if m else '')
