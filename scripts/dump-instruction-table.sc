from (import format) let hex
using import String
using import ..src.6502-instruction-set

for k v in NES6502
    v as:= Scope
    let mnemonic = (('@ v 'mnemonic) as string)
    let code     = (('@ v 'byte) as i32)
    let addrmode = (('@ v 'addrmode) as Symbol)
    print (default-styler 'style-number (string (.. "0x" (hex code)))) mnemonic "-" addrmode
