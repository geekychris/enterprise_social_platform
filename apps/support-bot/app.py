"""
WorkSphere Support Bot

A webhook-based app that:
1. Listens for POST_CREATED events on installed support pages
2. Searches FAQ knowledge base for matching answers
3. If found: replies with a card containing the answer
4. If not found: creates a support case and replies with the case number
5. Support agents can resolve cases, which trains the knowledge base
"""
import hashlib
import hmac
import json
import os
import sys
from flask import Flask, request, jsonify
from knowledge_base import KnowledgeBase
from case_manager import CaseManager
from config import API_KEY, WEBHOOK_PORT

app = Flask(__name__)
kb = KnowledgeBase()
cm = CaseManager()


def verify_signature(payload: bytes, signature: str) -> bool:
    """Verify webhook signature from WorkSphere."""
    # In development, skip verification. In production, use a dedicated webhook secret.
    if os.environ.get("SKIP_SIGNATURE_VERIFY", "true").lower() == "true":
        return True
    if not API_KEY or not signature:
        return True
    expected = "sha256=" + hmac.new(API_KEY.encode(), payload, hashlib.sha256).hexdigest()
    return hmac.compare_digest(signature, expected)


@app.route("/webhook", methods=["POST"])
def webhook():
    """Handle incoming events from WorkSphere."""
    # Verify signature
    sig = request.headers.get("X-WorkSphere-Signature", "")
    if not verify_signature(request.data, sig):
        return jsonify({"error": "Invalid signature"}), 401

    event = request.json
    event_type = event.get("event", "")

    print(f"[Support Bot] Received event: {event_type}")

    if event_type == "POST_CREATED":
        handle_new_post(event)
    elif event_type == "COMMENT_CREATED":
        handle_new_comment(event)

    return jsonify({"status": "ok"}), 200


def handle_new_post(event: dict):
    """Handle a new post on a support page."""
    post = event.get("data", {}).get("post", {})
    # Handle both nested and flat payload formats
    post_id = post.get("id") or post.get("postId")
    content = post.get("content", "")
    author = post.get("author", {})
    author_name = author.get("displayName", "User")
    author_id = author.get("id") or post.get("authorId")
    installation = event.get("installation", {})

    print(f"[Support Bot] Received post data: post_id={post_id}, content='{content[:50]}', author_id={author_id}, raw_post_keys={list(post.keys())}")

    if not post_id or not content:
        print(f"[Support Bot] Skipping: post_id={post_id}, content empty={not content}")
        return

    print(f"[Support Bot] New question from {author_name}: {content[:80]}...")

    # Search knowledge base
    result = kb.search(content)

    if result and result["score"] >= 0.3:
        # Found an answer — reply with a card
        card = {
            "title": "Answer Found",
            "description": result["answer"],
            "color": "#10B981",
            "fields": [
                {"name": "Matched Question", "value": result["question"]},
                {"name": "Confidence", "value": f"{result['score']:.0%}"}
            ],
            "footer": "Powered by Support Bot | Was this helpful? React with a thumbs up!"
        }
        resp = cm.post_card(post_id, card)
        if resp:
            print(f"[Support Bot] Replied with FAQ answer (score={result['score']:.2f})")
        else:
            # Fallback to markdown comment
            cm.post_comment(post_id,
                f"**Answer Found**\n\n{result['answer']}\n\n"
                f"_Matched: \"{result['question']}\" (confidence: {result['score']:.0%})_\n\n"
                f"---\n_Powered by Support Bot_"
            )
    else:
        # No match — create a support case
        case = cm.create_case(
            title=content[:200],
            description=f"Question from {author_name}:\n\n{content}",
            requester_id=author_id,
            source_post_id=post_id,
            installation_id=installation.get("id")
        )

        if case:
            case_num = case.get("caseNumber", "???")
            cm.post_comment(post_id,
                f"I couldn't find an answer to your question in our knowledge base.\n\n"
                f"**Support case created: #{case_num}**\n\n"
                f"A support agent will review your question and respond here. "
                f"You can track your case with reference number **#{case_num}**.\n\n"
                f"---\n_Powered by Support Bot_"
            )
            print(f"[Support Bot] Created case #{case_num}")
        else:
            cm.post_comment(post_id,
                f"I couldn't find an answer, and I had trouble creating a support case. "
                f"Please try again or contact support directly.\n\n---\n_Powered by Support Bot_"
            )


def handle_new_comment(event: dict):
    """Handle comments — could be an agent resolving a case."""
    # Future: detect if comment is from a support agent on a case post
    # and mark the case as resolved, learning the answer
    pass


@app.route("/health", methods=["GET"])
def health():
    return jsonify({
        "status": "ok",
        "app": "support-bot",
        "faq_count": len(kb.faqs),
        "resolved_count": len(kb.resolved_answers)
    })


if __name__ == "__main__":
    print(f"[Support Bot] Starting on port {WEBHOOK_PORT}")
    print(f"[Support Bot] FAQ entries: {len(kb.faqs)}")
    print(f"[Support Bot] WorkSphere URL: {cm.headers.get('Authorization', 'not set')[:20]}...")
    app.run(host="0.0.0.0", port=WEBHOOK_PORT, debug=True)
