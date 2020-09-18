using import radlib.core-extensions
using import radlib.foreign

define-scope cpath
    let header =
        include "cpath/cpath.h"
            options "-D_CPATH_FUNC_="
    using header.extern filter "^cpath"

sanitize-scope cpath "^cpath"
