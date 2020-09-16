using import struct
using import Array

struct CPUState
    # registers
    RA : u8  # accumulator
    RX : u8
    RY : u8
    PC : u16 # program counter
    RS : u8  # stack pointer
    RP : u8  # status

    iRAM : (Array u8 0xFFFF)

    inline __typecall (cls)
        local self = (super-type.__typecall cls)
        'resize self.iRAM 0xFFFF
        move (deref self)

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

    poke 0x4017 0x0
    poke 0x4015 0x0
    for addr in (range 0x4000 (0x400F + 1))
        poke addr 0
    for addr in (range 0x4010 (0x4013 + 1))
        poke addr 0

power-up;
