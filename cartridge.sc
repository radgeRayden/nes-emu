using import struct
using import Array
using import UTF-8
using import .helpers

struct ROM
    prgrom : (Array u8)
    chrrom : (Array u8)

fn load-iNES (data)
fn load-ROM (path)
    let data =
        try (read-whole-file path)
        else (error "could not read file")
    # check for magic string NES<EOF>
    let iNES? =
        and
            (data @ 0) == (char "N")
            (data @ 1) == (char "E")
            (data @ 2) == (char "S")
            (data @ 3) == 0x1A
    # http://wiki.nesdev.com/w/index.php/NES_2.0
    let iNES2.0? =
        and
            iNES?
            ((data @ 7) & 0x0C) == 0x80

    # NOTE: we don't handle loading iNES2.0 yet
    if iNES?
        load-iNES data
    else
        hide-traceback;
        error "ROM format not supported"

do
    let ROM load-ROM
    locals;
