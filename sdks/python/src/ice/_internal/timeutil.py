"""Time utilities for Ice SDK."""

from ice.enums import TimeType


def time_disabled(
    time_type: TimeType | int,
    request_time: int,
    start: int,
    end: int,
) -> bool:
    """
    Check if the node should be disabled based on time constraints.
    
    Args:
        time_type: Time control type
        request_time: Current request time in milliseconds
        start: Start time in milliseconds
        end: End time in milliseconds
    
    Returns:
        True if the node should be disabled, False otherwise
    """
    if isinstance(time_type, int):
        time_type = TimeType(time_type) if time_type in TimeType._value2member_map_ else TimeType.NONE
    
    if time_type == TimeType.NONE:
        return False
    
    if time_type == TimeType.BETWEEN:
        # Must be between start and end
        if start > 0 and request_time < start:
            return True
        if end > 0 and request_time > end:
            return True
        return False
    
    if time_type == TimeType.AFTER_START:
        # Must be after start
        if start > 0 and request_time < start:
            return True
        return False
    
    if time_type == TimeType.BEFORE_END:
        # Must be before end
        if end > 0 and request_time > end:
            return True
        return False
    
    return False

