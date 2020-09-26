sugar instruction-set (name body...)
    let header rest =
        sugar-match body...
        case (('with-header header...) rest...)
            _ (header... as list) rest...
        default
            error "incorrect syntax, was supposed to be `instruction-set @name @body...`'"

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
                            error
                                "incorrect syntax, was supposed to be `@opcode -> @router [additional arguments...]'"
                    _ opcodes rest...
                default
                    error
                        "incorrect syntax, was supposed to be `(instruction @mnemonic @opcodes...) (execute @body...)'"
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


spice build-instruction-switch (scope opcode f)
    vvv bind NYI
    do
        fn fun (cpu lo hi)
            print "this opcode is illegal or not yet implemented:" (hex (cpu.mmem @ (cpu.PC - 1)))
            ;
        let mnemonic = "NYI"
        let addrmode = 'immediate
        locals;

    let sw = (sc_switch_new opcode)
    for k ins in (scope as Scope)
        sc_switch_append_case sw ('@ (ins as Scope) 'byte) `(f ins)
    sc_switch_append_default sw `(f [NYI])
    sw

locals;
