using import radlib.core-extensions


using import struct
using import glm
using import Option

import .nfd
import .sokol
let sapp = sokol.app
let sg = sokol.gfx
let ig = (import .cimgui)

using import .nes

global emulator : (Option NESEmulator)

struct RenderingState plain
    pass-action : sg.pass_action
    pipeline : sg.pipeline
    bindings : sg.bindings
    frame-tex : sg.image

global gfx-state : RenderingState

fn select-ROM-file ()
    local selected : (mutable rawstring)
    let result = (nfd.OpenDialog "nes" module-dir &selected)
    switch result
    case nfd.result_t.NFD_OKAY
        let fresh-emu = (NESEmulator)
        try
            'insert-cart fresh-emu (string selected)
            emulator = fresh-emu
        else
            print "could not load ROM file at" (string selected)
        free selected
    case nfd.result_t.NFD_CANCEL
        ;
    default
        print "error:" (string (nfd.GetError))
    ;

fn app-UI ()
    let width height = (sapp.width) (sapp.height)
    # FIXME: get an actual deltatime
    sokol.imgui.new_frame width height (1 / 60)
    if (ig.BeginMainMenuBar)
        if (ig.BeginMenu "File" true)
            if (ig.MenuItemBool "Open..." "Ctrl+O" false true)
                select-ROM-file;
            ig.EndMenu;
        if (ig.BeginMenu "Emulator" true)
            if (ig.MenuItemBool "Reset" "Ctrl+R" false true)
                ;
            if (ig.MenuItemBool "Stop" "" false true)
                emulator = none
                ;
            ig.EndMenu;
        ;
    ig.EndMainMenuBar;

fn update ()
    app-UI;

    sg.begin_default_pass &gfx-state.pass-action (sapp.width) (sapp.height)

    # if the emulator is turned on (cart inside) then we render the game framebuffer.
    try
        let emulator = ('unwrap emulator)
        # get frame from emulator and update the texture on the gpu
        let frame-tex = ('get-frame emulator)
        let subimage =
            sg.subimage_content ((imply frame-tex pointer) as voidstar) ((countof frame-tex) as i32)
        local image-content : sg.image_content
        (image-content.subimage @ 0) @ 0 = subimage
        sg.update_image (gfx-state.bindings.fs_images @ 0) &image-content

        sg.apply_pipeline gfx-state.pipeline
        sg.apply_bindings &gfx-state.bindings
        sg.draw 0 6 1
    else
        ;

    sokol.imgui.render;

    sg.end_pass;
    sg.commit;

fn event-handler (ev)
    sokol.imgui.handle_event ev
    ;

fn init ()
    # SOKOL GFX
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
        min_filter = sg.filter.SG_FILTER_NEAREST
        mag_filter = sg.filter.SG_FILTER_NEAREST

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

    # IMGUI
    sokol.imgui.setup (&local sokol.imgui.desc_t)
    ;

fn cleanup ()
    sg.shutdown;

# ================================================================================

let argc argv = (launch-args)
# for now let's assume the only arg is the cart
if (argc > 2)
    let path = (argv @ 2)
    emulator = (NESEmulator)
    try
        let emulator = ('unwrap emulator)
        'insert-cart emulator path
    else
        ;

sapp.run
    &local sapp.desc
        width = (256 * 3)
        height = (240 * 3)
        init_cb = init
        frame_cb = update
        cleanup_cb = cleanup
        event_cb = event-handler

        window_title = "nes-emu"
