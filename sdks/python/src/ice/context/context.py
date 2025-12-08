"""Context implementation for Ice SDK."""

from __future__ import annotations

import io
import time
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from ice.context.pack import Pack


class Context:
    """
    Context is the execution context for ice processing.
    
    Contains:
    - ice_time: Time when context was created
    - ice_id: Handler ID being executed
    - pack: Input pack
    - process_info: Debug information collected during execution
    """
    
    def __init__(self, ice_id: int, pack: Pack) -> None:
        from ice.context.pack import Pack as PackClass
        
        self.ice_time: int = int(time.time() * 1000)
        self.ice_id: int = ice_id
        self.pack: Pack = pack if pack is not None else PackClass()
        self.process_info: io.StringIO = io.StringIO()
    
    def get_process_info(self) -> str:
        """Get collected process information."""
        return self.process_info.getvalue()
    
    def __repr__(self) -> str:
        return f"Context(ice_id={self.ice_id}, pack={self.pack})"

