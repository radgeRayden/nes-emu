# we need:
# - an Opcode table, that decodes an opcode to a function. Maybe it can be a big switch?
# - a sugar to define an instruction, perhaps it can be grouped by mnemonic then each
# addressing mode is specified together with its opcode.

using import struct
using import enum
using import Array

using import .cpustate

struct OpCode plain
    byte     : u8
    mnemonic : string
    fun      : (pointer (function void CPUState))
   
global opcode-table : (Array OpCode)

sugar instruction (mnemonic opcodes...)
    let next rest = (decons next-expr)
    for op in (opcodes... as list)
        sugar-match (op as list)
        case ((code as i32) '-> 'implicit)
        case ((code as i32) '-> 'accumulator)
        case ((code as i32) '-> 'immediate)
        case ((code as i32) '-> 'zero-page)
        case ((code as i32) '-> 'zero-pageX)
        case ((code as i32) '-> 'zero-pageY)
        case ((code as i32) '-> 'relative)
        case ((code as i32) '-> 'absolute)
        case ((code as i32) '-> 'absoluteX)
        case ((code as i32) '-> 'absoluteY)
        case ((code as i32) '-> 'indirect)
        case ((code as i32) '-> 'indexed-indirect)
        case ((code as i32) '-> 'indirect-indexed)
        default
            print op
    if (('typeof next) != list)
        error "expected an `execute` block"

    let instruction-code =
        sugar-match (next as list)
        case ('execute body...)
            body...
        default
            error "expected an `execute` block"

    # build function
    let fun =
        qq
            fn (cpu)
                [let]
                [inline] fset (flag v)
                    'set-flag cpu flag v

                [let] acc = cpu.RA
                [let] rx  = cpu.RX
                [let] ry  = cpu.RY
                [let] pc  = cpu.PC
                [let] sp  = cpu.RS

                [let] NF = StatusFlag.Negative
                [let] OF = StatusFlag.Overflow
                [let] BF = StatusFlag.Break
                [let] DF = StatusFlag.Decimal
                [let] IF = StatusFlag.InterruptDisable
                [let] ZF = StatusFlag.Zero
                [let] CF = StatusFlag.Carry
                unquote-splice instruction-code
    _ () rest

run-stage;

# NOTE: this is a mockup of what an instruction definition can look like.
""""Stores the contents of the X register into memory.
instruction STX
    0x86 -> zero-page
    0x96 -> zero-pageY
    0x8E -> absolute
execute
    operand = rx

""""Each of the bits in A or M is shift one place to the right.
    The bit that was in bit 0 is shifted into the carry flag.
    Bit 7 is set to zero.
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
    fset ZN (operand & 0x80)

# example of a generated opcode function:
# fn OpCode0x4A (cpu)
#     # this addressing mode uses the accumulator
#     let operand = cpu.RA
#     inline fset (flag v)
#         'set-flag cpu flag v

#     let acc = cpu.RA
#     let rx  = cpu.RX
#     let ry  = cpu.RY
#     let pc  = cpu.PC
#     let sp  = cpu.RS
#     let

#     let NF = StatusFlag.Negative
#     let OF = StatusFlag.Overflow
#     let BF = StatusFlag.Break
#     let DF = StatusFlag.Decimal
#     let IF = StatusFlag.InterruptDisable
#     let ZF = StatusFlag.Zero
#     let CF = StatusFlag.Carry
#     fset CF (operand & 0x1)
#     operand >>= 1
#     fset ZF (operand == 0)
#     fset ZN (operand & 0x80)

do
    let opcode-table
    locals;
