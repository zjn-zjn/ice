"""Point result leaf nodes for testing."""

from typing import Annotated

import ice
from ice import IceField, Roam
from service.send_service import send_service


@ice.leaf(
    "com.ice.test.result.PointResult",
    name="发放积分节点",
    desc="用于发放积分奖励",
    alias=["point_result"],
)
class PointResult:
    """Grants points to a user."""
    key: Annotated[str, IceField(name="用户ID键", desc="从roam中获取用户ID的键名")] = ""
    value: Annotated[float, IceField(name="积分值", desc="要发放的积分数量")] = 0.0
    
    def do_roam_result(self, roam: Roam) -> bool:
        uid_val = roam.get_multi(self.key)
        if uid_val is None or self.value <= 0:
            return False
        
        try:
            uid = int(uid_val)
        except (ValueError, TypeError):
            return False
        
        res = send_service.send_point(uid, self.value)
        roam.put("SEND_POINT", res)
        return res


@ice.leaf(
    "com.ice.test.result.PointResult2",
    name="发放积分节点2",
    desc="另一个积分发放",
    alias=["point_result_2"],
)
class PointResult2:
    """Another variant of point granting."""
    key: Annotated[str, IceField(name="用户ID键", desc="从roam中获取用户ID的键名")] = ""
    value: Annotated[float, IceField(name="积分值", desc="要发放的积分数量")] = 0.0
    
    def do_roam_result(self, roam: Roam) -> bool:
        uid_val = roam.get_multi(self.key)
        if uid_val is None or self.value <= 0:
            return False
        
        try:
            uid = int(uid_val)
        except (ValueError, TypeError):
            return False
        
        res = send_service.send_point(uid, self.value)
        roam.put("SEND_POINT", res)
        return res


@ice.leaf(
    "com.ice.test.result.InitConfigResult",
    name="初始化配置节点",
    desc="初始化roam中的配置",
    alias=["init_config_result"],
)
class InitConfigResult:
    """Initializes config in roam."""
    configKey: Annotated[str, IceField(name="配置键", desc="要设置的配置键名")] = ""
    configValue: Annotated[str, IceField(name="配置值", desc="要设置的配置值")] = ""
    
    def do_roam_result(self, roam: Roam) -> bool:
        if not self.configKey:
            return False
        roam.put(self.configKey, self.configValue)
        return True
