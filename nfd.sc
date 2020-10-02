using import radlib.core-extensions
using import radlib.foreign

load-library "libgtk-3.so"
load-library "./bin/libnfd.so"

define-scope nfd
    let header =
        include "nativefiledialog/src/include/nfd.h"
    using header.extern filter "^NFD_"
    using header.enum filter "nfd"
    using header.typedef filter "nfd"

sanitize-scope nfd "^NFD_" "^nfd"
