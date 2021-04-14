using import .radlib.core-extensions
using import .radlib.foreign

switch operating-system
case 'linux
    load-library "libgtk-3.so"
    load-library (.. module-dir "/../native/bin/libnfd.so")
case 'windows
    load-library (.. module-dir "/../native/bin/nfd.dll")
default
    error "OS not supported"

define-scope nfd
    let header =
        include "../native/nativefiledialog/src/include/nfd.h"
    using header.extern filter "^NFD_"
    using header.enum filter "nfd"
    using header.typedef filter "nfd"

sanitize-scope nfd "^NFD_" "^nfd"
