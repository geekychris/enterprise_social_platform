"""
Simple FAQ knowledge base with keyword matching.
For production, use Ollama embeddings for semantic search.
"""
import json
import re
from pathlib import Path

class KnowledgeBase:
    def __init__(self, faq_path: str = None):
        if faq_path is None:
            faq_path = str(Path(__file__).parent / "faq_data.json")
        with open(faq_path) as f:
            data = json.load(f)
        self.faqs = data["faqs"]
        # Also store resolved cases for learning
        self.resolved_answers = []

    def search(self, query: str, threshold: float = 0.3) -> dict | None:
        """Search FAQs by keyword overlap. Returns best match or None."""
        query_words = set(re.findall(r'\w+', query.lower()))

        best_match = None
        best_score = 0

        for faq in self.faqs:
            # Score based on keyword overlap with question + tags
            faq_words = set(re.findall(r'\w+', faq["question"].lower()))
            faq_words.update(t.lower() for t in faq.get("tags", []))

            if not query_words or not faq_words:
                continue

            overlap = len(query_words & faq_words)
            score = overlap / max(len(query_words), 1)

            if score > best_score:
                best_score = score
                best_match = faq

        # Also check resolved cases
        for resolved in self.resolved_answers:
            resolved_words = set(re.findall(r'\w+', resolved["question"].lower()))
            overlap = len(query_words & resolved_words)
            score = overlap / max(len(query_words), 1)
            if score > best_score:
                best_score = score
                best_match = resolved

        if best_score >= threshold and best_match:
            return {"answer": best_match["answer"], "question": best_match["question"], "score": best_score}
        return None

    def add_resolved_case(self, question: str, answer: str):
        """Learn from resolved cases."""
        self.resolved_answers.append({"question": question, "answer": answer, "tags": []})
