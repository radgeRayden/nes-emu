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

fn load-iNES (cpu rom)
    inline readstring (position size)
        assert ((position + size) < (countof rom))
        let ptr = (reftoptr (rom @ position))
        string (ptr as (pointer i8)) size

    # validate that this is an iNES rom
    let magic = (readstring 0 4)
    assert (magic == "NES\x1a") "iNES magic constant not found"
    prg-rom-size := ((rom @ 4) as usize) * 16 * 1024
    chr-rom-size := ((rom @ 5) as usize) * 8 * 1024
    trainer? := (rom @ 6) & 0b00000100
    assert (not trainer?) "trainer support not implemented"
    # NOTE: for now we'll skip the rest of the header because our test doesn't use
    # the features. Must be implemented on the actual loading function.
    assert ((16 + prg-rom-size + chr-rom-size) <= (countof rom))

    let memcpy = _string.memcpy
    let prg-romptr = (reftoptr (rom @ 0x10))
    let prg-rom-destptr = (reftoptr (cpu.mmem @ 0x8000))
    # NOTE: we know this ROM is only 16kb, so I'm just hardcoding this for now.
    memcpy prg-rom-destptr prg-romptr prg-rom-size
    let prg-rom-destptr = (reftoptr (cpu.mmem @ 0xc000))
    memcpy prg-rom-destptr prg-romptr prg-rom-size

nestest-path := module-dir .. "/nes-test-roms/other/nestest.nes"
let romdata =
    try
        read-whole-file nestest-path
    else
        print "was unable to open ROM file"
        exit 1

global cpu : CPUState
load-iNES cpu romdata
cpu.PC = 0xC000
dump-memory cpu "nestest.dump"
none
