#  Copyright 2025 Zeppelin Bend Pty Ltd
#
#  This Source Code Form is subject to the terms of the Mozilla Public
#  License, v. 2.0. If a copy of the MPL was not distributed with this
#  file, You can obtain one at https://mozilla.org/MPL/2.0/.
import re

_camel_pattern = re.compile(r'(?<!^)(?=[A-Z])')

def camel2snake(name: str):
    return _camel_pattern.sub('_', name).lower()

def removeindent(text: str) -> str:
    return '\n'.join(a.removeprefix(' ' * count_indent(text)) for a in text.splitlines())

def count_indent(block: str):
    a = (line for line in block.splitlines() if line)
    num = 0
    for i in next(a):
        if i == ' ':
            num += 1
            continue
        break
    return num