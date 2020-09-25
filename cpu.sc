using import struct
using import enum
using import Array

using import .helpers
using import .nes-common
using import .6502-instruction-set

struct CPUState
    # registers
    RA : ByteRegister  # accumulator
    RX : ByteRegister
    RY : ByteRegister
    PC : ProgramCounter # program counter
    RS : ByteRegister  # stack pointer
    RP : ByteRegister  # status

    let AddressableMemorySize = (0xFFFF + 1)
    let MappedMemoryT = (Array u8 AddressableMemorySize)
    mmem : MappedMemoryT

    cycles : u64

    inline __typecall (cls)
        local mmem = (MappedMemoryT)
        'resize mmem ('capacity mmem)

        super-type.__typecall cls
            mmem = mmem

    fn power-up (self)
        self.RS = 0xFD
        let pcl pch = (self.mmem @ 0xFFFC) (self.mmem @ 0xFFFD)
        self.PC = (joinLE pcl pch)
        self.RP = 0x24
        # starts at 7 because of some init work the cpu does
        self.cycles = 7

    inline... set-flag (self, flag : StatusFlag, v : bool)
        let flag-bit = (flag as u8)
        if v
            self.RP |= (1:u8 << flag-bit)
        else
            self.RP &= (~ (1:u8 << flag-bit))

    inline flag-set? (self flag)
        (self.RP & (1:u8 << (flag as u8))) as bool

    # the stack pointer indicates where the stack can write *next*;
    # this means that we write to the position where sp is, then decrement it,
    # and pull from sp + 1.
    # https://wiki.nesdev.com/w/index.php/Stack
    inline push-stack (self v)
        let vT = (typeof v)
        sp := self.RS
        static-if ((storageof vT) == u16)
            let lo hi = (separateLE v)
            let idx = (joinLE sp 0x01)
            self.mmem @ (idx - 1) = lo
            self.mmem @ idx = hi
            sp -= 2
            ;
        else
            let idx = (joinLE sp 0x01)
            self.mmem @ idx = (imply v u8)
            sp -= 1
            ;

    inline pull-stack (self n)
        sp := self.RS
        let idx = ((joinLE sp 0x01) + 1)
        static-match n
        case 1
            result := self.mmem @ idx
            sp += 1
            result
        case 2
            let result = (joinLE (self.mmem @ idx) (self.mmem @ (idx + 1)))
            sp += 2
            result
        default
            static-error "must always pull one or two bytes from the stack"

    fn step-instruction (self)
        pc := self.PC
        # NOTE: we don't do range checking here because pc is
        # only 16-bits wide, which gets us the desired behaviour of
        # wrapping back to 0 if it's incremented too much.
        op := self.mmem @ pc
        lo := self.mmem @ (pc + 1)
        hi := self.mmem @ (pc + 2)
        # instructions take at least 2 cycles
        self.cycles += 2

        using import .instruction-set
        build-instruction-switch NES6502 op
            inline (ins)
                pc += ((get-instruction-length ins.addrmode) as u16)
                ins.fun self lo hi
        ;

do
    let CPUState StatusFlag
    locals;
