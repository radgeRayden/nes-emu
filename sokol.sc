using import radlib.core-extensions
using import radlib.foreign

load-library (module-dir .. "/bin/libsokol.so")

# define-scope gfx


let sokol =
    include
        """"#include "sokol/sokol_gfx.h"
            #include "sokol/sokol_app.h"
            #include "sokol/sokol_glue.h"

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

define-scope glue
    let sgcontext = sokol.extern.sapp_sgcontext

do
    let gfx = (sanitize-scope gfx "^sg_")
    let app = (sanitize-scope app "^sapp_")
    let glue
    locals;
