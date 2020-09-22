#!/usr/bin/env bash
set -euo pipefail

gcc -shared -o ./bin/libsokol.so sokol.c
