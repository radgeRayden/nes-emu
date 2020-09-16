# we need:
# - an Opcode table, that decodes an opcode to a function. Maybe it can be a big switch?
# - a sugar to define an instruction, perhaps it can be grouped by mnemonic then each
# addressing mode is specified together with its opcode.

# NOTE: this is a mockup of what an instruction definition can look like.
# Stores the contents of the X register into memory.
instruction STX
    0x86 in zero-page
    0x96 in zero-pageY
    0x8E in absolute
execute
    poke op16 rx
