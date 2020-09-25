using import .cpu
struct NES
    cpu : CPUState

    let CyclesPerFrame = 30000 # approximation, I'll revisit this later
   
    fn step-frame (self)
    fn step-instruction (self)
        'step-instruction self.cpu
