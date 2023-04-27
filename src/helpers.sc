from (import format) let hex
using import Array
using import String

fn get-instruction-length (addrmode)
    switch addrmode
    case 'implicit
        1
    case 'accumulator
        1
    case 'immediate
        2
    case 'zero-page
        2
    case 'zero-pageX
        2
    case 'zero-pageY
        2
    case 'relative
        2
    case 'absolute
        3
    case 'absoluteX
        3
    case 'absoluteY
        3
    case 'indirect
        3
    case 'indirectX
        2
    case 'indirectY
        2
    default
        unreachable;

fn... fmt-hex (i, color? = true)
    width := (sizeof i) * 2
    representation := (hex i)
    padding := width - (countof representation)
    let str =
        if (padding > 0)
            let buf = (alloca-array i8 padding)
            for i in (range padding)
                buf @ i = 48:i8 # "0"
            .. (string buf padding) representation
        else
            (hex i)
    if color?
        String (sc_default_styler 'style-number (string str))
    else
        str

fn read-whole-file (path)
    import .libc
    using libc.stdio

    fhandle := (fopen path "rb")
    if (fhandle == null)
        raise false
    else
        local buf : (Array u8)
        fseek fhandle 0 SEEK_END
        flen := (ftell fhandle) as u64
        fseek fhandle 0 SEEK_SET

        'resize buf flen

        fread buf flen 1 fhandle
        fclose fhandle
        buf

inline joinLE (lo hi)
    ((hi as u16) << 8) | lo

inline separateLE (v16)
    _ (v16 as u8) ((v16 >> 8) as u8)

do
    let fmt-hex read-whole-file joinLE separateLE get-instruction-length
    locals;
