using import radlib.core-extensions

using import struct
using import glm

import .sokol
let sapp = sokol.app
let sg = sokol.gfx
using import .nes

global emulator : NESEmulator

struct RenderingState plain
    pass-action : sg.pass_action
    pipeline : sg.pipeline
    bindings : sg.bindings
    frame-tex : sg.image

global gfx-state : RenderingState


let vshader fshader =
    do
        using import glsl
        fn vertex ()
            local vertices =
                arrayof vec2
                    vec2 -1  1 # top left
                    vec2 -1 -1 # bottom left
                    vec2  1 -1 # bottom right
                    vec2  1 -1 # bottom right
                    vec2  1  1 # top right
                    vec2 -1  1 # top left

            local texcoords =
                arrayof vec2
                    vec2 0 1 # top left
                    vec2 0 0 # bottom left
                    vec2 1 0 # bottom right
                    vec2 1 0 # bottom right
                    vec2 1 1 # top right
                    vec2 0 1 # top left

            out vtexcoord : vec2
                location = 0

            gl_Position = (vec4 (vertices @ gl_VertexID) 0 1)
            vtexcoord = texcoords @ gl_VertexID

        fn fragment ()
            uniform tex : sampler2D
                set = 0
                binding = 1

            in vtexcoord : vec2
                location = 0
            out fcolor : vec4
                location = 0

            fcolor = (texture tex vtexcoord)
        _ vertex fragment

fn init ()
    sg.setup
        &local sg.desc
            context = (sokol.glue.sgcontext)

    gfx-state.pass-action.colors @ 0 =
        typeinit
            action = sg.action.SG_ACTION_CLEAR
            val = (arrayof f32 0.14 0.14 0.14 1.0)

    local shdesc : sg.shader_desc
        vs =
            typeinit
                source = (static-compile-glsl 440 'vertex (static-typify vshader))
                entry = "main"
        fs =
            typeinit
                source = (static-compile-glsl 440 'fragment (static-typify fshader))
                entry = "main"
    shdesc.fs.images @ 0 =
        typeinit
            name = "tex"
            type = sg.image_type.SG_IMAGETYPE_2D
            sampler_type = sg.sampler_type.SG_SAMPLERTYPE_UINT

    local pipdesc : sg.pipeline_desc
    pipdesc.shader = (sg.make_shader &shdesc)
    (pipdesc.layout.attrs @ 0) . format = sg.vertex_format.SG_VERTEXFORMAT_FLOAT

    gfx-state.pipeline = (sg.make_pipeline &pipdesc)

    local imgdesc : sg.image_desc
        width = 256
        height = 240
        usage = sg.usage.SG_USAGE_DYNAMIC

    let bindings = gfx-state.bindings
    bindings.fs_images @ 0 = (sg.make_image &imgdesc)

    # this buffer is here only to make sokol happy. All vertices are defined in the shader
    # because I couldn't be bothered.
    bindings.vertex_buffers @ 0 =
        sg.make_buffer
            &local sg.buffer_desc
                size = 6
                content =
                    &local (arrayof f32 0 0 0 0 0 0)
    ;

fn update ()
    # get frame from emulator and update the texture on the gpu
    let frame-tex = ('get-frame emulator)
    let subimage =
        sg.subimage_content ((imply frame-tex pointer) as voidstar) ((countof frame-tex) as i32)
    local image-content : sg.image_content
    (image-content.subimage @ 0) @ 0 = subimage
    sg.update_image (gfx-state.bindings.fs_images @ 0) &image-content

    sg.begin_default_pass &gfx-state.pass-action 256 240
    sg.apply_pipeline gfx-state.pipeline
    sg.apply_bindings &gfx-state.bindings
    sg.draw 0 6 1
    sg.end_pass;
    sg.commit;

fn cleanup ()
    sg.shutdown;
fn event-handler (ev)

# ================================================================================

let argc argv = (launch-args)
# for now let's assume the only arg is the cart
if (argc < 3)
    error "please supply a ROM as argument"
let path = (argv @ 2)
'insert-cart emulator path

sapp.run
    &local sapp.desc
        width = 256
        height = 240
        init_cb = init
        frame_cb = update
        cleanup_cb = cleanup
        event_cb = event-handler

        window_title = "nes-emu"
