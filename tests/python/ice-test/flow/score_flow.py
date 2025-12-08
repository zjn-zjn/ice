"""Score flow leaf nodes for testing."""

from typing import Annotated

import ice
from ice import IceField, Roam


@ice.leaf(
    "com.ice.test.flow.ScoreFlow",
    name="分数判断节点",
    desc="取出roam中的值比较大小",
    alias=["score_flow"],
)
class ScoreFlow:
    """Checks if roam[key] >= score."""
    score: Annotated[float, IceField(name="分数阈值", desc="判断分数的阈值")] = 0.0
    key: Annotated[str, IceField(name="取值键", desc="从roam中取值的键名")] = ""
    
    def do_roam_flow(self, roam: Roam) -> bool:
        value = roam.get_multi(self.key)
        if value is None:
            return False
        
        try:
            value_score = float(value)
        except (ValueError, TypeError):
            return False
        
        return value_score >= self.score


@ice.leaf(
    "com.ice.test.flow.ScoreFlow2",
    name="分数判断节点2",
    desc="另一个分数判断",
    alias=["score_flow_2"],
)
class ScoreFlow2:
    """Another variant of score checking."""
    score: Annotated[float, IceField(name="分数阈值", desc="判断分数的阈值")] = 0.0
    key: Annotated[str, IceField(name="取值键", desc="从roam中取值的键名")] = ""
    
    def do_roam_flow(self, roam: Roam) -> bool:
        value = roam.get_multi(self.key)
        if value is None:
            return False
        
        try:
            value_score = float(value)
        except (ValueError, TypeError):
            return False
        
        return value_score >= self.score
