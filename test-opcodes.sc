using import testing
using import .common
import .opcodes
global cpu : CPUState

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

inline decode (code lo hi)
    local opcode = (opcodes.opcode-table @ code)
    print opcode.mnemonic (fmt-hex opcode.byte)
    opcode.fun &opcode &cpu lo hi

# NOTE: these tests are non exhaustive, and meant to test
# features as I implement them. For a thorough test it is necessary
# to use a test ROM.
do
    cpu = (CPUState)
    (decode 0x69 0x10 0x00) # ADC #10
    (decode 0x69 0xFF 0x00) # ADC #FF
    test (cpu.RA == 0x0F)
    test
        'flag-set? cpu StatusFlag.Carry

do
    cpu = (CPUState)

    decode 0xA2 0x77 0x00 # LDX #77
    test (cpu.RX == 0x77)

    cpu.mmem @ 0x2242 = 0xBE
    decode 0xAE 0x42 0x22 # LDX $2242
    test (cpu.RX == 0xBE)

    # zero page:
    cpu.mmem @ 0x0042 = 0x56
    decode 0xA6 0x42 0xFF # LDX $42
    test (cpu.RX == 0x56)

do
    cpu = (CPUState)
    cpu.mmem @ 0x2242 = 0xBE
    decode 0x4E 0x42 0x22 # LSR $2242
    test ((cpu.mmem @ 0x2242) == 0x5F)

do
    cpu = (CPUState)
    print "Easy 6502"
    local program =
        arrayof u8 0xa9 0x01 0x8d 0x00 0x02 0xa9 0x05 0x8d 0x01 0x02 0xa9 0x08 0x8d 0x02 0x02

    inline fetch ()
        pc := cpu.PC
        op := program @ pc
        let lo =
            ? ((pc + 1) < (countof program)) (program @ (pc + 1)) 0x00:u8
        let hi =
            ? ((pc + 2) < (countof program)) (program @ (pc + 2)) 0x00:u8
        _ op lo hi

    loop ()
        if (cpu.PC >= (countof program))
            print "finished" (fmt-hex cpu.PC) (fmt-hex cpu.RA)
            break;
        decode (fetch)
