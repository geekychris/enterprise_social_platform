"""
Feed ranking model training.

Trains an XGBoost binary classifier to predict user engagement (click/react/comment)
from feed impression features. Outputs a model in XGBoost's .ubj format compatible
with the GBDT Accelerated Ranker Framework for GPU-accelerated inference.

Usage:
    python train.py                          # Train with synthetic data
    python train.py --data feed_data.parquet # Train with collected data
"""

import argparse
import json
import time
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Optional

import joblib
import numpy as np
import pandas as pd
import xgboost as xgb
from sklearn.metrics import (
    roc_auc_score,
    average_precision_score,
    classification_report,
)
from sklearn.model_selection import StratifiedKFold, train_test_split

from dataset import FEATURE_NAMES, LABEL_COL, USER_FEATURE_COUNT, generate_dataset


# Validation thresholds
AUC_ROC_THRESHOLD = 0.65  # Lower than fraud model since engagement is noisier
AUC_PR_THRESHOLD = 0.25


@dataclass
class TrainingResult:
    model_name: str
    auc_roc: float
    auc_pr: float
    n_train: int
    n_test: int
    n_features: int
    positive_rate: float
    hyperparams: dict
    feature_importance: dict
    training_time_s: float
    passed_validation: bool


def train_model(
    X: np.ndarray,
    y: np.ndarray,
    output_dir: str = "output",
    model_name: str = "feed_ranker",
    n_trees: int = 200,
    max_depth: int = 6,
    learning_rate: float = 0.1,
) -> TrainingResult:
    """Train XGBoost model and save artifacts."""

    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)

    # Split data
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, stratify=y, random_state=42
    )

    print(f"Training set: {X_train.shape[0]} samples ({y_train.mean():.3f} positive rate)")
    print(f"Test set:     {X_test.shape[0]} samples ({y_test.mean():.3f} positive rate)")

    # Hyperparameters
    params = {
        "n_estimators": n_trees,
        "max_depth": max_depth,
        "learning_rate": learning_rate,
        "objective": "binary:logistic",
        "eval_metric": "auc",
        "tree_method": "hist",
        "subsample": 0.8,
        "colsample_bytree": 0.8,
        "min_child_weight": 5,
        "gamma": 0.1,
        "reg_alpha": 0.1,
        "reg_lambda": 1.0,
        "random_state": 42,
        "n_jobs": -1,
    }

    print(f"\nTraining XGBoost with {n_trees} trees, depth {max_depth}...")
    start = time.time()

    model = xgb.XGBClassifier(**params)
    model.fit(
        X_train, y_train,
        eval_set=[(X_test, y_test)],
        verbose=False,
    )

    elapsed = time.time() - start
    print(f"Training completed in {elapsed:.1f}s")

    # Evaluate
    y_pred_proba = model.predict_proba(X_test)[:, 1]
    auc_roc = roc_auc_score(y_test, y_pred_proba)
    auc_pr = average_precision_score(y_test, y_pred_proba)

    y_pred = (y_pred_proba > 0.5).astype(int)
    print(f"\nAUC-ROC: {auc_roc:.4f}")
    print(f"AUC-PR:  {auc_pr:.4f}")
    print(f"\nClassification Report:\n{classification_report(y_test, y_pred)}")

    # Feature importance
    importance = dict(zip(FEATURE_NAMES, model.feature_importances_.tolist()))
    print("Feature Importance:")
    for name, imp in sorted(importance.items(), key=lambda x: -x[1]):
        bar = "█" * int(imp * 50)
        print(f"  {name:25s} {imp:.4f} {bar}")

    # Cross-validation
    print(f"\n5-Fold Cross-Validation:")
    cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=42)
    cv_scores = []
    for fold, (train_idx, val_idx) in enumerate(cv.split(X, y)):
        fold_model = xgb.XGBClassifier(**params)
        fold_model.fit(X[train_idx], y[train_idx], verbose=False)
        fold_pred = fold_model.predict_proba(X[val_idx])[:, 1]
        fold_auc = roc_auc_score(y[val_idx], fold_pred)
        cv_scores.append(fold_auc)
        print(f"  Fold {fold+1}: AUC-ROC = {fold_auc:.4f}")
    print(f"  Mean: {np.mean(cv_scores):.4f} ± {np.std(cv_scores):.4f}")

    # Validation gate
    passed = auc_roc >= AUC_ROC_THRESHOLD and auc_pr >= AUC_PR_THRESHOLD
    print(f"\nValidation: {'PASSED ✓' if passed else 'FAILED ✗'}")
    if not passed:
        print(f"  AUC-ROC {auc_roc:.4f} {'≥' if auc_roc >= AUC_ROC_THRESHOLD else '<'} {AUC_ROC_THRESHOLD}")
        print(f"  AUC-PR  {auc_pr:.4f} {'≥' if auc_pr >= AUC_PR_THRESHOLD else '<'} {AUC_PR_THRESHOLD}")

    # Save model in XGBoost native format (for GBDT ranker framework)
    model_ubj_path = output_path / f"{model_name}.ubj"
    model.get_booster().save_model(str(model_ubj_path))
    print(f"\nSaved XGBoost model: {model_ubj_path}")

    # Also save as JSON (human-readable, also supported by ranker framework)
    model_json_path = output_path / f"{model_name}.json"
    model.get_booster().save_model(str(model_json_path))
    print(f"Saved JSON model:    {model_json_path}")

    # Save joblib for Python inference
    joblib_path = output_path / f"{model_name}.joblib"
    joblib.dump(model, str(joblib_path))

    # Save training result
    result = TrainingResult(
        model_name=model_name,
        auc_roc=float(auc_roc),
        auc_pr=float(auc_pr),
        n_train=int(X_train.shape[0]),
        n_test=int(X_test.shape[0]),
        n_features=int(X.shape[1]),
        positive_rate=float(y.mean()),
        hyperparams=params,
        feature_importance=importance,
        training_time_s=float(elapsed),
        passed_validation=passed,
    )

    result_path = output_path / f"{model_name}_result.json"
    with open(result_path, "w") as f:
        json.dump(asdict(result), f, indent=2, default=str)
    print(f"Saved training result: {result_path}")

    # Save feature config for the ranker framework
    feature_config = {
        "model_file": f"{model_name}.ubj",
        "total_features": len(FEATURE_NAMES),
        "user_features": USER_FEATURE_COUNT,
        "feature_names": FEATURE_NAMES,
        "user_feature_names": ["affinity", "social_distance"],
        "item_feature_names": [f for i, f in enumerate(FEATURE_NAMES) if i not in [2, 9]],
    }
    config_path = output_path / "feature_config.json"
    with open(config_path, "w") as f:
        json.dump(feature_config, f, indent=2)
    print(f"Saved feature config: {config_path}")

    return result


def main():
    parser = argparse.ArgumentParser(description="Train feed ranking model")
    parser.add_argument("--data", type=str, help="Path to training data (parquet)")
    parser.add_argument("--output", type=str, default="output", help="Output directory")
    parser.add_argument("--trees", type=int, default=200, help="Number of trees")
    parser.add_argument("--depth", type=int, default=6, help="Max tree depth")
    parser.add_argument("--lr", type=float, default=0.1, help="Learning rate")
    parser.add_argument("--samples", type=int, default=100_000, help="Synthetic data size")
    args = parser.parse_args()

    if args.data:
        print(f"Loading data from {args.data}")
        df = pd.read_parquet(args.data)
        X = df[FEATURE_NAMES].values.astype(np.float32)
        y = df[LABEL_COL].values.astype(np.float32)
    else:
        print(f"Generating {args.samples} synthetic impressions")
        df, X, y = generate_dataset(n_impressions=args.samples)

    result = train_model(X, y, output_dir=args.output, n_trees=args.trees,
                         max_depth=args.depth, learning_rate=args.lr)

    if result.passed_validation:
        print(f"\n{'='*60}")
        print(f"Model ready for deployment!")
        print(f"Next: Generate ranking kernel with GBDT accelerated ranker:")
        print(f"  python -m cuda_codegen generate \\")
        print(f"    --model output/feed_ranker.ubj \\")
        print(f"    --output output/generated \\")
        print(f"    --user-features {USER_FEATURE_COUNT} \\")
        print(f"    --library")
        print(f"{'='*60}")


if __name__ == "__main__":
    main()
