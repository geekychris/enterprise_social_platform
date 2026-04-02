"""Support Bot Configuration"""
import os

WORKSPHERE_URL = os.environ.get("WORKSPHERE_URL", "http://localhost:8088")
WEBHOOK_PORT = int(os.environ.get("WEBHOOK_PORT", "5050"))
APP_ID = os.environ.get("APP_ID", "")
API_KEY = os.environ.get("API_KEY", "")
OLLAMA_URL = os.environ.get("OLLAMA_URL", "http://localhost:11434")
