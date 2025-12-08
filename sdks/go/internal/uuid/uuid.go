// Package uuid provides UUID generation utilities.
package uuid

import (
	"crypto/rand"
	"encoding/base64"
	"strings"
)

// 64 character alphabet (same as Java: A-Z, a-z, 0-9, -, _)
var digits64 = []byte("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_")

// Generate22 generates a 22-character URL-safe base64 UUID.
func Generate22() string {
	b := make([]byte, 16)
	_, _ = rand.Read(b)
	// Set version 4
	b[6] = (b[6] & 0x0f) | 0x40
	// Set variant
	b[8] = (b[8] & 0x3f) | 0x80
	return strings.TrimRight(base64.URLEncoding.EncodeToString(b), "=")
}

// GenerateShortId generates an 11-character short ID (same format as Java).
// Uses 8 random bytes encoded with base64 variant to produce 11 characters.
func GenerateShortId() string {
	randomBytes := make([]byte, 8)
	_, _ = rand.Read(randomBytes)

	// Set version 4 marker (same as Java)
	randomBytes[6] = (randomBytes[6] & 0x0f) | 0x40

	// Convert bytes to int64
	var msb int64
	for i := 0; i < 8; i++ {
		msb = (msb << 8) | int64(randomBytes[i]&0xff)
	}

	// Encode to 11 characters using base64 variant
	out := make([]byte, 12)
	bit := 0
	bt1 := 8
	bt2 := 8
	offsetm := 1

	for idx := 0; offsetm > 0; {
		offsetm = 64 - ((bit + 3) << 3)

		var mask int64
		if bt1 > 3 {
			mask = (1 << (8 * 3)) - 1
		} else if bt1 >= 0 {
			mask = (1 << (8 * bt1)) - 1
			bt2 -= 3 - bt1
		} else {
			minBt := bt2
			if minBt > 3 {
				minBt = 3
			}
			mask = (1 << (8 * minBt)) - 1
			bt2 -= 3
		}

		var tmp int64
		if bt1 > 0 {
			bt1 -= 3
			if offsetm < 0 {
				tmp = msb
			} else {
				tmp = (msb >> uint(offsetm)) & mask
			}
			if bt1 < 0 {
				absOffset := -offsetm
				if absOffset < 0 {
					absOffset = -absOffset
				}
				tmp <<= uint(absOffset)
			}
		}

		out[idx+3] = digits64[tmp&0x3f]
		tmp >>= 6
		out[idx+2] = digits64[tmp&0x3f]
		tmp >>= 6
		out[idx+1] = digits64[tmp&0x3f]
		tmp >>= 6
		out[idx] = digits64[tmp&0x3f]

		bit += 3
		idx += 4
	}

	return string(out[:11])
}
