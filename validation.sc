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
cpu.RS = 0xFD
cpu.PC = 0xC000
cpu.RP = 0x24

fn fmt-hex (i)
    width := (sizeof i) * 2
    representation := (hex i)
    padding := width - (countof representation)
    let str =
        if (padding > 0)
            let buf = (alloca-array i8 padding)
            for i in (range padding)
                buf @ i = 48:i8 # "0"
            .. "0x" (string buf padding) representation
        else
            .. "0x" (hex i)
    sc_default_styler 'style-number str

fn format-operand (addrmode lo hi)
    using opcodes

    inline joinLE (lo hi)
        ((hi as u16) << 8) | lo

    switch addrmode
    case AddressingMode.Immediate
        .. "#$" (hex lo)
    case AddressingMode.Absolute
        .. "$" (hex (joinLE lo hi))
    default
        ""

inline fetch ()
    pc := cpu.PC
    op := cpu.mmem @ pc
    let lo =
        ? ((pc + 1) < (countof cpu.mmem)) (cpu.mmem @ (pc + 1)) 0x00:u8
    let hi =
        ? ((pc + 2) < (countof cpu.mmem)) (cpu.mmem @ (pc + 2)) 0x00:u8
    _ op lo hi

fn print-cpu-state ()
    let op lo hi = (fetch)
    local opcode = (opcodes.opcode-table @ op)
    print
        fmt-hex cpu.PC
        "\t"
        fmt-hex opcode.byte
        fmt-hex lo
        fmt-hex hi
        opcode.mnemonic
        format-operand opcode.addrmode lo hi
        opcode.addrmode
        "\t\t"
        .. "A:" (fmt-hex cpu.RA)
        .. "X:" (fmt-hex cpu.RX)
        .. "Y:" (fmt-hex cpu.RY)
        .. "P:" (fmt-hex cpu.RP)
        .. "SP:" (fmt-hex cpu.RS)
    # print (fmt-hex cpu.PC) opcode.mnemonic opcode.addrmode (va-map fmt-hex opcode.byte lo hi)

inline decode (code lo hi)
    local opcode = (opcodes.opcode-table @ code)
    opcode.fun &opcode &cpu lo hi
    ;

loop ()
    if (cpu.PC >= (countof cpu.mmem))
        print "finished" (fmt-hex cpu.PC) (fmt-hex cpu.RA)
        break;
    print-cpu-state;
    decode (fetch)
    stdio.getchar;
    ;

none
