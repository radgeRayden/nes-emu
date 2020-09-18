using import radlib.libc
using import Array
using import Option

using import .common
import .opcodes

fn dump-memory (cpu path)
    using stdio
    let fhandle = (fopen path "wb")
    if (fhandle == null)
    fwrite cpu.mmem 1 (countof cpu.mmem) fhandle
    fclose fhandle

fn read-whole-file (path)
    using stdio

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

nestest-path := module-dir .. "/nes-test-roms/other/nestest.nes"
let romdata =
    try
        read-whole-file nestest-path
    else
        print "was unable to open ROM file"
        exit 1

global cpu : CPUState
let memcpy = _string.memcpy
memcpy (reftoptr (cpu.mmem  @ 0x8000)) romdata (countof romdata)
cpu.PC = 0xC000
dump-memory cpu "nestest.dump"
none
