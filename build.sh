#!/usr/bin/env bash
set -euo pipefail

gcc -shared -o ./bin/libsokol.so sokol.c -pthread -lX11 -lXi -lXcursor -lGL -ldl -lm
