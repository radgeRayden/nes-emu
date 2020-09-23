using import radlib.core-extensions

using import struct

import .sokol
let sapp = sokol.app
let sg = sokol.gfx

struct RenderingState plain
    pass-action : sg.pass_action

global gfx-state : RenderingState

fn init ()
    sg.setup
        &local sg.desc
            context = (sokol.glue.sgcontext)
    gfx-state.pass-action.colors @ 0 =
        typeinit
            action = sg.action.SG_ACTION_CLEAR
            val = (arrayof f32 0.14 0.14 0.14 1.0)

fn update ()
    sg.begin_default_pass &gfx-state.pass-action 256 240
    sg.end_pass;
    sg.commit;
fn cleanup ()
    sg.shutdown;
fn event-handler (ev)

sapp.run
    &local sapp.desc
        width = 256
        height = 240
        init_cb = init
        frame_cb = update
        cleanup_cb = cleanup
        event_cb = event-handler

        window_title = "nes-emu"
