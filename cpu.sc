using import struct
using import Array
using import enum

enum StatusFlag : u8
    Negative = 7
    Overflow = 6
    Break = 4
    Decimal = 3
    InterruptDisable = 2
    Zero = 1
    Carry = 0

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

    inline... set-flag (self, flag : StatusFlag, v : bool)
        let flag-bit = (flag as u8)
        if v
            self.RP |= (1:u8 << flag-bit)
        else
            self.RP &= (~ (1:u8 << flag-bit))

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

fn decode-op (op)
    import .opcodes

power-up;
