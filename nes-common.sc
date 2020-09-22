using import enum
using import struct
using import Array

enum StatusFlag plain
    Negative = 7
    Overflow = 6
    Break = 4
    Decimal = 3
    InterruptDisable = 2
    Zero = 1
    Carry = 0

typedef ProgramCounter <: u16

do
    let StatusFlag ProgramCounter
    locals;
