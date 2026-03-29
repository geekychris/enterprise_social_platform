"""
Feed ranking dataset for GBDT training.

Generates synthetic feed impression + interaction data that mimics
what the WorkSphere structured logging pipeline would collect.

Features match the FeedFeatureExtractor output:
  - engagement:        reactions + 2*comments
  - recency_hours:     hours since posted
  - affinity:          author affinity score (1.0 + prior_interactions * 0.3)
  - reaction_count:    total reaction count
  - comment_count:     total comment count
  - author_followers:  author's follower count
  - is_recommended:    1 if from recommendation engine, 0 if organic
  - has_attachment:    1 if has image/video, 0 otherwise
  - has_poll:          1 if has poll, 0 otherwise
  - social_distance:   1=following, 2=FOF, 3=other

Label: clicked/engaged (1) or skipped (0)
"""

import numpy as np
import pandas as pd
from typing import Tuple

# Feature names must match FeedFeatureExtractor.toFeatureVector() order
FEATURE_NAMES = [
    "engagement",
    "recency_hours",
    "affinity",
    "reaction_count",
    "comment_count",
    "author_followers",
    "is_recommended",
    "has_attachment",
    "has_poll",
    "social_distance",
]

# User features (sent per-request): affinity, social_distance
USER_FEATURE_COUNT = 2
USER_FEATURE_INDICES = [2, 9]  # affinity, social_distance

# Item/document features (pre-loaded): everything else
ITEM_FEATURE_INDICES = [0, 1, 3, 4, 5, 6, 7, 8]

LABEL_COL = "engaged"


def generate_dataset(
    n_impressions: int = 100_000,
    engagement_rate: float = 0.15,
    seed: int = 42,
) -> Tuple[pd.DataFrame, np.ndarray, np.ndarray]:
    """
    Generate synthetic feed impression data with realistic feature distributions.

    Returns:
        df: Full DataFrame with features + label
        X: Feature matrix (n_impressions, 10)
        y: Label vector (n_impressions,)
    """
    rng = np.random.default_rng(seed)

    n = n_impressions

    # Generate features with realistic distributions
    engagement = rng.exponential(5.0, n).clip(0, 500)
    recency_hours = rng.exponential(24.0, n).clip(0.01, 720)
    affinity = 1.0 + rng.exponential(0.3, n).clip(0, 5)
    reaction_count = rng.poisson(3.0, n).clip(0, 200)
    comment_count = rng.poisson(1.5, n).clip(0, 50)
    author_followers = rng.lognormal(4.0, 1.5, n).clip(1, 10000).astype(int)
    is_recommended = rng.binomial(1, 0.2, n).astype(float)
    has_attachment = rng.binomial(1, 0.3, n).astype(float)
    has_poll = rng.binomial(1, 0.05, n).astype(float)
    social_distance = rng.choice([1, 2, 3], n, p=[0.4, 0.25, 0.35]).astype(float)

    # Generate labels based on realistic engagement model
    # Higher engagement, more recent, higher affinity, closer social distance → more likely to engage
    logit = (
        0.3 * np.log1p(engagement)
        - 0.02 * recency_hours
        + 0.5 * (affinity - 1.0)
        + 0.1 * np.log1p(reaction_count)
        + 0.15 * np.log1p(comment_count)
        + 0.05 * np.log1p(author_followers) / 5
        + 0.3 * is_recommended
        + 0.4 * has_attachment
        + 0.6 * has_poll
        - 0.3 * (social_distance - 1)
        + rng.normal(0, 0.5, n)  # noise
        - 1.5  # base offset to control engagement rate
    )
    prob = 1 / (1 + np.exp(-logit))
    engaged = (rng.random(n) < prob).astype(int)

    print(f"Generated {n} impressions, engagement rate: {engaged.mean():.3f}")

    df = pd.DataFrame({
        "engagement": engagement,
        "recency_hours": recency_hours,
        "affinity": affinity,
        "reaction_count": reaction_count,
        "comment_count": comment_count,
        "author_followers": author_followers,
        "is_recommended": is_recommended,
        "has_attachment": has_attachment,
        "has_poll": has_poll,
        "social_distance": social_distance,
        "engaged": engaged,
    })

    X = df[FEATURE_NAMES].values.astype(np.float32)
    y = df[LABEL_COL].values.astype(np.float32)

    return df, X, y


if __name__ == "__main__":
    df, X, y = generate_dataset()
    print(f"\nDataset shape: {X.shape}")
    print(f"Positive rate: {y.mean():.3f}")
    print(f"\nFeature statistics:")
    print(df[FEATURE_NAMES].describe().round(2))
    df.to_parquet("feed_ranking_data.parquet", index=False)
    print(f"\nSaved to feed_ranking_data.parquet")
