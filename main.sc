using import radlib.core-extensions

import .sokol
let sapp = sokol.app
let sg = sokol.gfx

fn init ()
    sg.setup
        &local sg.desc
            context = (sokol.glue.sgcontext)

fn update ()
fn cleanup ()
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
