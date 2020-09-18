using import Array

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
    let read-whole-file joinLE separateLE
    locals;
