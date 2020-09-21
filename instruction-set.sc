sugar instruction-set (name body...)
    fn gen-opcode-entry (mnemonic opcode addr-router body)
        mnemonic as:= Symbol
        let entry-name = (Symbol ((tostring mnemonic) .. "0x" .. (hex (opcode as i32))))
        qq
            [let] [entry-name] =
                [do]
                    # some metadata about the instruction
                    [let] byte = [opcode]
                    [let] mnemonic = [(tostring mnemonic)]
                    [let] addrmode = (sugar-quote [addr-router])
                    [locals];

    let instructions =
        loop (result rest = '() (body... as list))
            if (empty? rest)
                break result

            let instruction rest =
                sugar-match rest
                case (('instruction mnemonic opcodes...) ('execute body...) rest...)
                    vvv bind opcodes
                    fold (opcodes = '(embed)) for opcode in (opcodes... as list)
                        sugar-match (opcode as list)
                        case (opcode '-> router)
                            cons
                                gen-opcode-entry mnemonic opcode router body...
                                opcodes
                        default
                            error "!"
                    _ opcodes rest...
                default
                    error "!"
            _ (cons ('reverse instruction) result) rest
    vvv bind result
    qq
        [let] [name] =
            [do]
                [embed]
                    unquote-splice ('reverse instructions)
                [locals];
    # print result
    result

locals;
