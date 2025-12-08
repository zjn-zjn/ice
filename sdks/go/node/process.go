package node

import (
	"fmt"
	"strings"
	"time"
)

// currentTimeMillis returns the current time in milliseconds.
func currentTimeMillis() int64 {
	return time.Now().UnixMilli()
}

// CollectInfo collects debug information for a node execution.
// Format: [nodeId:logName:state:timeMs] or [nodeId:logName:state-I:timeMs] if inverse is active
func CollectInfo(sb *strings.Builder, base *Base, state byte, timeMs int64) {
	if base == nil || !base.IceNodeDebug {
		return
	}
	inverseStr := ""
	if base.IceInverse {
		inverseStr = "-I"
	}
	sb.WriteString(fmt.Sprintf("[%d:%s:%c%s:%d]", base.IceNodeId, base.IceLogName, state, inverseStr, timeMs))
}

// CollectRejectInfo collects debug information for a rejected node (forward returned false).
func CollectRejectInfo(sb *strings.Builder, base *Base) {
	if base == nil || !base.IceNodeDebug {
		return
	}
	sb.WriteString(fmt.Sprintf("[%d:%s:R-F]", base.IceNodeId, base.IceLogName))
}
