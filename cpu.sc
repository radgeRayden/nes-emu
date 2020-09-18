using import struct
using import enum
using import Array

using import .helpers

enum StatusFlag plain
    Negative = 7
    Overflow = 6
    Break = 4
    Decimal = 3
    InterruptDisable = 2
    Zero = 1
    Carry = 0

typedef MemoryAddress <: u16

struct CPUState
    # registers
    RA : u8  # accumulator
    RX : u8
    RY : u8
    PC : MemoryAddress # program counter
    RS : u8  # stack pointer
    RP : u8  # status

    mmem : (Array u8 0xFFFF)

    inline __typecall (cls)
        local self = (super-type.__typecall cls)
        'resize self.mmem 0xFFFF
        move (deref self)

    inline... set-flag (self, flag : StatusFlag, v : bool)
        let flag-bit = (flag as u8)
        if v
            self.RP |= (1:u8 << flag-bit)
        else
            self.RP &= (~ (1:u8 << flag-bit))

    inline flag-set? (self flag)
        (self.RP & (1:u8 << (flag as u8))) as bool

do
    let CPUState StatusFlag MemoryAddress
    locals;
