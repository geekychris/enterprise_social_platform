"""
Tests for the feed ranking model and training pipeline.
"""
import json
import sys
from pathlib import Path

import numpy as np
import xgboost as xgb
import joblib

from dataset import FEATURE_NAMES, LABEL_COL, USER_FEATURE_COUNT, generate_dataset

PASS = 0
FAIL = 0


def test(name, condition):
    global PASS, FAIL
    if condition:
        PASS += 1
        print(f"  ✓ {name}")
    else:
        FAIL += 1
        print(f"  ✗ {name}")


def run_tests():
    global PASS, FAIL
    output_dir = Path("output")

    print("=== Dataset Tests ===")

    df, X, y = generate_dataset(n_impressions=1000, seed=99)
    test("Dataset generates correct shape", X.shape == (1000, 10))
    test("Labels are binary", set(np.unique(y)).issubset({0.0, 1.0}))
    test("Feature count matches", len(FEATURE_NAMES) == 10)
    test("User feature count is 2", USER_FEATURE_COUNT == 2)
    test("Engagement rate is reasonable", 0.05 < y.mean() < 0.5)
    test("No NaN in features", not np.any(np.isnan(X)))
    test("Recency hours positive", np.all(X[:, 1] > 0))
    test("Social distance in {1,2,3}", set(np.unique(X[:, 9])).issubset({1.0, 2.0, 3.0}))

    print("\n=== Model Artifact Tests ===")

    test("Model .ubj exists", (output_dir / "feed_ranker.ubj").exists())
    test("Model .json exists", (output_dir / "feed_ranker.json").exists())
    test("Model .joblib exists", (output_dir / "feed_ranker.joblib").exists())
    test("Training result exists", (output_dir / "feed_ranker_result.json").exists())
    test("Feature config exists", (output_dir / "feature_config.json").exists())

    print("\n=== Model Loading Tests ===")

    model = joblib.load(output_dir / "feed_ranker.joblib")
    test("Joblib model loads", model is not None)

    booster = xgb.Booster()
    booster.load_model(str(output_dir / "feed_ranker.ubj"))
    test("XGBoost booster loads", booster is not None)

    print("\n=== Prediction Tests ===")

    # Generate test data
    _, X_test, y_test = generate_dataset(n_impressions=500, seed=123)

    preds = model.predict_proba(X_test)[:, 1]
    test("Predictions are probabilities", np.all((preds >= 0) & (preds <= 1)))
    test("Predictions have variance", preds.std() > 0.01)

    # Verify model discriminates: high-engagement posts should score higher
    high_engagement = X_test[:, 0] > np.percentile(X_test[:, 0], 75)
    low_engagement = X_test[:, 0] < np.percentile(X_test[:, 0], 25)
    test("High engagement → higher scores",
         preds[high_engagement].mean() > preds[low_engagement].mean())

    # Close social distance should score higher
    close = X_test[:, 9] == 1.0
    far = X_test[:, 9] == 3.0
    if close.sum() > 0 and far.sum() > 0:
        test("Close social distance → higher scores",
             preds[close].mean() > preds[far].mean())

    print("\n=== Feature Config Tests ===")

    with open(output_dir / "feature_config.json") as f:
        config = json.load(f)

    test("Config has correct total features", config["total_features"] == 10)
    test("Config has correct user features", config["user_features"] == 2)
    test("Config feature names match", config["feature_names"] == FEATURE_NAMES)
    test("User features are affinity + social_distance",
         config["user_feature_names"] == ["affinity", "social_distance"])

    print("\n=== Training Result Tests ===")

    with open(output_dir / "feed_ranker_result.json") as f:
        result = json.load(f)

    test("AUC-ROC > 0.55", result["auc_roc"] > 0.55)
    test("AUC-PR > 0.15", result["auc_pr"] > 0.15)
    test("Has feature importance", len(result["feature_importance"]) == 10)
    test("Training time recorded", result["training_time_s"] > 0)

    print("\n=== Generated Kernel Tests ===")

    gen_dir = output_dir / "generated"
    test("C core header exists", (gen_dir / "scoring_split_core.h").exists())
    test("C bench exists", (gen_dir / "scoring_split_bench.c").exists())
    test("Makefile exists", (gen_dir / "Makefile").exists())

    # Check the generated code contains the right number of trees
    core_content = (gen_dir / "scoring_split_core.h").read_text()
    test("Generated code has scoring function", "score_user" in core_content)
    test("Generated code has correct tree count", "NUM_TREES          200" in core_content)

    print(f"\n{'='*50}")
    print(f"Results: {PASS} passed, {FAIL} failed, {PASS+FAIL} total")
    print(f"{'='*50}")

    return FAIL == 0


if __name__ == "__main__":
    success = run_tests()
    sys.exit(0 if success else 1)
