// Package timeutil provides time-related utilities.
package timeutil

import "github.com/zjn-zjn/ice/sdks/go/enum"

// TimeDisabled checks if the node should be disabled based on time constraints.
func TimeDisabled(timeType enum.TimeType, requestTime, start, end int64) bool {
	switch timeType {
	case enum.TimeNone:
		return false
	case enum.TimeBetween:
		return requestTime < start || requestTime > end
	case enum.TimeAfterStart:
		return requestTime < start
	case enum.TimeBeforeEnd:
		return requestTime > end
	default:
		return false
	}
}
