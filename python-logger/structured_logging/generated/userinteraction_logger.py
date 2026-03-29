"""
Generated structured logger for Tracks all user interactions: clicks, reactions, comments, messages, searches, profile views.

Version: 1.0.0
Kafka Topic: worksphere-user-interactions
Warehouse Table: analytics.user_interactions

DO NOT EDIT - This file is auto-generated from the log config.
"""

from datetime import datetime, date
from typing import Optional, Dict, List, Any
from structured_logging.base_logger import BaseStructuredLogger


class UserInteractionLogger(BaseStructuredLogger):
    """Structured logger for UserInteraction events."""

    def __init__(self, kafka_bootstrap_servers: Optional[str] = None):
        """Initialize the UserInteraction logger."""
        super().__init__(
            topic_name="worksphere-user-interactions",
            logger_name="UserInteraction",
            kafka_bootstrap_servers=kafka_bootstrap_servers,
        )

    def log(
        self,
        timestamp: datetime, event_date: date, user_id: int, interaction_type: str, target_id: Optional[int] = None, target_type: Optional[str] = None, content_author_id: Optional[int] = None, reaction_type: Optional[str] = None, search_query: Optional[str] = None, search_result_count: Optional[int] = None, message_has_attachment: Optional[bool] = None, bot_context: Optional[str] = None, bot_tools_used: Optional[str] = None, bot_response_time_ms: Optional[int] = None, group_id: Optional[int] = None, page_id: Optional[int] = None, device_type: Optional[str] = None, properties: Optional[Dict[str, str]] = None,
    ) -> None:
        """
        Log a UserInteraction event.

        Args:
            timestamp: Event timestamp
            event_date: Partition date
            user_id: User performing action
            interaction_type: FEED_CLICK, REACTION, COMMENT, MESSAGE_SENT, SEARCH, PROFILE_VIEW, BOT_INTERACTION, POLL_VOTE
            target_id: Target entity ID (post, user, conversation)
            target_type: POST, USER, CONVERSATION, GROUP, PAGE
            content_author_id: Author of the content interacted with
            reaction_type: LIKE, LOVE, HAHA, etc (for reactions)
            search_query: Search query text
            search_result_count: Number of search results
            message_has_attachment: Message had attachment
            bot_context: Bot interaction context
            bot_tools_used: Comma-separated bot tools
            bot_response_time_ms: Bot response latency
            group_id: Group context if applicable
            page_id: Page context if applicable
            device_type: web, ios, android
            properties: Additional properties
        """
        record = {
            "timestamp": timestamp,
            "event_date": event_date,
            "user_id": user_id,
            "interaction_type": interaction_type,
            "target_id": target_id,
            "target_type": target_type,
            "content_author_id": content_author_id,
            "reaction_type": reaction_type,
            "search_query": search_query,
            "search_result_count": search_result_count,
            "message_has_attachment": message_has_attachment,
            "bot_context": bot_context,
            "bot_tools_used": bot_tools_used,
            "bot_response_time_ms": bot_response_time_ms,
            "group_id": group_id,
            "page_id": page_id,
            "device_type": device_type,
            "properties": properties,
        }

        # Remove None values for optional fields
        record = {k: v for k, v in record.items() if v is not None}

        self.publish(key=user_id, log_record=record)

    @classmethod
    def builder(cls) -> "UserInteractionLoggerBuilder":
        """Create a builder for constructing log records."""
        return UserInteractionLoggerBuilder()


class UserInteractionLoggerBuilder:
    """Builder for UserInteraction log records."""

    def __init__(self):
        """Initialize the builder."""
        self._timestamp: Optional[datetime] = None
        self._event_date: Optional[date] = None
        self._user_id: Optional[int] = None
        self._interaction_type: Optional[str] = None
        self._target_id: Optional[int] = None
        self._target_type: Optional[str] = None
        self._content_author_id: Optional[int] = None
        self._reaction_type: Optional[str] = None
        self._search_query: Optional[str] = None
        self._search_result_count: Optional[int] = None
        self._message_has_attachment: Optional[bool] = None
        self._bot_context: Optional[str] = None
        self._bot_tools_used: Optional[str] = None
        self._bot_response_time_ms: Optional[int] = None
        self._group_id: Optional[int] = None
        self._page_id: Optional[int] = None
        self._device_type: Optional[str] = None
        self._properties: Optional[Dict[str, str]] = None

    def timestamp(self, value: datetime) -> "UserInteractionLoggerBuilder":
        """Set timestamp."""
        self._timestamp = value
        return self
    def event_date(self, value: date) -> "UserInteractionLoggerBuilder":
        """Set event_date."""
        self._event_date = value
        return self
    def user_id(self, value: int) -> "UserInteractionLoggerBuilder":
        """Set user_id."""
        self._user_id = value
        return self
    def interaction_type(self, value: str) -> "UserInteractionLoggerBuilder":
        """Set interaction_type."""
        self._interaction_type = value
        return self
    def target_id(self, value: int) -> "UserInteractionLoggerBuilder":
        """Set target_id."""
        self._target_id = value
        return self
    def target_type(self, value: str) -> "UserInteractionLoggerBuilder":
        """Set target_type."""
        self._target_type = value
        return self
    def content_author_id(self, value: int) -> "UserInteractionLoggerBuilder":
        """Set content_author_id."""
        self._content_author_id = value
        return self
    def reaction_type(self, value: str) -> "UserInteractionLoggerBuilder":
        """Set reaction_type."""
        self._reaction_type = value
        return self
    def search_query(self, value: str) -> "UserInteractionLoggerBuilder":
        """Set search_query."""
        self._search_query = value
        return self
    def search_result_count(self, value: int) -> "UserInteractionLoggerBuilder":
        """Set search_result_count."""
        self._search_result_count = value
        return self
    def message_has_attachment(self, value: bool) -> "UserInteractionLoggerBuilder":
        """Set message_has_attachment."""
        self._message_has_attachment = value
        return self
    def bot_context(self, value: str) -> "UserInteractionLoggerBuilder":
        """Set bot_context."""
        self._bot_context = value
        return self
    def bot_tools_used(self, value: str) -> "UserInteractionLoggerBuilder":
        """Set bot_tools_used."""
        self._bot_tools_used = value
        return self
    def bot_response_time_ms(self, value: int) -> "UserInteractionLoggerBuilder":
        """Set bot_response_time_ms."""
        self._bot_response_time_ms = value
        return self
    def group_id(self, value: int) -> "UserInteractionLoggerBuilder":
        """Set group_id."""
        self._group_id = value
        return self
    def page_id(self, value: int) -> "UserInteractionLoggerBuilder":
        """Set page_id."""
        self._page_id = value
        return self
    def device_type(self, value: str) -> "UserInteractionLoggerBuilder":
        """Set device_type."""
        self._device_type = value
        return self
    def properties(self, value: Dict[str, str]) -> "UserInteractionLoggerBuilder":
        """Set properties."""
        self._properties = value
        return self

    def build(self) -> Dict[str, Any]:
        """Build and return the log record dictionary."""
        record = {
            "timestamp": self._timestamp,
            "event_date": self._event_date,
            "user_id": self._user_id,
            "interaction_type": self._interaction_type,
            "target_id": self._target_id,
            "target_type": self._target_type,
            "content_author_id": self._content_author_id,
            "reaction_type": self._reaction_type,
            "search_query": self._search_query,
            "search_result_count": self._search_result_count,
            "message_has_attachment": self._message_has_attachment,
            "bot_context": self._bot_context,
            "bot_tools_used": self._bot_tools_used,
            "bot_response_time_ms": self._bot_response_time_ms,
            "group_id": self._group_id,
            "page_id": self._page_id,
            "device_type": self._device_type,
            "properties": self._properties,
        }
        return {k: v for k, v in record.items() if v is not None}
