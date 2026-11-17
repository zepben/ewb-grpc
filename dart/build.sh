#!/usr/bin/env bash
#
# Copyright 2026 Zeppelin Bend Pty Ltd
#
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.
#
# Generates the Dart messages and gRPC clients into lib/. Requires protoc on the PATH.

set -euo pipefail
cd "$(dirname "$0")"

dart pub get

plugin_dir="$(mktemp -d)"
trap 'rm -rf "$plugin_dir"' EXIT
printf '#!/bin/sh\nexec dart run protoc_plugin\n' > "$plugin_dir/protoc-gen-dart"
chmod +x "$plugin_dir/protoc-gen-dart"

rm -rf lib/zepben
mkdir -p lib
protoc \
  --plugin="protoc-gen-dart=$plugin_dir/protoc-gen-dart" \
  --dart_out="grpc:lib" \
  -I../proto \
  $(find ../proto/zepben -name '*.proto' | sort)
