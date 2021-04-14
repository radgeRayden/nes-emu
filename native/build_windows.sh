#!/usr/bin/env bash
set -euo pipefail

gcc -shared -o ./bin/sokol.dll sokol.c -fPIC -lkernel32 -lgdi32 -luser32 -pthread -lstdc++ -L./cimgui -lcimgui -Wl,--export-all
make -C ./nativefiledialog/build/gmake_windows/ clean
make -C ./nativefiledialog/build/gmake_windows/ CFLAGS=-fPIC
gcc -o ./bin/nfd.dll -shared ./nativefiledialog/build/obj/x64/Release/nfd/nfd_common.o ./nativefiledialog/build/obj/x64/Release/nfd/nfd_win.o -lole32 -luuid
