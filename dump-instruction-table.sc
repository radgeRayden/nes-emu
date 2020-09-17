using import .opcodes
for code op in (enumerate opcode-table)
    hexcode := (.. "0x" (hex code))
    print (sc_default_styler 'style-number hexcode) op.mnemonic
