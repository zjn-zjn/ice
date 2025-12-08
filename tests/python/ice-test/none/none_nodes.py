"""None leaf nodes for testing."""

import logging
from typing import Annotated

import ice
from ice import IceField, Roam, Pack

logger = logging.getLogger(__name__)


@ice.leaf(
    "com.ice.test.none.RoamProbeLogNone",
    name="Roam探针日志",
    desc="输出roam内容用于调试",
    alias=["roam_probe_log_none"],
)
class RoamProbeLogNone:
    """Logs roam content for debugging."""
    key: Annotated[str, IceField(name="探测键", desc="要输出的roam键名,为空则输出全部")] = ""
    
    def do_roam_none(self, roam: Roam) -> None:
        if self.key:
            logger.info(f"roam probe key={self.key} value={roam.get_multi(self.key)}")
        else:
            logger.info(f"roam probe data={roam.to_dict()}")


@ice.leaf(
    "com.ice.test.none.TimeChangeNone",
    name="时间修改节点",
    desc="用于测试时修改请求时间",
    alias=["time_change_none"],
)
class TimeChangeNone:
    """Modifies the request time for testing."""
    time: Annotated[int, IceField(name="指定时间", desc="将请求时间设置为指定时间的毫秒数")] = 0
    cursorMills: Annotated[int, IceField(name="时间偏移", desc="在当前请求时间上增加的毫秒数")] = 0
    environment: Annotated[str, IceField(name="环境", desc="限制生效的环境,prod环境不生效")] = ""
    
    def do_pack_none(self, pack: Pack) -> None:
        if self.environment != "prod":
            if self.time > 0:
                pack.request_time = self.time
            else:
                pack.request_time = pack.request_time + self.cursorMills
