using import struct

using import .instruction-set
using import .helpers
using import .nes-common

""""This type is used in `absolute` addressing modes to represent
    a 16-bit wide operand that can be both a literal u16 memory location,
    or a reference to this location in cpu memory. Whenever assigned to it will
    store the value on mmem @ addr; if implied to u8 will become the value at mem @ addr.
    This means that on instructions that can be written with absolute addressing mode,
    operations with `operand` must observe these rules.
    Since it needs to be able to address mapped memory, it stores a pointer to it. It's never
    meant to survive past the instruction execution, so it _should_ never become stale. To get
    as much safety as we can in this situation we assert that the mmem has been resized
    so that the full 64Kb are addressable.
struct AbsoluteOperand plain
    addr    : u16
    mmemptr : (mutable@ u8)
    clock : (mutable@ u64)
    inline __typecall (cls addr cpu)
        super-type.__typecall cls
            addr = addr
            mmemptr = (reftoptr (cpu.mmem._items @ addr))
            clock = &cpu.cycles

    inline __= (lhsT rhsT)
        static-if (rhsT < integer)
            inline (lhs rhs)
                @lhs.clock += 1
                @lhs.mmemptr = (rhs as u8)

    inline __imply (lhsT rhsT)
        static-if (rhsT == ProgramCounter)
            inline (self)
                self.addr as ProgramCounter
        elseif (rhsT < integer)
            inline (self)
                imply (deref @self.mmemptr) rhsT

    inline __toref (self)
        @self.clock += 1
        imply @self.mmemptr u8

typedef ConstantOperand <: u8
    inline __toref (self)
        deref self

struct MemoryOperand plain
    clockptr : (mutable@ u64)
    memptr   : (mutable@ u8)

    inline __typecall (cls addr cpu)
        super-type.__typecall cls
            clockptr = &cpu.cycles
            memptr = (reftoptr (cpu.mmem @ addr))
    inline __= (clsT otherT)
        inline (lhs rhs)
            (ptrtoref lhs.clockptr) += 1
            (ptrtoref lhs.memptr) = (rhs as u8)

    inline __imply (clsT otherT)
        static-if (otherT < integer)
            inline (self)
                imply (deref @self.memptr) otherT

    inline __toref (self)
        (ptrtoref self.clockptr) += 1
        imply (deref @self.memptr) u8

inline page-crossed? (prev next)
    oh := prev >> 8
    nh := next >> 8
    oh != nh

inline branch-relative (pc operand cycles)
    next := pc + operand
    if (page-crossed? pc next)
        cycles += 1
    pc += operand
    cycles += 1

inline implicit (cpu lo hi)
    ;

inline accumulator (cpu lo hi)
    cpu.RA

inline immediate (cpu lo hi)
    ConstantOperand lo

inline zero-page (cpu lo hi)
    MemoryOperand (joinLE lo 0x00) cpu

inline zero-pageX (cpu lo hi)
    cpu.cycles += 1
    MemoryOperand (joinLE (lo + cpu.RX) 0x00) cpu

inline zero-pageY (cpu lo hi)
    cpu.cycles += 1
    MemoryOperand (joinLE (lo + cpu.RY) 0x00) cpu

inline relative (cpu lo hi)
    lo as i8

inline absolute (cpu lo hi)
    # one extra cycle to fetch the high byte
    cpu.cycles += 1
    AbsoluteOperand (joinLE lo hi) cpu

inline absoluteX (cpu lo hi write?)
    real-addr := (joinLE lo hi) + cpu.RX
    cpu.cycles += 1
    static-if write?
        # while writing, we always spend a cycle to correct hi
        cpu.cycles += 1
    else
        # did we cross a page?
        if (((real-addr & 0xff00) >> 8) != hi)
            cpu.cycles += 1
    AbsoluteOperand ((joinLE lo hi) + cpu.RX) cpu

inline absoluteY (cpu lo hi write?)
    real-addr := (joinLE lo hi) + cpu.RY
    cpu.cycles += 1
    static-if write?
        # while writing, we always spend a cycle to correct hi
        cpu.cycles += 1
    else
        # did we cross a page?
        if (((real-addr & 0xff00) >> 8) != hi)
            cpu.cycles += 1
    AbsoluteOperand real-addr cpu

inline indirect (cpu lo hi)
    # http://obelisk.me.uk/6502/reference.html#JMP
    # here we simulate the indirect fetch bug that makes it so that
    # if the MSB is on another page, it's actually fetched from the beginning
    # of the page.
    let cross-page? = (lo == 0xFF)
    let iaddr = (joinLE lo hi)
    let rl rh =
        cpu.mmem @ iaddr
        cpu.mmem @ (? cross-page? (joinLE 0x00 hi) (iaddr + 1))
    # only JMP uses this, so we can sorta hardcode how many cycles it takes
    cpu.cycles += 3
    # return the location stored at this address
    AbsoluteOperand (joinLE rl rh) cpu

inline indirectX (cpu lo hi)
    iaddr := (joinLE (lo + cpu.RX) 0x00) as u8 # ensure wrap around
    let rl rh = (cpu.mmem @ iaddr) (cpu.mmem @ (iaddr + 1))
    # 3    pointer    R  read from the address, add X to it
    # 4   pointer+X   R  fetch effective address low
    # 5  pointer+X+1  R  fetch effective address high
    cpu.cycles += 3
    MemoryOperand (joinLE rl rh) cpu

inline indirectY (cpu lo hi write?)
    iaddr := ((joinLE lo 0x00) as u8)
    let rl rh = (cpu.mmem @ iaddr) (cpu.mmem @ (iaddr + 1))
    real-addr := (joinLE rl rh) + cpu.RY
    cpu.cycles += 2
    static-if write?
        # while writing, we always spend a cycle to correct hi
        cpu.cycles += 1
    else
        # did we cross a page?
        if (((real-addr & 0xff00) >> 8) != rh)
            cpu.cycles += 1
    MemoryOperand real-addr cpu


instruction-set NES6502
    with-header
        inline fset? (...)
            'flag-set? cpu ...
        inline fset (...)
            'set-flag cpu ...
        inline push-stack (...)
            'push-stack cpu ...
        inline pull-stack (...)
            'pull-stack cpu ...

        let acc = cpu.RA
        let rx  = cpu.RX
        let ry  = cpu.RY
        let pc  = cpu.PC
        let sp  = cpu.RS
        let rp  = cpu.RP
        let cycles = cpu.cycles

        let NF = StatusFlag.Negative
        let VF = StatusFlag.Overflow
        let BF = StatusFlag.Break
        let DF = StatusFlag.Decimal
        let IF = StatusFlag.InterruptDisable
        let ZF = StatusFlag.Zero
        let CF = StatusFlag.Carry

    # Add with Carry
    instruction ADC
        0x69 -> immediate
        0x65 -> zero-page
        0x75 -> zero-pageX
        0x6D -> absolute
        0x7D -> absoluteX
        0x79 -> absoluteY
        0x61 -> indirectX
        0x71 -> indirectY
    execute
        # fetch
        operand := @operand
        carry  := (? (fset? CF) 1:u8 0:u8)
        result := acc + operand + carry
        fset VF (((acc ^ result) & (operand ^ result)) & 0x80)
        fset CF (result < acc)
        acc = result
        fset ZF (acc == 0)
        fset NF (acc & 0x80)

    # Logical AND
    instruction AND
        0x29 -> immediate
        0x25 -> zero-page
        0x35 -> zero-pageX
        0x2D -> absolute
        0x3D -> absoluteX
        0x39 -> absoluteY
        0x21 -> indirectX
        0x31 -> indirectY
    execute
        acc &= @operand
        fset ZF (acc == 0)
        fset NF (acc & 0x80)

    # Arithmetic Shift Left
    instruction ASL
        0x0A -> accumulator
        0x06 -> zero-page
        0x16 -> zero-pageX
        0x0E -> absolute
        0x1E -> absoluteX true
    execute
        value := @operand
        fset CF (value & 0x80)
        @operand
        operand <<= 1
        fset ZF (operand == 0)
        fset NF (operand & 0x80)

    # Branch if Carry Clear
    instruction BCC
        0x90 -> relative
    execute
        if (not (fset? CF))
            branch-relative pc operand cycles

    # Branch if Carry Set
    instruction BCS
        0xB0 -> relative
    execute
        if (fset? CF)
            branch-relative pc operand cycles

    # Branch if Equal
    instruction BEQ
        0xF0 -> relative
    execute
        if (fset? ZF)
            branch-relative pc operand cycles
    # Bit Test
    instruction BIT
        0x24 -> zero-page
        0x2C -> absolute
    execute
        operand := @operand
        fset ZF (not (acc & operand))
        fset NF (operand & 0x80) # 7
        fset VF (operand & 0x40) # 6

    # Branch if Minus
    instruction BMI
        0x30 -> relative
    execute
        if (fset? NF)
            branch-relative pc operand cycles

    # Branch if Not Equal
    instruction BNE
        0xD0 -> relative
    execute
        if (not (fset? ZF))
            branch-relative pc operand cycles

    # Branch if Positive
    instruction BPL
        0x10 -> relative
    execute
        if (not (fset? NF))
            branch-relative pc operand cycles

    # Branch if Overflow Clear
    instruction BVC
        0x50 -> relative
    execute
        if (not (fset? VF))
            branch-relative pc operand cycles

    # Branch if Overflow Set
    instruction BVS
        0x70 -> relative
    execute
        if (fset? VF)
            branch-relative pc operand cycles

    # Clear Carry Flag
    instruction CLC
        0x18 -> implicit
    execute
        fset CF false

    # Clear Decimal Mode
    instruction CLD
        0xD8 -> implicit
    execute
        fset DF false

    # Clear Overflow Flag
    instruction CLV
        0xB8 -> implicit
    execute
        fset VF false

    # Compare
    instruction CMP
        0xC9 -> immediate
        0xC5 -> zero-page
        0xD5 -> zero-pageX
        0xCD -> absolute
        0xDD -> absoluteX
        0xD9 -> absoluteY
        0xC1 -> indirectX
        0xD1 -> indirectY
    execute
        # fetch
        operand := @operand
        fset CF (acc >= operand)
        fset ZF (acc == operand)
        fset NF ((acc - operand) & 0x80)

    # Compare X Register
    instruction CPX
        0xE0 -> immediate
        0xE4 -> zero-page
        0xEC -> absolute
    execute
        # fetch
        operand := @operand
        fset CF (rx >= operand)
        fset ZF (rx == operand)
        fset NF ((rx - operand) & 0x80)

    # Compare Y Register
    instruction CPY
        0xC0 -> immediate
        0xC4 -> zero-page
        0xCC -> absolute
    execute
        # fetch
        operand := @operand
        fset CF (ry >= operand)
        fset ZF (ry == operand)
        fset NF ((ry - operand) & 0x80)

    # Decrement then Compare
    # this instruction is undocumented
    instruction DCP
        0xC3 -> indirectX
        0xC7 -> zero-page
        0xCF -> absolute
        0xD3 -> indirectY
        0xD7 -> zero-pageX
        0xDB -> absoluteY
        0xDF -> absoluteX
    execute
        # RMW quirk
        operand = @operand
        operand -= 1
        fset CF (acc >= operand)
        fset ZF (acc == operand)
        fset NF ((acc - operand) & 0x80)

    # Decrement Memory
    instruction DEC
        0xC6 -> zero-page
        0xD6 -> zero-pageX
        0xCE -> absolute
        0xDE -> absoluteX true
    execute
        operand = @operand
        operand -= 1
        fset ZF (operand == 0)
        fset NF (operand & 0x80)

    # Decrement X Register
    instruction DEX
        0xCA -> implicit
    execute
        rx -= 1
        fset ZF (rx == 0)
        fset NF (rx & 0x80)

    # Decrement Y Register
    instruction DEY
        0x88 -> implicit
    execute
        ry -= 1
        fset ZF (ry == 0)
        fset NF (ry & 0x80)

    # Exclusive OR
    instruction EOR
        0x49 -> immediate
        0x45 -> zero-page
        0x55 -> zero-pageX
        0x4D -> absolute
        0x5D -> absoluteX
        0x59 -> absoluteY
        0x41 -> indirectX
        0x51 -> indirectY
    execute
        acc ^= @operand
        fset ZF (acc == 0)
        fset NF (acc & 0x80)

    # Increment Memory
    instruction INC
        0xE6 -> zero-page
        0xF6 -> zero-pageX
        0xEE -> absolute
        0xFE -> absoluteX true
    execute
        operand = @operand
        operand += 1
        fset ZF (operand == 0)
        fset NF (operand & 0x80)

    # Increment X Register
    instruction INX
        0xE8 -> implicit
    execute
        rx += 1
        fset ZF (rx == 0)
        fset NF (rx & 0x80)

    # Increment Y Register
    instruction INY
        0xC8 -> implicit
    execute
        ry += 1
        fset ZF (ry == 0)
        fset NF (ry & 0x80)

    # INC+SBC
    # this instruction is undocumented
    instruction ISB
        0xE3 -> indirectX
        0xE7 -> zero-page
        0xEF -> absolute
        0xF3 -> indirectY
        0xF7 -> zero-pageX
        0xFB -> absoluteY
        0xFF -> absoluteX
    execute
        # RMW quirk
        operand = @operand
        operand += 1
        carry := (? (fset? CF) 1:u8 0:u8)
        twos  := (~ (imply operand u8)) + 1:u8
        old   := (deref acc)
        oldp? := old < 128

        acc += twos + (carry - 1:u8)
        fset VF
            if (operand < 128)
                ? oldp? ((acc > old) and (acc < 128)) (acc < 128)
            else
                ? oldp? ((acc < old) and (acc > 128)) (acc > 128)
        fset CF (acc <= old)
        fset ZF (acc == 0)
        fset NF (acc & 0x80)

    # Jump
    instruction JMP
        0x4C -> absolute
        0x6C -> indirect
    execute
        pc = operand

    # Jump to Subroutine
    instruction JSR
        0x20 -> absolute
    execute
        push-stack (pc - 1)
        cycles += 3
        pc = operand

    # Load A and X simultaneously
    # this instruction is undocumented.
    instruction LAX
        0xA3 -> indirectX
        0xA7 -> zero-page
        0xAF -> absolute
        0xB3 -> indirectY
        0xB7 -> zero-pageY
        0xBF -> absoluteY
    execute
        acc = @operand
        rx = acc
        fset ZF (rx == 0)
        fset NF (rx & 0x80)

    # Load Accumulator
    instruction LDA
        0xA9 -> immediate
        0xA5 -> zero-page
        0xB5 -> zero-pageX
        0xAD -> absolute
        0xBD -> absoluteX
        0xB9 -> absoluteY
        0xA1 -> indirectX
        0xB1 -> indirectY
    execute
        acc = @operand
        fset ZF (acc == 0)
        fset NF (acc & 0x80)

    # Load X Register
    instruction LDX
        0xA2 -> immediate
        0xA6 -> zero-page
        0xB6 -> zero-pageY
        0xAE -> absolute
        0xBE -> absoluteY
    execute
        rx = @operand
        fset ZF (rx == 0)
        fset NF (rx & 0x80)

    # Load Y Register
    instruction LDY
        0xA0 -> immediate
        0xA4 -> zero-page
        0xB4 -> zero-pageX
        0xAC -> absolute
        0xBC -> absoluteX
    execute
        ry = @operand
        fset ZF (ry == 0)
        fset NF (ry & 0x80)

    # Logical Shift Right
    instruction LSR
        0x4A -> accumulator
        0x46 -> zero-page
        0x56 -> zero-pageX
        0x4E -> absolute
        0x5E -> absoluteX true
    execute
        fset CF (operand & 0x1)
        operand = @operand
        operand >>= 1
        fset ZF (operand == 0)
        fset NF (operand & 0x80)

    # No Operation
    instruction NOP
        0xEA -> implicit
        0x04 -> zero-page # undocumented
        0x44 -> zero-page # undocumented
        0x64 -> zero-page # undocumented
        0x0C -> absolute # undocumented
        0x14 -> zero-pageX # undocumented
        0x34 -> zero-pageX # undocumented
        0x54 -> zero-pageX # undocumented
        0x74 -> zero-pageX # undocumented
        0xD4 -> zero-pageX # undocumented
        0xF4 -> zero-pageX # undocumented
        0x1A -> implicit # undocumented
        0x3A -> implicit # undocumented
        0x5A -> implicit # undocumented
        0x7A -> implicit # undocumented
        0xDA -> implicit # undocumented
        0xFA -> implicit # undocumented
        0x80 -> immediate # undocumented
        0x1C -> absoluteX # undocumented
        0x3C -> absoluteX # undocumented
        0x5C -> absoluteX # undocumented
        0x7C -> absoluteX # undocumented
        0xDC -> absoluteX # undocumented
        0xFC -> absoluteX # undocumented
    execute
        static-if (not (none? operand))
            # dummy read!
            @operand
        ;

    # Logical Inclusive OR
    instruction ORA
        0x09 -> immediate
        0x05 -> zero-page
        0x15 -> zero-pageX
        0x0D -> absolute
        0x1D -> absoluteX
        0x19 -> absoluteY
        0x01 -> indirectX
        0x11 -> indirectY
    execute
        acc |= @operand
        fset ZF (acc == 0)
        fset NF (acc & 0x80)

    # Push Accumulator
    instruction PHA
        0x48 -> implicit
    execute
        push-stack acc
        # push register, decrement S
        cycles += 1

    # Push Processor Status
    instruction PHP
        0x08 -> implicit
    execute
        # http://wiki.nesdev.com/w/index.php/Status_flags
        # See "The B Flag"
        push-stack (rp | 0x30)
        # push register, decrement S
        cycles += 1

    # Pull Accumulator
    instruction PLA
        0x68 -> implicit
    execute
        acc = (pull-stack 1)
        # +2 for stack manipulation
        cycles += 2
        fset ZF (acc == 0)
        fset NF (acc & 0x80)

    # Pull Processor Status
    instruction PLP
        0x28 -> implicit
    execute
        # bits 4 and 5 have to remain the same
        mask := (rp & 0x30:u8)
        svalue := (pull-stack 1)
        # +2 for stack manipulation
        cycles += 2
        rp = ((svalue & (~ 0x30:u8)) | mask)
        ;

    # ROL then AND
    # this instruction is undocumented
    instruction RLA
        0x23 -> indirectX
        0x27 -> zero-page
        0x2F -> absolute
        0x33 -> indirectY
        0x37 -> zero-pageX
        0x3B -> absoluteY
        0x3F -> absoluteX
    execute
        let carry = (? (fset? CF) 1:u8 0:u8)
        fset CF (operand & 0x80)
        # RMW quirk
        operand = @operand
        operand = ((operand << 1) | carry)
        acc &= operand
        fset ZF (acc == 0)
        fset NF (acc & 0x80)

    # Rotate Left
    instruction ROL
        0x2A -> accumulator
        0x26 -> zero-page
        0x36 -> zero-pageX
        0x2E -> absolute
        0x3E -> absoluteX true
    execute
        let carry = (? (fset? CF) 1:u8 0:u8)
        fset CF (@operand & 0x80)
        operand <<= 1
        operand |= carry
        fset ZF (operand == 0)
        fset NF (operand & 0x80)

    # Rotate Right
    instruction ROR
        0x6A -> accumulator
        0x66 -> zero-page
        0x76 -> zero-pageX
        0x6E -> absolute
        0x7E -> absoluteX true
    execute
        let carry = (? (fset? CF) 1:u8 0:u8)
        value := @operand
        bit0 := (value & 0x01)
        operand >>= 1
        operand |= (carry << 7)
        fset CF bit0
        fset ZF (operand == 0)
        fset NF (operand & 0x80)

    # ROR then ADC
    # this instruction is undocumented
    instruction RRA
        0x63 -> indirectX
        0x67 -> zero-page
        0x6F -> absolute
        0x73 -> indirectY
        0x77 -> zero-pageX
        0x7B -> absoluteY
        0x7F -> absoluteX
    execute
        # RMW quirk
        operand = @operand
        # ROR
        let carry = (? (fset? CF) 1:u8 0:u8)
        bit0 := (operand & 0x01)
        operand = (operand >> 1) | (carry << 7)
        fset CF bit0
        fset ZF (operand == 0)
        fset NF (operand & 0x80)

        # ADC
        carry  := (? (fset? CF) 1:u8 0:u8)
        result := acc + operand + carry
        fset VF (((acc ^ result) & (operand ^ result)) & 0x80)
        fset CF (result < acc)
        acc = result
        fset ZF (acc == 0)
        fset NF (acc & 0x80)

    # Return from Interrupt
    instruction RTI
        0x40 -> implicit
    execute
        # see PLP
        mask := (rp & 0x30:u8)
        svalue := (pull-stack 1)
        rp = ((svalue & (~ 0x30:u8)) | mask)
        pc = (pull-stack 2)
        # 3  $0100,S  R  increment S
        # 4  $0100,S  R  pull P from stack, increment S
        # 5  $0100,S  R  pull PCL from stack, increment S
        # 6  $0100,S  R  pull PCH from stack
        cycles += 4

    # Return from Subroutine
    instruction RTS
        0x60 -> implicit
    execute
        pc = ((pull-stack 2) + 1)
        # +3 for stack manipulation
        # +1 for incrementing PC
        cycles += 4

    # Store A & X
    # this instruction is undocumented
    instruction SAX
        0x83 -> indirectX
        0x87 -> zero-page
        0x8F -> absolute
        0x97 -> zero-pageY
    execute
        operand = (acc & rx)

    # Subtract with Carry
    instruction SBC
        0xE9 -> immediate
        0xE5 -> zero-page
        0xF5 -> zero-pageX
        0xED -> absolute
        0xFD -> absoluteX
        0xF9 -> absoluteY
        0xE1 -> indirectX
        0xF1 -> indirectY
        0xEB -> immediate # undocumented
    execute
        # NOTE: at this point this differs from ISB, but hasn't failed validation.
        # One of them is certainly wrong, and I think it's this one (possibly both,
        # in different ways).
        carry := (? (fset? CF) 1:u8 0:u8)
        twos  := (~ @operand) + 1:u8
        old   := (deref acc)
        oldp? := old < 128

        acc += twos + (carry - 1:u8)
        fset VF
            ? oldp? ((acc > old) and (acc < 128)) (acc < 128)
        fset CF (acc < old)
        fset ZF (acc == 0)
        fset NF (acc & 0x80)

    # Set Carry Flag
    instruction SEC
        0x38 -> implicit
    execute
        fset CF true

    # Set Decimal Flag
    instruction SED
        0xF8 -> implicit
    execute
        fset DF true

    # Set Interrupt Disable
    instruction SEI
        0x78 -> implicit
    execute
        fset IF true

    # ASL then ORA
    # this instruction is undocumented
    instruction SLO
        0x03 -> indirectX
        0x07 -> zero-page
        0x0F -> absolute
        0x13 -> indirectY
        0x17 -> zero-pageX
        0x1B -> absoluteY
        0x1F -> absoluteX
    execute
        # RMW quirk
        operand = @operand
        fset CF (operand & 0x80)
        operand <<= 1
        acc |= operand
        fset ZF (acc == 0)
        fset NF (acc & 0x80)

    # LSR then EOR
    # this instruction is undocumented
    instruction SRE
        0x43 -> indirectX
        0x47 -> zero-page
        0x4F -> absolute
        0x53 -> indirectY
        0x57 -> zero-pageX
        0x5B -> absoluteY
        0x5F -> absoluteX
    execute
        fset CF (operand & 0x1)
        # RMW quirk
        operand = @operand
        operand >>= 1
        acc ^= operand
        fset ZF (acc == 0)
        fset NF (acc & 0x80)

    # Store Accumulator
    instruction STA
        0x85 -> zero-page
        0x95 -> zero-pageX
        0x8D -> absolute
        0x9D -> absoluteX true
        0x99 -> absoluteY true
        0x81 -> indirectX
        0x91 -> indirectY true
    execute
        operand = acc

    # Store X Register
    instruction STX
        0x86 -> zero-page
        0x96 -> zero-pageY
        0x8E -> absolute
    execute
        operand = rx

    # Store Y Register
    instruction STY
        0x84 -> zero-page
        0x94 -> zero-pageX
        0x8C -> absolute
    execute
        operand = ry

    # Transfer Accumulator to X
    instruction TAX
        0xAA -> implicit
    execute
        rx = acc
        fset ZF (rx == 0)
        fset NF (rx & 0x80)

    # Transfer Accumulator to Y
    instruction TAY
        0xA8 -> implicit
    execute
        ry = acc
        fset ZF (ry == 0)
        fset NF (ry & 0x80)

    # Transfer Stack Pointer to X
    instruction TSX
        0xBA -> implicit
    execute
        rx = sp
        fset ZF (rx == 0)
        fset NF (rx & 0x80)

    # Transfer X to Accumulator
    instruction TXA
        0x8A -> implicit
    execute
        acc = rx
        fset ZF (acc == 0)
        fset NF (acc & 0x80)

    # Transfer X to Stack Pointer
    instruction TXS
        0x9A -> implicit
    execute
        sp = rx

    # Transfer Y to Accumulator
    instruction TYA
        0x98 -> implicit
    execute
        acc = ry
        fset ZF (acc == 0)
        fset NF (acc & 0x80)

do
    let NES6502
    locals;
