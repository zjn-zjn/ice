"""UUID utilities for Ice SDK."""

import base64
import secrets
import uuid as uuid_module

# 64 character alphabet (same as Java: A-Z, a-z, 0-9, -, _)
_DIGITS64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"


def generate_uuid22() -> str:
    """Generate a 22-character UUID (base64 encoded UUID without padding)."""
    u = uuid_module.uuid4()
    return base64.urlsafe_b64encode(u.bytes).decode("ascii").rstrip("=")


def generate_uuid() -> str:
    """Generate a standard UUID string."""
    return str(uuid_module.uuid4())


def generate_short_id() -> str:
    """
    Generate an 11-character short ID (same format as Java).
    Uses 8 random bytes encoded with base64 variant to produce 11 characters.
    """
    random_bytes = bytearray(secrets.token_bytes(8))
    
    # Set version 4 marker (same as Java)
    random_bytes[6] = (random_bytes[6] & 0x0f) | 0x40
    
    # Convert bytes to int64
    msb = 0
    for i in range(8):
        msb = (msb << 8) | (random_bytes[i] & 0xff)
    
    # Encode to 11 characters using base64 variant
    out = [''] * 12
    bit = 0
    bt1 = 8
    bt2 = 8
    offsetm = 1
    idx = 0
    
    while offsetm > 0:
        offsetm = 64 - ((bit + 3) << 3)
        
        if bt1 > 3:
            mask = (1 << (8 * 3)) - 1
        elif bt1 >= 0:
            mask = (1 << (8 * bt1)) - 1
            bt2 -= 3 - bt1
        else:
            min_bt = min(bt2, 3)
            mask = (1 << (8 * min_bt)) - 1
            bt2 -= 3
        
        tmp = 0
        if bt1 > 0:
            bt1 -= 3
            if offsetm < 0:
                tmp = msb
            else:
                tmp = (msb >> offsetm) & mask
            if bt1 < 0:
                tmp <<= abs(offsetm)
        
        out[idx + 3] = _DIGITS64[tmp & 0x3f]
        tmp >>= 6
        out[idx + 2] = _DIGITS64[tmp & 0x3f]
        tmp >>= 6
        out[idx + 1] = _DIGITS64[tmp & 0x3f]
        tmp >>= 6
        out[idx] = _DIGITS64[tmp & 0x3f]
        
        bit += 3
        idx += 4
    
    return ''.join(out[:11])

