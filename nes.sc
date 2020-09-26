using import Array
using import struct
using import .cpu

struct NESEmulator
    cpu : CPUState

    let CyclesPerFrame = 30000 # approximation, I'll revisit this later
   
    fn step-frame (self)
        for i in (range CyclesPerFrame)
            'step-instruction self.cpu
    fn step-instruction (self)
        'step-instruction self.cpu

    fn get-frame (self)
        local arr : (Array u8)
        'resize arr (256 * 240 * 4)
        using import itertools
        for x y in (dim 256 240)
            idx := (y * 256 + x) * 4
            arr @ idx = (y as u8)
            arr @ (idx + 1) = (x as u8)
            arr @ (idx + 2) = ((idx / (256 * 240 * 4)) * 255) as u8
            arr @ (idx + 3) = 255
        deref arr

do
    let NESEmulator
    locals;
