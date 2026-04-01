"""
Case management — creates and tracks support cases via WorkSphere API.
"""
import requests
from config import WORKSPHERE_URL, API_KEY, APP_ID

class CaseManager:
    def __init__(self):
        self.headers = {
            "Authorization": f"Bearer {API_KEY}",
            "X-App-Id": str(APP_ID),
            "X-Tenant-Id": "1",  # TODO: get from webhook event
            "Content-Type": "application/json"
        }

    def create_case(self, title: str, description: str, requester_id: int,
                    source_post_id: int = None, source_comment_id: int = None,
                    installation_id: int = None) -> dict | None:
        """Create a support case via the WorkSphere API."""
        try:
            resp = requests.post(f"{WORKSPHERE_URL}/api/apps/cases", json={
                "title": title,
                "description": description,
                "requesterId": requester_id,
                "sourcePostId": source_post_id,
                "sourceCommentId": source_comment_id,
                "appId": int(APP_ID) if APP_ID else None,
                "installationId": installation_id,
                "priority": "NORMAL"
            }, headers=self.headers, timeout=10)
            if resp.ok:
                return resp.json()
            else:
                print(f"[CaseManager] Case creation failed: {resp.status_code} {resp.text[:200]}")
        except Exception as e:
            print(f"[CaseManager] Failed to create case: {e}")
        return None

    def post_comment(self, post_id: int, content: str) -> dict | None:
        """Post a comment on behalf of the app."""
        try:
            resp = requests.post(f"{WORKSPHERE_URL}/api/apps/comments", json={
                "postId": post_id,
                "content": content
            }, headers=self.headers, timeout=10)
            if resp.ok:
                return resp.json()
            else:
                print(f"[CaseManager] Comment failed: {resp.status_code} {resp.text[:200]}")
        except Exception as e:
            print(f"[CaseManager] Failed to post comment: {e}")
        return None

    def post_card(self, post_id: int, card: dict) -> dict | None:
        """Post a rich card response."""
        try:
            resp = requests.post(f"{WORKSPHERE_URL}/api/apps/cards", json={
                "targetType": "POST",
                "targetId": post_id,
                "card": card
            }, headers=self.headers, timeout=10)
            if resp.ok:
                return resp.json()
        except Exception as e:
            print(f"Failed to post card: {e}")
        return None
