sugar instruction-set (name body...)
    let header rest =
        sugar-match body...
        case (('with-header header...) rest...)
            _ (header... as list) rest...
        default
            error "!"

    inline gen-opcode-entry (mnemonic opcode addr-router body additional-args)
        mnemonic as:= Symbol
        let entry-name = (Symbol ((tostring mnemonic) .. "0x" .. (hex (opcode as i32))))
        qq
            [let] [entry-name] =
                [do]
                    # some metadata about the instruction
                    [let] byte = [opcode]
                    [let] mnemonic = [(tostring mnemonic)]
                    [let] addrmode = ([sugar-quote] [addr-router])
                    [inline] fun (cpu lo hi)
                        [let] cpu = ([ptrtoref] cpu)

                        unquote-splice header

                        [let] operand =
                            [addr-router] cpu lo hi
                                unquote-splice additional-args
                        [unlet] cpu lo hi

                        unquote-splice body
                    [locals];

    let instructions =
        loop (result rest = '() rest)
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
                                gen-opcode-entry mnemonic opcode router body... '()
                                opcodes
                        case (opcode '-> router args...)
                            cons
                                gen-opcode-entry mnemonic opcode router body... args...
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
