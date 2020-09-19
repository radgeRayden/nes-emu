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
    mnemonic : (array i8 4)
    operand-low  : (Option u8)
    operand-high : (Option u8)

    inline __== (lhsT rhsT)
        static-if (lhsT == rhsT)
            inline (l r)
                va-lfold true
                    inline (__ f result)
                        let k = (keyof f.Type)
                        result and ((getattr l k) == (getattr r k))
                    this-type.__fields__

    inline __repr (self)
        using import radlib.string-utils
        let lo =
            try
                fmt-hex ('unwrap self.operand-low)
            else
                "  "
        let hi =
            try
                fmt-hex ('unwrap self.operand-high)
            else
                "  "
        s := self
        let A X Y P SP = (va-map fmt-hex s.A s.X s.Y s.P s.SP)
        let op = (fmt-hex s.opcode false)
        local mnemonic = s.mnemonic
        f"${fmt-hex s.PC}  ${string &mnemonic} ${op} ${lo} ${hi}  A:${A} X:${X} Y:${Y} P:${P} SP:${SP}"

fn parse-log (path)
    using stdio
    using _string
    let fhandle = (fopen path "r")
    assert (fhandle != null)

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
        # NOTE: Here I use an intermediary string to avoid reading an hex value from the
        # mnemonic.
        let istring = (string (reftoptr (line @ 6)) 9)
        let ins-byte-count =
            sscanf istring "%hhx %hhx %hhx" &snap.opcode &lo &hi
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

fn take-register-snapshot (cpu)
    let op = (opcodes.opcode-table @ (cpu.mmem @ cpu.PC))
    let optT = (Option u8)
    # next two bytes after opcode
    let b1 b2 = (cpu.mmem @ (cpu.PC + 1)) (cpu.mmem @ (cpu.PC + 2))
    let lo hi =
        match ('length op)
        case 1
            _ (optT.None) (optT.None)
        case 2
            _ (optT b1) (optT.None)
        default
            _ (optT b1) (optT b2)

    local mnemonic : (array i8 4)
    for i c in (enumerate op.mnemonic)
        mnemonic @ i = c
    RegisterSnapshot
        A  = cpu.RA
        X  = cpu.RX
        Y  = cpu.RY
        PC = cpu.PC
        SP  = cpu.RS
        P  = cpu.RP
        opcode = op.byte
        mnemonic = mnemonic
        operand-low = lo
        operand-high = hi

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
nestest-log-path := module-dir .. "/nes-test-roms/other/nestest.log"
let romdata =
    try
        read-whole-file nestest-path
    else
        print "was unable to open ROM file"
        exit 1

global state : CPUState
load-iNES state romdata

let log-snapshots = (parse-log nestest-log-path)
# print (countof log-snapshots)
# for this test we set PC to 0xc000 as instructed by the test documentation (nestest.txt)
state.PC = 0xC000
for i entry in (enumerate log-snapshots)
    using import radlib.string-utils
    using import testing
    let current = (take-register-snapshot state)
    print current
    test (entry == current)
        f""""
             entry ${i} didn't match CPU state, log says:
             ${entry}
    'next state opcodes.opcode-table
    ;

none
