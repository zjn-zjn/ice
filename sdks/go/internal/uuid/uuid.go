// Package uuid provides UUID generation utilities.
package uuid

import (
	"crypto/rand"
	"encoding/base64"
	"encoding/hex"
	"strings"
	"sync/atomic"
	"time"
)

var counter uint32

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

// GenerateShortId generates a short ID based on time and counter.
func GenerateShortId() string {
	now := time.Now().UnixNano()
	cnt := atomic.AddUint32(&counter, 1)
	b := make([]byte, 12)
	b[0] = byte(now >> 40)
	b[1] = byte(now >> 32)
	b[2] = byte(now >> 24)
	b[3] = byte(now >> 16)
	b[4] = byte(now >> 8)
	b[5] = byte(now)
	b[6] = byte(cnt >> 24)
	b[7] = byte(cnt >> 16)
	b[8] = byte(cnt >> 8)
	b[9] = byte(cnt)
	_, _ = rand.Read(b[10:])
	return hex.EncodeToString(b)
}
