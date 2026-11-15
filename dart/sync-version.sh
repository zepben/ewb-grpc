#!/usr/bin/env bash
#
# Copyright 2026 Zeppelin Bend Pty Ltd
#
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.
#
# Syncs the Python release or beta version to Dart's pub-semver equivalent.
# Usage: sync-version.sh path/to/pyproject.toml path/to/pubspec.yaml

set -euo pipefail

if [[ $# -ne 2 ]]; then
    echo "Usage: $0 path/to/pyproject.toml path/to/pubspec.yaml" >&2
    exit 1
fi

python_version=$(sed -nE "s/^[[:space:]]*version[[:space:]]*=[[:space:]]*['\"]([^'\"]+)['\"][[:space:]]*(#.*)?$/\1/p" "$1")

if [[ $python_version =~ ^([0-9]+\.[0-9]+\.[0-9]+)b([0-9]+)$ ]]; then
    dart_version="${BASH_REMATCH[1]}-beta.${BASH_REMATCH[2]}"
elif [[ $python_version =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    dart_version=$python_version
else
    echo "Unsupported Python version: '$python_version' (expected X.Y.Z or X.Y.ZbN)" >&2
    exit 1
fi

if [[ $(grep -c '^version:' "$2") -ne 1 ]]; then
    echo "Expected exactly one top-level version: line in $2" >&2
    exit 1
fi

sed -i "s/^version:.*/version: $dart_version/" "$2"
