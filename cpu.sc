using import struct
using import Array
using import enum

using import .common
import .opcodes

global cpu : CPUState

inline poke (addr value)
    cpu.iRAM @ addr = value
    ;

inline peek (addr)
    cpu.iRAM @ addr

fn power-up ()
    # http://wiki.nesdev.com/w/index.php/CPU_power_up_state
    cpu.RP = 0x34
    cpu.RA = 0
    cpu.RX = 0
    cpu.RY = 0
    cpu.RS = 0xFD

power-up;

locals;
