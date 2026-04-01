"""
WorkSphere Slash Commands App

Handles:
  /joke <topic>  -- generates a knock-knock joke about the topic
  /stock <TICKER> -- looks up stock price and posts a card
"""
import os
import sys
import json
import random
import hashlib
import hmac
import requests as req
from flask import Flask, request, jsonify
from config import WORKSPHERE_URL, WEBHOOK_PORT, APP_ID, API_KEY

app = Flask(__name__)

HEADERS = {
    "Authorization": f"Bearer {API_KEY}",
    "X-App-Id": str(APP_ID),
    "X-Tenant-Id": "1",
    "Content-Type": "application/json"
}

# -- Joke Generator --

KNOCK_KNOCK_TEMPLATES = [
    ("Who's there?", "{topic}.", "{topic} who?", "{topic} {punchline}!"),
]

PUNCHLINES = {
    "default": [
        "is what I was going to say, but I forgot the rest",
        "walked into a bar and the bartender said 'we don't serve your kind here'",
        "called, they want their joke back",
        "is the answer to everything, didn't you know?",
        "but make it enterprise-grade",
    ],
    "coffee": [
        "better latte than never",
        "you mocha me crazy",
        "espresso yourself",
        "grounds for celebration",
    ],
    "code": [
        "it works on my machine",
        "have you tried turning it off and on again",
        "there are only 10 types of people who understand this",
        "it's not a bug, it's a feature",
    ],
    "work": [
        "sounds like a Monday problem",
        "per my last email, that's hilarious",
        "let's take this offline and laugh about it later",
        "I'll add that to the backlog of jokes",
    ],
    "meeting": [
        "this could have been an email",
        "let's circle back on that punchline",
        "I'll ping you the funny part async",
        "that's a great joke, let's put a pin in it",
    ],
}

def generate_joke(topic: str) -> str:
    """Generate a knock-knock style joke about a topic."""
    topic_lower = topic.lower().strip()

    # Find matching punchlines
    punchline_pool = PUNCHLINES.get("default", [])
    for key, lines in PUNCHLINES.items():
        if key in topic_lower:
            punchline_pool = lines
            break

    punchline = random.choice(punchline_pool)
    topic_word = topic.split()[0] if topic.split() else "Something"

    joke = f"**Knock knock!** \U0001f6aa\n\n"
    joke += f"*Who's there?*\n\n"
    joke += f"**{topic_word}.**\n\n"
    joke += f"*{topic_word} who?*\n\n"
    joke += f"**{topic_word}** -- {punchline}! \U0001f604\n\n"
    joke += f"---\n_/joke by Slash Commands App_"

    return joke


# -- Stock Lookup --

# Simulated stock data (in production, use Alpha Vantage, Yahoo Finance, etc.)
MOCK_STOCKS = {
    "AAPL": {"name": "Apple Inc.", "price": 178.72, "change": 2.34, "pct": 1.33, "high": 180.10, "low": 175.50, "volume": "52.3M", "pe": 28.5, "mktcap": "2.78T"},
    "GOOGL": {"name": "Alphabet Inc.", "price": 141.80, "change": -0.92, "pct": -0.64, "high": 143.20, "low": 140.50, "volume": "18.7M", "pe": 24.1, "mktcap": "1.78T"},
    "MSFT": {"name": "Microsoft Corp.", "price": 415.50, "change": 3.12, "pct": 0.76, "high": 417.00, "low": 412.30, "volume": "22.1M", "pe": 35.2, "mktcap": "3.09T"},
    "AMZN": {"name": "Amazon.com Inc.", "price": 182.30, "change": 1.55, "pct": 0.86, "high": 183.80, "low": 180.00, "volume": "34.5M", "pe": 52.8, "mktcap": "1.90T"},
    "TSLA": {"name": "Tesla Inc.", "price": 175.20, "change": -4.30, "pct": -2.39, "high": 180.50, "low": 174.00, "volume": "78.2M", "pe": 48.3, "mktcap": "557B"},
    "META": {"name": "Meta Platforms Inc.", "price": 505.75, "change": 8.20, "pct": 1.65, "high": 508.00, "low": 498.00, "volume": "15.3M", "pe": 27.8, "mktcap": "1.29T"},
    "NVDA": {"name": "NVIDIA Corp.", "price": 875.30, "change": 12.40, "pct": 1.44, "high": 880.00, "low": 860.00, "volume": "42.8M", "pe": 65.4, "mktcap": "2.16T"},
    "NFLX": {"name": "Netflix Inc.", "price": 628.90, "change": -3.50, "pct": -0.55, "high": 635.00, "low": 625.00, "volume": "5.2M", "pe": 42.1, "mktcap": "272B"},
    "JPM": {"name": "JPMorgan Chase & Co.", "price": 198.40, "change": 1.80, "pct": 0.92, "high": 199.50, "low": 196.00, "volume": "8.9M", "pe": 11.5, "mktcap": "571B"},
    "V": {"name": "Visa Inc.", "price": 278.60, "change": 0.45, "pct": 0.16, "high": 280.00, "low": 277.00, "volume": "6.1M", "pe": 30.2, "mktcap": "572B"},
}

def lookup_stock(ticker: str) -> str:
    """Look up stock data and format as a card-style message."""
    ticker = ticker.upper().strip()
    stock = MOCK_STOCKS.get(ticker)

    if not stock:
        return (f"**Stock not found: {ticker}** \U0001f4c9\n\n"
                f"Available tickers: {', '.join(sorted(MOCK_STOCKS.keys()))}\n\n"
                f"---\n_/stock by Slash Commands App_")

    change_emoji = "\U0001f4c8" if stock["change"] >= 0 else "\U0001f4c9"
    change_sign = "+" if stock["change"] >= 0 else ""

    msg = f"## {stock['name']} ({ticker}) {change_emoji}\n\n"
    msg += f"**${stock['price']:.2f}** &nbsp; {change_sign}{stock['change']:.2f} ({change_sign}{stock['pct']:.2f}%)\n\n"
    msg += f"| | |\n|---|---|\n"
    msg += f"| **Day Range** | ${stock['low']:.2f} — ${stock['high']:.2f} |\n"
    msg += f"| **Volume** | {stock['volume']} |\n"
    msg += f"| **P/E Ratio** | {stock['pe']} |\n"
    msg += f"| **Market Cap** | ${stock['mktcap']} |\n\n"
    msg += f"---\n\n_/stock • Data is simulated for demo_"

    return msg


# -- Webhook Handler --

@app.route("/webhook", methods=["POST"])
def webhook():
    event = request.json
    event_type = event.get("event", "")

    if event_type == "POST_CREATED":
        post = event.get("data", {}).get("post", {})
        content = post.get("content", "")
        post_id = post.get("postId") or post.get("id")

        if not post_id or not content:
            return jsonify({"status": "ignored"}), 200

        content = content.strip()

        if content.startswith("/joke"):
            topic = content[5:].strip() or "programming"
            response = generate_joke(topic)
            post_comment(post_id, response)
            print(f"[SlashCmd] /joke '{topic}' on post {post_id}")

        elif content.startswith("/stock"):
            ticker = content[6:].strip() or "AAPL"
            response = lookup_stock(ticker)
            post_comment(post_id, response)
            print(f"[SlashCmd] /stock '{ticker}' on post {post_id}")

    return jsonify({"status": "ok"}), 200


def post_comment(post_id, content: str):
    """Post a comment via the WorkSphere App API."""
    try:
        resp = req.post(f"{WORKSPHERE_URL}/api/apps/comments", json={
            "postId": post_id,
            "content": content
        }, headers=HEADERS, timeout=10)
        if not resp.ok:
            print(f"[SlashCmd] Comment failed: {resp.status_code} {resp.text[:100]}")
    except Exception as e:
        print(f"[SlashCmd] Comment error: {e}")


@app.route("/health", methods=["GET"])
def health():
    return jsonify({
        "status": "ok",
        "app": "slash-commands",
        "commands": ["/joke", "/stock"],
        "stocks": len(MOCK_STOCKS)
    })


if __name__ == "__main__":
    print(f"[SlashCmd] Starting on port {WEBHOOK_PORT}")
    print(f"[SlashCmd] Commands: /joke, /stock")
    print(f"[SlashCmd] Available tickers: {', '.join(sorted(MOCK_STOCKS.keys()))}")
    app.run(host="0.0.0.0", port=WEBHOOK_PORT, debug=False)
