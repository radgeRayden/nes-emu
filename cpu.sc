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

    let AddressableMemorySize = (0xFFFF + 1)
    mmem : (Array u8 AddressableMemorySize)

    inline __typecall (cls)
        local self = (super-type.__typecall cls)
        'resize self.mmem ('capacity self.mmem)
        # set power up state
        self.RS = 0xFD
        # FIXME: this is very likely incorrect, I
        # just set it to point to start of PRG ROM.
        self.PC = 0x8000
        self.RP = 0x24
        deref self

    inline... set-flag (self, flag : StatusFlag, v : bool)
        let flag-bit = (flag as u8)
        if v
            self.RP |= (1:u8 << flag-bit)
        else
            self.RP &= (~ (1:u8 << flag-bit))

    inline flag-set? (self flag)
        (self.RP & (1:u8 << (flag as u8))) as bool

    fn push-stack (self v)
        let vT = (typeof v)
        sp := self.RS
        static-if (imply? vT u16)
            let lo hi = (separateLE v)
            sp -= 2
            let idx = (joinLE sp 0x01)
            self.mmem @ idx = lo
            self.mmem @ (idx + 1) = hi
            ;
        else
            sp -= 1
            let idx = (joinLE sp 0x01)
            self.mmem @ idx = (imply v u8)
            ;

    fn next (self optable)
        pc := self.PC
        # NOTE: we don't do range checking here because pc is
        # only 16-bits wide, which gets us the desired behaviour of
        # wrapping back to 0 if it's incremented too much.
        op := self.mmem @ pc
        lo := self.mmem @ (pc + 1)
        hi := self.mmem @ (pc + 2)
        instruction := optable @ op
        'execute instruction self lo hi
        ;


do
    let CPUState StatusFlag MemoryAddress
    locals;
