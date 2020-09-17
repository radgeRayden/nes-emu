using import testing
using import .common
import .opcodes
global cpu : CPUState

inline decode (code lo hi)
    local opcode = (opcodes.opcode-table @ code)
    opcode.fun &opcode &cpu lo hi

do
    cpu = (CPUState)
    (decode 0x69 0x10 0x00) # ADC #10
    (decode 0x69 0xFF 0x00) # ADC #FF
    test
        'flag-set? cpu StatusFlag.Carry
