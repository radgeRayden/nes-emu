using import radlib.core-extensions

using import struct
using import enum
using import Array

using import .cpu
using import .helpers

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
struct AbsoluteOperand
    addr    : u16
    mmemptr : (mutable@ u8)
    inline __typecall (cls addr cpu)
        assert ((countof cpu.mmem) == CPUState.AddressableMemorySize)
        super-type.__typecall cls
            addr = addr
            mmemptr = cpu.mmem._items

    inline __= (lhsT rhsT)
        static-if (rhsT < integer)
            inline (lhs rhs)
                lhs.mmemptr @ lhs.addr = (rhs as u8)

    inline __imply (lhsT rhsT)
        static-if (rhsT == MemoryAddress)
            inline (self)
                self.addr as MemoryAddress
        elseif (imply? u8 rhsT)
            inline (self)
                imply (self.mmemptr @ self.addr) rhsT

""""The instruction wrapper:
    Takes the Instruction itself (for debugging purposes), a view of the cpu state so it
    can mutate it and the next two bytes after the opcode in memory,
    which can form a full or partial operand depending on the addressing mode.
inline make-instruction-fpT (T)
    # void <- _opcode, cpu-state, low, high
    pointer (function void (pointer T) (viewof (mutable@ CPUState) 2) u8 u8)

enum AddressingMode plain
    Implicit = 'implicit
    Accumulator = 'accumulator
    Immediate = 'immediate
    ZeroPage = 'zero-page
    ZeroPageX = 'zero-pageX
    ZeroPageY = 'zero-pageY
    Relative = 'relative
    Absolute = 'absolute
    AbsoluteX = 'absoluteX
    AbsoluteY = 'absoluteY
    Indirect = 'indirect
    IndirectX = 'indirectX
    IndirectY = 'indirectY

    inline __rimply (lhsT rhsT)
        static-if (lhsT == Symbol)
            inline (other)
                (storagecast other) as i32 as this-type
        else
            super-type.__rimply lhsT rhsT


fn get-instruction-length (addrmode)
    switch (addrmode as AddressingMode)
    case AddressingMode.Implicit
        1
    case AddressingMode.Accumulator
        1
    case AddressingMode.Immediate
        2
    case AddressingMode.ZeroPage
        2
    case AddressingMode.ZeroPageX
        2
    case AddressingMode.ZeroPageY
        2
    case AddressingMode.Relative
        2
    case AddressingMode.Absolute
        3
    case AddressingMode.AbsoluteX
        3
    case AddressingMode.AbsoluteY
        3
    case AddressingMode.Indirect
        3
    case AddressingMode.IndirectX
        2
    case AddressingMode.IndirectY
        2
    default
        unreachable;

struct Instruction plain
    byte     : u8
    mnemonic : string
    addrmode : AddressingMode
    fun      : (make-instruction-fpT this-type)

    fn execute (self cpu lo hi)
        self.fun &self &cpu lo hi

    fn length (self)
        get-instruction-length self.addrmode

fn NYI-instruction (_opcode cpu low high)
    cpu.PC += 1
    print "this instruction is illegal or not yet implemented." (hex _opcode.byte)
    ;

global opcode-table : (Array Instruction)
for i in (range 256)
    'append opcode-table
        Instruction
            byte = (i as u8)
            mnemonic = "NYI"
            addrmode = AddressingMode.Implicit
            fun = NYI-instruction

define-scope operand-routers
    # opcodes that allow implicit only allow implicit, so we don't have
    # to do anything special.
    inline implicit (cpu lo hi)
        ;

    inline accumulator (cpu lo hi)
        cpu.RA

    inline immediate (cpu lo hi)
        lo

    inline zero-page (cpu lo hi)
        cpu.mmem @ (joinLE lo 0x00)

    inline zero-pageX (cpu lo hi)
        cpu.mmem @ (joinLE (lo + cpu.RX) 0x00)

    inline zero-pageY (cpu lo hi)
        cpu.mmem @ (joinLE (lo + cpu.RY) 0x00)

    inline relative (cpu lo hi)
        lo as i8

    inline absolute (cpu lo hi)
        AbsoluteOperand (joinLE lo hi) cpu

    inline absoluteX (cpu lo hi)
        AbsoluteOperand ((joinLE lo hi) + cpu.RX) cpu

    inline absoluteY (cpu lo hi)
        AbsoluteOperand ((joinLE lo hi) + cpu.RY) cpu

    inline indirect (cpu lo hi)
        # fetch 2 bytes at the indirect address provided
        iaddr := (joinLE lo hi)
        let rl rh = (cpu.mmem @ iaddr) (cpu.mmem @ (iaddr + 1))
        # return the location stored at this address
        MemoryAddress (joinLE rl rh)

    inline indirectX (cpu lo hi)
        iaddr := (joinLE (lo + cpu.RX) 0x00)
        let rl rh = (cpu.mmem @ iaddr) (cpu.mmem @ (iaddr + 1))
        AbsoluteOperand (joinLE rl rh) cpu

    inline indirectY (cpu lo hi)
        iaddr := (joinLE lo 0x00)
        let rl rh = (cpu.mmem @ iaddr) (cpu.mmem @ (iaddr + 1))
        AbsoluteOperand ((joinLE rl rh) + cpu.RY) cpu

sugar instruction (mnemonic opcodes...)
    let mnemonic = (mnemonic as Symbol as string)
    let next rest = (decons next-expr)
    if (('typeof next) != list)
        error "expected an `execute` block"

    let instruction-code =
        sugar-match (next as list)
        case ('execute body...)
            body...
        default
            error "expected an `execute` block"

    inline build-instruction-function (addressing-mode)
        let router =
            try
                '@ operand-routers (addressing-mode as Symbol)
            except (ex)
                error
                    .. "unrecognized addressing mode `"
                        (tostring addressing-mode)
                        "`, see documentation"

        instruction-length := (get-instruction-length (addressing-mode as Symbol))

        qq
            fn (_opcode cpu lo hi)
                [let] cpu = ([ptrtoref] cpu)
                cpu.PC += [instruction-length]

                # there's probably a way neater way to do this!
                [inline] wrap-helper (helper)
                    [inline] (...)
                        helper cpu ...

                [let] fset? = (wrap-helper [CPUState.flag-set?])
                [let] fset = (wrap-helper [CPUState.set-flag])
                [let] push-stack = (wrap-helper [CPUState.push-stack])
                [let] pull-stack = (wrap-helper [CPUState.pull-stack])

                [let] acc = cpu.RA
                [let] rx  = cpu.RX
                [let] ry  = cpu.RY
                [let] pc  = cpu.PC
                [let] sp  = cpu.RS
                [let] rp  = cpu.RP

                [let] NF = StatusFlag.Negative
                [let] VF = StatusFlag.Overflow
                [let] BF = StatusFlag.Break
                [let] DF = StatusFlag.Decimal
                [let] IF = StatusFlag.InterruptDisable
                [let] ZF = StatusFlag.Zero
                [let] CF = StatusFlag.Carry

                [let] operand = ([router] cpu lo hi)
                [unlet] cpu lo hi wrap-helper

                unquote-splice instruction-code

    inline gen-opcode-table-entry (opcode mnemonic addrmode fun)
        opcode-table @ (opcode as u8) =
            Instruction
                byte = (opcode as u8)
                fun = fun
                addrmode = addrmode
                mnemonic = mnemonic

    let result =
        fold (result = '()) for op in (opcodes... as list)
            sugar-match (op as list)
            case ((code as i32) '-> addressing-mode)
                cons
                    qq
                        [gen-opcode-table-entry] [code] [mnemonic] (sugar-quote [addressing-mode])
                            [(build-instruction-function addressing-mode)]
                    result

            default
                error "incorrect syntax. Should be [opcode] -> [addressing-mode]"
    _ (cons 'embed result) rest

run-stage;

# we need to do this in a function so it also works AOT, otherwise it
# will be compile-time only.
fn init-instructions ()
    """"Add with Carry
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
        carry  := (? (fset? CF) 1:u8 0:u8)
        result := acc + operand + carry
        fset VF (((acc ^ result) & (operand ^ result)) & 0x80)
        fset CF (result < acc)
        acc = result
        fset ZF (acc == 0)
        fset NF (acc & 0x80)

    """"Logical AND
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
        acc &= operand
        fset ZF (acc == 0)
        fset NF (acc & 0x80)

    """"Branch if Carry Clear
    instruction BCC
        0x90 -> relative
    execute
        if (not (fset? CF))
            pc += operand

    """"Branch if Carry Set
    instruction BCS
        0xB0 -> relative
    execute
        if (fset? CF)
            pc += operand

    """"Branch if Equal
    instruction BEQ
        0xF0 -> relative
    execute
        if (fset? ZF)
            pc += operand

    """"Bit Test
    instruction BIT
        0x24 -> zero-page
        0x2C -> absolute
    execute
        fset ZF (not (acc & operand))
        fset NF (operand & 0x80) # 7
        fset VF (operand & 0x40) # 6

    """"Branch if Minus
    instruction BMI
        0x30 -> relative
    execute
        if (fset? NF)
            pc += operand

    """"Branch if Not Equal
    instruction BNE
        0xD0 -> relative
    execute
        if (not (fset? ZF))
            pc += operand
    """"Branch if Positive
    instruction BPL
        0x10 -> relative
    execute
        if (not (fset? NF))
            pc += operand

    """"Branch if Overflow Clear
    instruction BVC
        0x50 -> relative
    execute
        if (not (fset? VF))
            pc += operand

    """"Branch if Overflow Set
    instruction BVS
        0x70 -> relative
    execute
        if (fset? VF)
            pc += operand

    """"Clear Carry Flag
    instruction CLC
        0x18 -> implicit
    execute
        fset CF false

    """"Clear Decimal Mode
    instruction CLD
        0xD8 -> implicit
    execute
        fset DF false

    """"Clear Overflow Flag
    instruction CLV
        0xB8 -> implicit
    execute
        fset VF false

    """"Compare
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
        fset CF (acc >= operand)
        fset ZF (acc == operand)
        fset NF ((acc - operand) & 0x80)
    
    """"Exclusive OR
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
        acc ^= operand
        fset ZF (acc == 0)
        fset NF (acc & 0x80)

    """"Jump
    instruction JMP
        0x4C -> absolute
        0x6C -> indirect
    execute
        pc = operand

    """"Jump to Subroutine
    instruction JSR
        0x20 -> absolute
    execute
        push-stack (pc - 1)
        pc = operand

    """"Load Accumulator
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
        acc = operand
        fset ZF (acc == 0)
        fset NF (acc & 0x80)

    """"Load X Register
    instruction LDX
        0xA2 -> immediate
        0xA6 -> zero-page
        0xB6 -> zero-pageY
        0xAE -> absolute
        0xBE -> absoluteY
    execute
        rx = operand
        fset ZF (rx == 0)
        fset NF (rx & 0x80)

    """"Logical Shift Right
    instruction LSR
        0x4A -> accumulator
        0x46 -> zero-page
        0x56 -> zero-pageX
        0x4E -> absolute
        0x5E -> absoluteX
    execute
        fset CF (operand & 0x1)
        operand >>= 1
        fset ZF (operand == 0)
        fset NF (operand & 0x80)

    """"No Operation
    instruction NOP
        0xEA -> implicit
    execute
        ;

    """"Logical Inclusive OR
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
        acc |= operand
        fset CF (acc == 0)
        fset NF (acc & 0x80)

    """"Push Accumulator
    instruction PHA
        0x48 -> implicit
    execute
        push-stack acc

    """"Push Processor Status
    instruction PHP
        0x08 -> implicit
    execute
        # http://wiki.nesdev.com/w/index.php/Status_flags
        # See "The B Flag"
        push-stack (rp | 0x30)

    """"Pull Accumulator
    instruction PLA
        0x68 -> implicit
    execute
        acc = (pull-stack 1)
        fset ZF (acc == 0)
        fset NF (acc & 0x80)

    """"Pull Processor Status
    instruction PLP
        0x28 -> implicit
    execute
        # bits 4 and 5 have to remain the same
        mask := (rp & 0x30:u8)
        svalue := (pull-stack 1)
        rp = ((svalue & (~ 0x30:u8)) | mask)
        ;

    """"Return from Subroutine
    instruction RTS
        0x60 -> implicit
    execute
        pc = ((pull-stack 2) + 1)

    """"Set Carry Flag
    instruction SEC
        0x38 -> implicit
    execute
        fset CF true

    """"Set Decimal Flag
    instruction SED
        0xF8 -> implicit
    execute
        fset DF true

    """"Set Interrupt Disable
    instruction SEI
        0x78 -> implicit
    execute
        fset IF true

    """"Store Accumulator
    instruction STA
        0x85 -> zero-page
        0x95 -> zero-pageX
        0x8D -> absolute
        0x9D -> absoluteX
        0x99 -> absoluteY
        0x81 -> indirectX
        0x91 -> indirectY
    execute
        operand = acc

    """"Store X Register
    instruction STX
        0x86 -> zero-page
        0x96 -> zero-pageY
        0x8E -> absolute
    execute
        operand = rx

init-instructions;

do
    let opcode-table init-instructions AddressingMode
    locals;
