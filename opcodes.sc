# we need:
# - an Opcode table, that decodes an opcode to a function. Maybe it can be a big switch?
# - a sugar to define an instruction, perhaps it can be grouped by mnemonic then each
# addressing mode is specified together with its opcode.

using import radlib.core-extensions

using import struct
using import enum
using import Array

using import .common

""""The instruction wrapper:
    Takes the OpCode itself (for debugging purposes), a view of the cpu state so it
    can mutate it and the next two bytes after the opcode in memory,
    which can form a full or partial operand depending on the addressing mode.
inline make-instruction-fpT (T)
    # void <- _opcode, cpu-state, low, high
    pointer (function void (pointer T) (viewof (mutable@ CPUState) 2) u8 u8)

struct OpCode plain
    byte     : u8
    mnemonic : string
    fun      : (make-instruction-fpT this-type)
   
fn NYI-instruction (_opcode cpu low high)
    print "this instruction is illegal or not yet implemented." _opcode.byte
    ;

global opcode-table : (Array OpCode)
for i in (range 255)
    'append opcode-table
        OpCode
            byte = (i as u8)
            mnemonic = "NYI"
            fun = NYI-instruction

inline join16 (lo hi)
    ((hi as u16) << 8) | lo

define-scope operand-routers
    inline implicit (cpu lo hi)
        ;

    inline accumulator (cpu lo hi)
        cpu.RA

    inline immediate (cpu lo hi)
        lo

    inline zero-page (cpu lo hi)
        ;

    inline zero-pageX (cpu lo hi)
        ;

    inline zero-pageY (cpu lo hi)
        ;

    inline relative (cpu lo hi)
        ;

    inline absolute (cpu lo hi)
        cpu.iRAM @ (join16 lo hi)

    inline absoluteX (cpu lo hi)
        ;

    inline absoluteY (cpu lo hi)
        ;

    inline indirect (cpu lo hi)
        ;

    inline indirectX (cpu lo hi)
        ;

    inline indirectY (cpu lo hi)
        ;
       
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

    inline build-instruction-function (router)
        qq
            fn (_opcode cpu lo hi)
                [let] cpu = ([ptrtoref] cpu)
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

                [let] operand = ([router] cpu lo hi)
                [unlet] cpu lo hi

                unquote-splice instruction-code

    inline gen-opcode-table-entry (opcode mnemonic fun)
        opcode-table @ (opcode as u8) =
            OpCode
                byte = (opcode as u8)
                fun = fun
                mnemonic = mnemonic

    let result =
        fold (result = '()) for op in (opcodes... as list)
            sugar-match (op as list)
            case ((code as i32) '-> addressing-mode)
                let router =
                    try
                        '@ operand-routers (addressing-mode as Symbol)
                    except (ex)
                        error
                            .. "unrecognized addressing mode `"
                                (tostring addressing-mode)
                                "`, see documentation"
                cons
                    qq
                        [gen-opcode-table-entry] [code] [mnemonic]
                            [(build-instruction-function router)]
                    result

            default
                error "incorrect syntax. Should be [opcode] -> [addressing-mode]"
    _ (cons 'embed result) rest

run-stage;

""""Add with Carry
instruction ADC
    0x69 -> immediate
    # 0x65 -> zero-page
    # 0x75 -> zero-pageX
    0x6D -> absolute
    # 0x7D -> absoluteX
    # 0x79 -> absoluteY
    # 0x61 -> indirectX
    # 0x71 -> indirectY
execute
    temp := (acc as u16) + operand
    fset CF (temp > 0xFF)
    acc = (temp as u8)
    # fset ZV ???
    fset ZF (acc == 0)
    fset NF (acc & 0x80)

""""Stores the contents of the X register into memory.
instruction STX
    # 0x86 -> zero-page
    # 0x96 -> zero-pageY
    0x8E -> absolute
execute
    operand = rx

""""Each of the bits in A or M is shift one place to the right.
    The bit that was in bit 0 is shifted into the carry flag.
    Bit 7 is set to zero.
instruction LSR
    0x4A -> accumulator
    # 0x46 -> zero-page
    # 0x56 -> zero-pageX
    # 0x4E -> absolute
    # 0x5E -> absoluteX
execute
    fset CF (operand & 0x1)
    operand >>= 1
    fset ZF (operand == 0)
    fset NF (operand & 0x80)

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
