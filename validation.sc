using import radlib.libc
using import Array
using import Option
using import struct

using import .cpu
import .opcodes
using import .helpers

struct RegisterSnapshot
    PC : u16
    A  : u8
    X  : u8
    Y  : u8
    P  : u8
    SP : u8

    opcode : u8
    mnemonic : rawstring
    operand-low  : (Option u8)
    operand-high : (Option u8)

    inline __== (lhsT rhsT)
        dump lhsT rhsT

fn parse-log (path)
    using stdio
    using _string
    let fhandle = (fopen path "r")

    fseek fhandle 0 SEEK_END
    let flen = (ftell fhandle)
    fseek fhandle 0 SEEK_SET

    local logged-state : (Array RegisterSnapshot)
    loop ()
        local line : (array i8 128)
        let ptr = (fgets &line (sizeof line) fhandle)
        if (ptr == null)
            break;

        local snap : RegisterSnapshot
        assert ((sscanf &line "%hx" &snap.PC) == 1)

        local lo : u8
        local hi : u8
        # we'll be skipping to specific line offsets since the log table has a fixed
        # width for columns.
        let ins-byte-count =
            sscanf (reftoptr (line @ 6)) "%hhx %hhx %hhx" &snap.opcode &lo &hi
        assert ins-byte-count
        match ins-byte-count
        case 1
            ;
        case 2
            snap.operand-low = lo
        default
            snap.operand-low = lo
            snap.operand-high = hi

        assert (sscanf (reftoptr (line @ 16)) "%s" &snap.mnemonic)

        let ins-byte-count =
            sscanf (reftoptr (line @ 48)) "A:%hhx X:%hhx Y:%hhx P:%hhx SP:%hhx"
                &snap.A
                &snap.X
                &snap.Y
                &snap.P
                &snap.SP
        assert (ins-byte-count == 5)

        'append logged-state snap
        ;

    fclose fhandle
    deref logged-state
fn dump-memory (cpu path)
    using stdio
    let fhandle = (fopen path "wb")
    if (fhandle == null)
        error "could not open file for writing"
    fwrite cpu.mmem 1 (countof cpu.mmem) fhandle
    fclose fhandle

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

global state : CPUState
load-iNES state romdata

fn format-operand (addrmode lo hi)
    using opcodes

    switch addrmode
    case AddressingMode.Immediate
        .. "#$" (hex lo)
    case AddressingMode.Absolute
        .. "$" (hex (joinLE lo hi))
    default
        ""

# fn print-cpu-state ()
#     let op lo hi = (fetch)
#     local opcode = (opcodes.opcode-table @ op)
#     print
#         fmt-hex state.PC
#         "\t"
#         fmt-hex opcode.byte
#         fmt-hex lo
#         fmt-hex hi
#         opcode.mnemonic
#         format-operand opcode.addrmode lo hi
#         opcode.addrmode
#         "\t\t"
#         .. "A:" (fmt-hex state.RA)
#         .. "X:" (fmt-hex state.RX)
#         .. "Y:" (fmt-hex state.RY)
#         .. "P:" (fmt-hex state.RP)
#         .. "SP:" (fmt-hex state.RS)

loop ()
    if (state.PC >= (CPUState.AddressableMemorySize - 1))
        print "finished" (fmt-hex state.PC) (fmt-hex state.RA)
        break;
    'next state opcodes.opcode-table
    ;

none
