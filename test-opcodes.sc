using import .common
import .opcodes
global cpu : CPUState

inline decode (code lo hi)
    local opcode = (opcodes.opcode-table @ code)
    opcode.fun &opcode &cpu lo hi

# (decode 0x4A 0x10 0x00)
cpu.iRAM @ 0x10 = 0x25
(decode 0x69 0x10 0x00) # ADC #10
(decode 0x69 0xFF 0x00) # ADC #FF
print (hex cpu.RA)
print cpu.RP
