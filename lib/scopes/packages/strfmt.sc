using import enum
using import String
using import Array
using import struct

enum TemplateChunk
    Text : String
    Code : String

let C = (import C.string)
let stbsp = (import stb.sprintf)

fn format (fmt ...)
    let ... =
        va-map
            inline (val)
                static-match (typeof val)
                case String
                    val as rawstring
                case string
                    val as rawstring
                default
                    static-if ((typeof val) < zarray)
                        val as rawstring
                    else
                        val
            ...

    let expected-size = (stbsp.snprintf null 0 fmt ...)
    assert (expected-size != -1)

    local result = (String (expected-size as usize))
    'resize result expected-size
    stbsp.snprintf
        (imply result pointer) as (mutable rawstring)
        (expected-size + 1)
        fmt
        ...
    result

let start-token end-token = "${" "}"

""""Get the inner code inside a template.
    @param start rawstring @pointer to start of template, at start-token
    @param end rawstring @pointer to end of template, at end-token
    @return (:_ rawstring usize)
fn get-inner (start end)
    let start-offset end-offset = (ptrtoint start usize) (ptrtoint end usize)
    _
        inttoptr (start-offset + (countof start-token)) rawstring
        end-offset - start-offset - (countof start-token)

inline ptr-offset (start end)
    (ptrtoint end usize) - (ptrtoint start usize)

""""Parse a templated string into a list of interleaved code strings.
    @param input (array i8) @constant string literal containing the templated string
    @return (Array TemplateChunk)
fn parse-template (input)
    local result : (Array TemplateChunk)

    # (prefix .. code .. suffix)+ | input
    loop (next = (input as rawstring))
        let start = (C.strstr next start-token)
        if (start != null)
            let end = (C.strstr start end-token)
            if (end != null)
                # add anything between current and previous
                if (next != start)
                    let prefix = (String next (ptr-offset next start))
                    'append result (TemplateChunk.Text prefix)

                let inner len = (get-inner start end)
                'append result (TemplateChunk.Code (String inner len))
                repeat
                    as
                        & (end @ (countof end-token))
                        rawstring

        # no more templates
        let next-idx = (ptr-offset (input as rawstring) next)
        if (next-idx == (countof input))
            break;
        else
            let remainder = (String next ((countof input) - next-idx))
            'append result (TemplateChunk.Text remainder)
            break;

    result

fn value->format-specifier (val)
    returning string Value

    let T = ('typeof val)

    if (T < integer)
        if ('signed? T)
            _ str"%d" val
        else
            _ str"%u" val
    elseif (T < real)
        _ str"%f" val
    elseif (T < pointer)
        _ str"%p" val
    elseif (or (T < zarray) (T == string) (T == String))
        _ str"%s" val
    elseif (T < Arguments)
        vvv bind specifiers args
        fold (specifiers = str"") for arg in ('args val)
            let specifier ?? =
                (this-function arg)
            .. specifiers specifier " "

        # remove extra space
        specifiers := (lslice specifiers ((countof specifiers) - 1))
        _ str"%s" `(format [specifiers] val)
    else
        _ str"%s" `(tostring val)

fn parse-positive-integer (str)
    using import UTF-8

    fold (result = 0) for c in str
        if (c >= (char32 "0") and c <= (char32 "9"))
            result := (result * 10) + (c - (char32 "0"))
        else
            error ("illegal character: " .. (c as string))

spice interpolate (fmt args...)
    let chunked = (parse-template (fmt as string))

    local fmt : String
    local args : (Array Value)

    for chunk in chunked
        dispatch chunk
        case Text (txt)
            fmt ..= txt
        case Code (idx)
            let arg = ('getarg args... (parse-positive-integer idx))
            let specifier val = (value->format-specifier arg)
            fmt ..= specifier
            'append args val
        default
            unreachable;

    let arglist =
        sc_argument_list_map_new ((countof args) as i32)
            inline (n)
                args @ n
    spice-quote
        format fmt arglist

spice va-format (sep args...)
    sep as:= string
    local fmt : String
    local args : (Array Value)

    fold (idx = 0) for arg in ('args args...)
        let specifier arg = (value->format-specifier arg)
        if (idx == (('argcount args...) - 1))
            fmt ..= specifier
        else
            fmt = (fmt .. specifier .. sep)
        'append args arg
        idx + 1

    let arglist =
        sc_argument_list_map_new ((countof args) as i32)
            inline (n)
                args @ n
    spice-quote
        format fmt arglist

sugar prefix:f (str)
    let chunked = (parse-template (str as string))

    vvv bind fmt args
    fold (fmt args = S"" (list)) for chunk in chunked
        dispatch chunk
        case Text (txt)
            # FIXME: escape % character
            _ (fmt .. txt) args
        case Code (code)
            let parsed = (cons 'embed ((sc_parse_from_string (string (code as rawstring))) as list))

            _
                .. fmt start-token (tostring (countof args)) end-token
                cons
                    parsed
                    args

        default
            unreachable;

    if ((countof args) == 0)
        qq [embed] [str]
    else
        let args = ('reverse args)
        qq [interpolate] [(fmt as string)] (unquote-splice args)

do
    let format prefix:f interpolate va-format
    locals;
