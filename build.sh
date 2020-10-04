#!/usr/bin/env bash
set -euo pipefail

gcc -shared -o ./bin/libsokol.so sokol.c -fPIC -pthread -lX11 -lXi -lXcursor -lGL -ldl -lm
make -C ./nativefiledialog/build/gmake_linux/ clean
make -C ./nativefiledialog/build/gmake_linux/ CFLAGS=-fPIC
gcc -o ./bin/libnfd.so -shared -Wl,--whole-archive ./nativefiledialog/build/lib/Release/x64/libnfd.a -Wl,--no-whole-archive
