"""Mock send service for testing."""

import logging

logger = logging.getLogger(__name__)


class SendService:
    """Provides mock send operations."""
    
    def send_amount(self, uid: int, value: float) -> bool:
        """Simulate sending amount to a user."""
        logger.info(f"=======send amount uid={uid} value={value}")
        return True
    
    def send_point(self, uid: int, value: float) -> bool:
        """Simulate sending points to a user."""
        logger.info(f"=======send point uid={uid} value={value}")
        return True


# Global instance
send_service = SendService()

