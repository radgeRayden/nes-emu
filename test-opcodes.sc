using import testing
using import .common
import .opcodes
global cpu : CPUState

inline decode (code lo hi)
    local opcode = (opcodes.opcode-table @ code)
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

    (decode 0xA2 0x77 0x00) # LDX #77
    test (cpu.RX == 0x77)

    cpu.mmem @ 0x2242 = 0xBE
    (decode 0xAE 0x42 0x22) # LDX $2242
    test (cpu.RX == 0xBE)

    # zero page:
    cpu.mmem @ 0x0042 = 0x56
    (decode 0xA6 0x42 0xFF) # LDX $42
    test (cpu.RX == 0x56)
