using import radlib.core-extensions
using import radlib.foreign

define-scope gfx
    let header =
        include
            "sokol/sokol_gfx.h"
    using header.extern filter "sg_"
    using header.typedef filter "sg_"
    using header.define
    using header.enum filter "SG_"
    using header.struct

define-scope app
    let header =
        include
            "sokol/sokol_app.h"
    using header.extern filter "sapp_"
    using header.typedef filter "sapp_"
    using header.define
    using header.enum
    using header.struct

do
    let gfx = (sanitize-scope gfx "^sg_")
    let app = (sanitize-scope app "^sapp_")
    locals;
