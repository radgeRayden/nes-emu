using import Array

fn fmt-hex (i)
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
    sc_default_styler 'style-number str

fn read-whole-file (path)
    import radlib.libc
    using radlib.libc.stdio

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
    let fmt-hex read-whole-file joinLE separateLE
    locals;
