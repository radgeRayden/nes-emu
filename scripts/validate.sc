from (import format) let hex dec
let C =  (import ..src.libc)
using import Array
using import Option
using import struct

using import ..src.instruction-set
using import ..src.6502-instruction-set
using import ..src.cpu
using import ..src.helpers

run-stage;

struct RegisterSnapshot
    # NOTE: due to %hhx format not being respected on windows (resulting in an overflow), we organize
      the struct in read order to mitigate any problems.
    PC : u16
    opcode : u8
    operand-low  : (Option u8)
    operand-high : (Option u8)
    mnemonic : (array i8 4)

    A  : u8
    X  : u8
    Y  : u8
    P  : u8
    SP : u8

    cycles : u64

    inline __== (lhsT rhsT)
        static-if (lhsT == rhsT)
            inline (l r)
                va-lfold true
                    inline (__ f result)
                        let k = (keyof f.Type)
                        result and ((getattr l k) == (getattr r k))
                    this-type.__fields__

    fn __repr (self)
        using import strfmt
        let lo =
            try
                fmt-hex ('unwrap self.operand-low)
            else
                S"  "
        let hi =
            try
                fmt-hex ('unwrap self.operand-high)
            else
                S"  "
        s := self
        let PC A X Y P SP = (va-map fmt-hex s.PC s.A s.X s.Y s.P s.SP)
        let op = (fmt-hex s.opcode false)
        let cyc = (sc_default_styler 'style-number (tostring s.cycles))
        local mnemonic = s.mnemonic
        let flags =
            fold (result = S"") for i c in (enumerate "NV54DIZC")
                local c = c
                let str = (string &c 1)
                bit-set? := (s.P & (1 << (7 - i))) != 0
                .. result
                    ? bit-set?
                        String
                            sc_default_styler 'style-string str
                        String
                            sc_default_styler 'style-comment "-"

        f"${PC}  ${string &mnemonic} ${op} ${lo} ${hi}  A:${A} X:${X} Y:${Y} P:${P} SP:${SP}  ${flags} CYC:${cyc}"

    fn display-diff (ours theirs)
        # we have to duplicate the repr code to be able to use different colors :(
        using import strfmt
        inline highlight (str correct?)
            if (not correct?)
                String
                    sc_default_styler 'style-error (string str)
            else
                str

        let PC-match? = (ours.PC == theirs.PC)
        let PC =
            highlight (fmt-hex ours.PC PC-match?) PC-match?

        let mnemonic-match? = (ours.mnemonic == theirs.mnemonic)
        let mnemonic =
            do
                local s = ours.mnemonic
                highlight (String &s 3) mnemonic-match?

        let op-match? = (ours.opcode == theirs.opcode)
        let op =
            highlight (hex ours.opcode) op-match?

        let lo-match? = (ours.operand-low == theirs.operand-low)
        let lo =
            if lo-match?
                try
                    fmt-hex ('unwrap ours.operand-low)
                else
                    S"  "
            else
                try (highlight (fmt-hex ('unwrap ours.operand-low) false) false)
                else (highlight S"■■" false)

        let hi-match? = (ours.operand-high == theirs.operand-high)
        let hi =
            if hi-match?
                try
                    fmt-hex ('unwrap ours.operand-high)
                else
                    S"  "
            else
                try (highlight (fmt-hex ('unwrap ours.operand-high) false) false)
                else (highlight S"■■" false)

        let A X Y P SP =
            va-map
                inline (k)
                    let attr = (getattr ours k)
                    let other-attr = (getattr theirs k)
                    let attr-match? = (attr == other-attr)
                    highlight (fmt-hex attr attr-match?) attr-match?
                _ 'A 'X 'Y 'P 'SP

        let cyc-match? = (ours.cycles == theirs.cycles)
        let cyc =
            if cyc-match?
                String
                    sc_default_styler 'style-number (tostring ours.cycles)
            else
                highlight (dec ours.cycles) false

        let flags =
            fold (result = S"") for i c in (enumerate "NV54DIZC")
                local c = c
                let str = (String &c 1)
                Abit-set? := (ours.P & (1 << (7 - i)))
                Bbit-set? := (theirs.P & (1 << (7 - i)))
                .. result
                    if Abit-set?
                        if Bbit-set?
                            String
                                sc_default_styler 'style-string (string str)
                        else
                            String
                                sc_default_styler 'style-error (string str)
                    else
                        if (not Bbit-set?)
                            String
                                sc_default_styler 'style-comment "-"
                        else
                            String
                                sc_default_styler 'style-error "-"

        let Astr =
            ..
                f"${PC}  ${mnemonic} ${op} ${lo} ${hi}  A:${A} X:${X} "
                f"Y:${Y} P:${P} SP:${SP}  ${flags} CYC:${cyc}"
        io-write! (sc_default_styler 'style-error "<<< ")
        print Astr

        io-write! (sc_default_styler 'style-string ">>> ")
        print theirs

fn parse-log (path)
    using C.stdio
    using C.string
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

        assert (sscanf (reftoptr (line @ 90)) "%llu" &snap.cycles)
        'append logged-state snap
        ;

    fclose fhandle
    deref logged-state

fn take-register-snapshot (cpu)
    let byte = (cpu.mmem @ cpu.PC)
    let mnemonic addrmode =
        build-instruction-switch NES6502 byte
            inline (ins)
                _ ins.mnemonic ins.addrmode

    let optT = (Option u8)
    # next two bytes after opcode
    let b1 b2 = (cpu.mmem @ (cpu.PC + 1)) (cpu.mmem @ (cpu.PC + 2))
    let lo hi =
        match (get-instruction-length addrmode)
        case 1
            _ (optT.None) (optT.None)
        case 2
            _ (optT b1) (optT.None)
        default
            _ (optT b1) (optT b2)

    let mnemonic =
        do
            local cstr : (array i8 4)
            for i c in (enumerate mnemonic)
                cstr @ i = c
            cstr

    RegisterSnapshot
        A  = cpu.RA
        X  = cpu.RX
        Y  = cpu.RY
        PC = cpu.PC
        SP = cpu.RS
        P  = cpu.RP
        opcode = byte
        mnemonic = mnemonic
        operand-low = lo
        operand-high = hi
        cycles = cpu.cycles

fn dump-memory (cpu path)
    using C.stdio
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

    prg-romptr := (reftoptr (rom @ 0x10)) as (@ i8)
    prg-rom-destptr := (reftoptr (cpu.mmem @ 0x8000)) as (mutable@ i8)
    # NOTE: we know this ROM is only 16kb, so I'm just hardcoding this for now.
    memcpy prg-rom-destptr prg-romptr prg-rom-size
    prg-rom-destptr := (reftoptr (cpu.mmem @ 0xc000)) as (mutable@ i8)
    memcpy prg-rom-destptr prg-romptr prg-rom-size

nestest-path := module-dir .. "/../validation/nes-test-roms/other/nestest.nes"
nestest-log-path := module-dir .. "/../validation/nes-test-roms/other/nestest.log"
let romdata =
    try
        read-whole-file nestest-path
    else
        print "was unable to open ROM file"
        exit 1

global state : CPUState
load-iNES state romdata

let log-snapshots = (parse-log nestest-log-path)

'power-up state
# for this test we set PC to 0xc000 as instructed by the test documentation (nestest.txt)
state.PC = 0xC000

LOG_EVERY_INSTRUCTION := true

fn log-instruction (snap line)
    let mode =
        build-instruction-switch NES6502 snap.opcode
            inline (ins)
                String
                    tostring ins.addrmode
    print snap "\t" mode "\t" line

for i entry in (enumerate log-snapshots)
    using import strfmt
    using import testing
    let current = (take-register-snapshot state)

    static-if LOG_EVERY_INSTRUCTION
        log-instruction current (i + 1)

    equal? := entry == current
    if (not equal?) (dump-memory state "nestest.dump")
        print "----------------------------------------------------------------------------------"
        RegisterSnapshot.display-diff current entry
        static-if (not LOG_EVERY_INSTRUCTION)
            log-instruction current (i + 1)
    test equal?
        string
            f"entry ${i + 1} didn't match CPU state!"
    'step-instruction state
    ;

print "Validation succesful!"
dump-memory state "nestest.dump"

none
