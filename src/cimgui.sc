using import .radlib.core-extensions
using import .radlib.foreign

if (operating-system == 'linux)
    load-library (module-dir .. "/../bin/cimgui.so")

define-scope cimgui
    let header =
        include "cimgui/cimgui.h"
            options "-DCIMGUI_DEFINE_ENUMS_AND_STRUCTS"
                .. "-I" module-dir "/../native/"
    using header.extern filter "ig"
    using header.typedef filter "Im"
    using header.define
    using header.enum
    using header.struct

sanitize-scope cimgui "ig" "Im"
