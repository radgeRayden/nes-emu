inline joinLE (lo hi)
    ((hi as u16) << 8) | lo

inline separateLE (v16)
    _ (v16 as u8) ((v16 >> 8) as u8)

locals;
