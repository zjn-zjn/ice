"""Amount result leaf nodes for testing."""

import ice
from ice import Roam
from service.send_service import send_service


@ice.leaf("com.ice.test.result.AmountResult", name="发放余额节点", desc="用于发放余额")
class AmountResult:
    """Grants amount to a user."""
    key: str = ""
    value: float = 0.0
    
    def do_roam_result(self, roam: Roam) -> bool:
        uid_val = roam.get_multi(self.key)
        if uid_val is None or self.value <= 0:
            return False
        
        try:
            uid = int(uid_val)
        except (ValueError, TypeError):
            return False
        
        res = send_service.send_amount(uid, self.value)
        roam.put("SEND_AMOUNT", res)
        return res


@ice.leaf("com.ice.test.result.AmountResult2", name="发放余额节点2", desc="另一个发放余额")
class AmountResult2:
    """Another variant of amount granting."""
    key: str = ""
    value: float = 0.0
    
    def do_roam_result(self, roam: Roam) -> bool:
        uid_val = roam.get_multi(self.key)
        if uid_val is None or self.value <= 0:
            return False
        
        try:
            uid = int(uid_val)
        except (ValueError, TypeError):
            return False
        
        res = send_service.send_amount(uid, self.value)
        roam.put("SEND_AMOUNT", res)
        return res

