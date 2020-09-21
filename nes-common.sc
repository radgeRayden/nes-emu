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

    cycles : u64

do
    let StatusFlag MemoryAddress CPUState
    locals;
