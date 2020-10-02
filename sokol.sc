using import radlib.core-extensions
using import radlib.foreign

load-library (module-dir .. "/bin/libsokol.so")

let sokol =
    include
        """"#include "sokol/sokol_gfx.h"
            #include "sokol/sokol_app.h"
            #include "sokol/sokol_glue.h"
            #include "sokol/util/sokol_imgui.h"

define-scope gfx
    using sokol.extern filter "sg_"
    using sokol.typedef filter "sg_"
    using sokol.define
    using sokol.enum filter "SG_"
    using sokol.struct

define-scope app
    using sokol.extern filter "sapp_"
    using sokol.typedef filter "sapp_"
    using sokol.define
    using sokol.enum
    using sokol.struct

define-scope imgui
    using sokol.extern filter "simgui_"
    using sokol.typedef filter "simgui_"
    using sokol.define
    using sokol.enum
    using sokol.struct

define-scope glue
    let sgcontext = sokol.extern.sapp_sgcontext

for k v in sokol.enum
    'set-symbol (v as type) '__typecall
        inline (cls)
            bitcast 0 cls

do
    let gfx = (sanitize-scope gfx "^sg_")
    let app = (sanitize-scope app "^sapp_")
    let glue
    let imgui = (sanitize-scope imgui "^simgui_")
    locals;
