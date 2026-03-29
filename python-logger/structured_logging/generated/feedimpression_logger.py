"""
Generated structured logger for Tracks posts shown in user feeds with ranking features.

Version: 1.0.0
Kafka Topic: worksphere-feed-impressions
Warehouse Table: analytics.feed_impressions

DO NOT EDIT - This file is auto-generated from the log config.
"""

from datetime import datetime, date
from typing import Optional, Dict, List, Any
from structured_logging.base_logger import BaseStructuredLogger


class FeedImpressionLogger(BaseStructuredLogger):
    """Structured logger for FeedImpression events."""

    def __init__(self, kafka_bootstrap_servers: Optional[str] = None):
        """Initialize the FeedImpression logger."""
        super().__init__(
            topic_name="worksphere-feed-impressions",
            logger_name="FeedImpression",
            kafka_bootstrap_servers=kafka_bootstrap_servers,
        )

    def log(
        self,
        timestamp: datetime, event_date: date, user_id: int, post_id: int, author_id: int, position: int, score: Optional[float] = None, source: str, target_type: Optional[str] = None, target_id: Optional[int] = None, feat_engagement: Optional[float] = None, feat_recency_hours: Optional[float] = None, feat_affinity: Optional[float] = None, feat_reaction_count: Optional[int] = None, feat_comment_count: Optional[int] = None, feat_author_follower_count: Optional[int] = None, feat_is_recommended: Optional[bool] = None, feat_has_attachment: Optional[bool] = None, feat_has_poll: Optional[bool] = None, feat_social_distance: Optional[int] = None,
    ) -> None:
        """
        Log a FeedImpression event.

        Args:
            timestamp: Event timestamp
            event_date: Partition date
            user_id: Viewing user ID
            post_id: Post shown
            author_id: Post author ID
            position: Position in feed (0-indexed)
            score: Ranking score
            source: ORGANIC, RECOMMENDED, TRENDING
            target_type: GROUP_FEED, PAGE_FEED, etc
            target_id: Group/page ID
            feat_engagement: Feature: engagement score
            feat_recency_hours: Feature: hours since posted
            feat_affinity: Feature: author affinity score
            feat_reaction_count: Feature: total reactions
            feat_comment_count: Feature: total comments
            feat_author_follower_count: Feature: author's follower count
            feat_is_recommended: Feature: is recommended post
            feat_has_attachment: Feature: has image/video
            feat_has_poll: Feature: has poll
            feat_social_distance: Feature: 1=following, 2=FOF, 3=other
        """
        record = {
            "timestamp": timestamp,
            "event_date": event_date,
            "user_id": user_id,
            "post_id": post_id,
            "author_id": author_id,
            "position": position,
            "score": score,
            "source": source,
            "target_type": target_type,
            "target_id": target_id,
            "feat_engagement": feat_engagement,
            "feat_recency_hours": feat_recency_hours,
            "feat_affinity": feat_affinity,
            "feat_reaction_count": feat_reaction_count,
            "feat_comment_count": feat_comment_count,
            "feat_author_follower_count": feat_author_follower_count,
            "feat_is_recommended": feat_is_recommended,
            "feat_has_attachment": feat_has_attachment,
            "feat_has_poll": feat_has_poll,
            "feat_social_distance": feat_social_distance,
        }

        # Remove None values for optional fields
        record = {k: v for k, v in record.items() if v is not None}

        self.publish(key=user_id, log_record=record)

    @classmethod
    def builder(cls) -> "FeedImpressionLoggerBuilder":
        """Create a builder for constructing log records."""
        return FeedImpressionLoggerBuilder()


class FeedImpressionLoggerBuilder:
    """Builder for FeedImpression log records."""

    def __init__(self):
        """Initialize the builder."""
        self._timestamp: Optional[datetime] = None
        self._event_date: Optional[date] = None
        self._user_id: Optional[int] = None
        self._post_id: Optional[int] = None
        self._author_id: Optional[int] = None
        self._position: Optional[int] = None
        self._score: Optional[float] = None
        self._source: Optional[str] = None
        self._target_type: Optional[str] = None
        self._target_id: Optional[int] = None
        self._feat_engagement: Optional[float] = None
        self._feat_recency_hours: Optional[float] = None
        self._feat_affinity: Optional[float] = None
        self._feat_reaction_count: Optional[int] = None
        self._feat_comment_count: Optional[int] = None
        self._feat_author_follower_count: Optional[int] = None
        self._feat_is_recommended: Optional[bool] = None
        self._feat_has_attachment: Optional[bool] = None
        self._feat_has_poll: Optional[bool] = None
        self._feat_social_distance: Optional[int] = None

    def timestamp(self, value: datetime) -> "FeedImpressionLoggerBuilder":
        """Set timestamp."""
        self._timestamp = value
        return self
    def event_date(self, value: date) -> "FeedImpressionLoggerBuilder":
        """Set event_date."""
        self._event_date = value
        return self
    def user_id(self, value: int) -> "FeedImpressionLoggerBuilder":
        """Set user_id."""
        self._user_id = value
        return self
    def post_id(self, value: int) -> "FeedImpressionLoggerBuilder":
        """Set post_id."""
        self._post_id = value
        return self
    def author_id(self, value: int) -> "FeedImpressionLoggerBuilder":
        """Set author_id."""
        self._author_id = value
        return self
    def position(self, value: int) -> "FeedImpressionLoggerBuilder":
        """Set position."""
        self._position = value
        return self
    def score(self, value: float) -> "FeedImpressionLoggerBuilder":
        """Set score."""
        self._score = value
        return self
    def source(self, value: str) -> "FeedImpressionLoggerBuilder":
        """Set source."""
        self._source = value
        return self
    def target_type(self, value: str) -> "FeedImpressionLoggerBuilder":
        """Set target_type."""
        self._target_type = value
        return self
    def target_id(self, value: int) -> "FeedImpressionLoggerBuilder":
        """Set target_id."""
        self._target_id = value
        return self
    def feat_engagement(self, value: float) -> "FeedImpressionLoggerBuilder":
        """Set feat_engagement."""
        self._feat_engagement = value
        return self
    def feat_recency_hours(self, value: float) -> "FeedImpressionLoggerBuilder":
        """Set feat_recency_hours."""
        self._feat_recency_hours = value
        return self
    def feat_affinity(self, value: float) -> "FeedImpressionLoggerBuilder":
        """Set feat_affinity."""
        self._feat_affinity = value
        return self
    def feat_reaction_count(self, value: int) -> "FeedImpressionLoggerBuilder":
        """Set feat_reaction_count."""
        self._feat_reaction_count = value
        return self
    def feat_comment_count(self, value: int) -> "FeedImpressionLoggerBuilder":
        """Set feat_comment_count."""
        self._feat_comment_count = value
        return self
    def feat_author_follower_count(self, value: int) -> "FeedImpressionLoggerBuilder":
        """Set feat_author_follower_count."""
        self._feat_author_follower_count = value
        return self
    def feat_is_recommended(self, value: bool) -> "FeedImpressionLoggerBuilder":
        """Set feat_is_recommended."""
        self._feat_is_recommended = value
        return self
    def feat_has_attachment(self, value: bool) -> "FeedImpressionLoggerBuilder":
        """Set feat_has_attachment."""
        self._feat_has_attachment = value
        return self
    def feat_has_poll(self, value: bool) -> "FeedImpressionLoggerBuilder":
        """Set feat_has_poll."""
        self._feat_has_poll = value
        return self
    def feat_social_distance(self, value: int) -> "FeedImpressionLoggerBuilder":
        """Set feat_social_distance."""
        self._feat_social_distance = value
        return self

    def build(self) -> Dict[str, Any]:
        """Build and return the log record dictionary."""
        record = {
            "timestamp": self._timestamp,
            "event_date": self._event_date,
            "user_id": self._user_id,
            "post_id": self._post_id,
            "author_id": self._author_id,
            "position": self._position,
            "score": self._score,
            "source": self._source,
            "target_type": self._target_type,
            "target_id": self._target_id,
            "feat_engagement": self._feat_engagement,
            "feat_recency_hours": self._feat_recency_hours,
            "feat_affinity": self._feat_affinity,
            "feat_reaction_count": self._feat_reaction_count,
            "feat_comment_count": self._feat_comment_count,
            "feat_author_follower_count": self._feat_author_follower_count,
            "feat_is_recommended": self._feat_is_recommended,
            "feat_has_attachment": self._feat_has_attachment,
            "feat_has_poll": self._feat_has_poll,
            "feat_social_distance": self._feat_social_distance,
        }
        return {k: v for k, v in record.items() if v is not None}
