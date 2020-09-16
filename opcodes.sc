# we need:
# - an Opcode table, that decodes an opcode to a function. Maybe it can be a big switch?
# - a sugar to define an instruction, perhaps it can be grouped by mnemonic then each
# addressing mode is specified together with its opcode.

# NOTE: this is a mockup of what an instruction definition can look like.
""""Stores the contents of the X register into memory.
instruction STX
    0x86 in zero-page
    0x96 in zero-pageY
    0x8E in absolute
execute
    operand = rx

""""Each of the bits in A or M is shift one place to the right.
    The bit that was in bit 0 is shifted into the carry flag.
    Bit 7 is set to zero.
instruction LSR
    0x4A in accumulator
    0x46 in zero-page
    0x56 in zero-pageX
    0x4E in absolute
    0x5E in absoluteX
execute
    'set CF (operand & 0x1)
    operand >>= 1
    'set ZF (operand == 0)
    'set ZN (operand & 0x80)

# example of a generated opcode function:
fn OpCode0x4A (cpu)
    # this addressing mode uses the accumulator
    let operand = cpu.RA
    inline fset (flag v)
        'set-flag cpu flag v

    let acc = cpu.RA
    let rx  = cpu.RX
    let ry  = cpu.RY
    let pc  = cpu.PC
    let sp  = cpu.RS
    let

    let NF = StatusFlag.Negative
    let OF = StatusFlag.Overflow
    let BF = StatusFlag.Break
    let DF = StatusFlag.Decimal
    let IF = StatusFlag.InterruptDisable
    let ZF = StatusFlag.Zero
    let CF = StatusFlag.Carry
    fset CF (operand & 0x1)
    operand >>= 1
    fset ZF (operand == 0)
    fset ZN (operand & 0x80)
