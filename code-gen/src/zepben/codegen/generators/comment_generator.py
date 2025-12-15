#  Copyright 2025 Zeppelin Bend Pty Ltd
#
#  This Source Code Form is subject to the terms of the Mozilla Public
#  License, v. 2.0. If a copy of the MPL was not distributed with this
#  file, You can obtain one at https://mozilla.org/MPL/2.0/.
from zepben.codegen.generators.base_generator import BaseSpecGenerator


class CommentGenerator:
    proto = lambda comment: "/**\n" + '\n'.join(f" * {c}" for c in comment.splitlines()) + "\n */"
    kt = proto
    python = lambda comment: '"""\n' + comment + '\n"""'
