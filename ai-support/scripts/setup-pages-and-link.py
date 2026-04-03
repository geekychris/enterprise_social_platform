#!/usr/bin/env python3
"""Create support pages in the social app and link them to AI Support knowledge sets."""
import requests
import json

SOCIAL_BASE = 'http://localhost:8088'
AI_BASE = 'http://localhost:8090'
AUTH = {'X-Debug-User-Id': '72057594037927937'}

# Page definitions: (name, description, knowledge_set_id)
PAGES = [
    ('Amiga Support', 'Ask questions about Commodore Amiga computers — hardware, software, emulation, and programming. AI-powered answers from our knowledge base.', 1),
    ('Gowin FPGA Support', 'Get help with Gowin FPGA development — Tang Nano boards, Verilog, pin constraints, and more. AI-powered answers.', 2),
    ('Atari 8-bit Support', 'Support for Atari 400/800/XL/XE computers — hardware, BASIC programming, storage, and repairs. AI-powered answers.', 3),
    ('Geek Help', 'General retro computing and FPGA help. Ask anything — we will route your question to the right specialist page if needed.', 4),
]

print('=== Creating Support Pages ===')
page_ids = {}

for name, desc, ks_id in PAGES:
    # Check if page already exists
    existing = requests.get(f'{SOCIAL_BASE}/api/pages/search', params={'q': name}, headers=AUTH).json()
    found = [p for p in existing if p['name'] == name]

    if found:
        page_id = found[0]['id']
        print(f'  Page "{name}" already exists: {page_id}')
    else:
        r = requests.post(f'{SOCIAL_BASE}/api/pages', json={
            'name': name,
            'description': desc,
            'visibility': 'PUBLIC',
        }, headers=AUTH)
        data = r.json()
        page_id = data.get('id')
        print(f'  Created page "{name}": {page_id}')

    page_ids[ks_id] = page_id

    # Post a welcome message on the page
    requests.post(f'{SOCIAL_BASE}/api/posts', json={
        'content': f'Welcome to {name}! 🤖\n\nThis is an AI-powered support page. Ask your questions here and our AI assistant will search our knowledge base to help you.\n\nFeel free to post questions about anything related to this topic. If the AI can\'t help, we\'ll connect you with a human.',
        'targetType': 'PAGE_FEED',
        'targetId': str(page_id),
    }, headers=AUTH)

print()
print('=== Linking Knowledge Sets to Pages ===')

for ks_id, page_id in page_ids.items():
    # Update knowledge set with social page ID
    r = requests.put(f'{AI_BASE}/api/knowledge/sets/{ks_id}', json={
        'socialPageId': page_id,
        'socialPageType': 'PAGE',
    })
    if r.status_code == 200:
        print(f'  KS {ks_id} -> Page {page_id} ✓')
    else:
        print(f'  KS {ks_id} -> FAILED: {r.status_code} {r.text[:100]}')

print()
print('=== Verification ===')
for ks_id in [1, 2, 3, 4]:
    r = requests.get(f'{AI_BASE}/api/knowledge/sets/{ks_id}')
    d = r.json()
    print(f'  KS [{d.get("id")}] {d.get("name")} -> socialPageId={d.get("socialPageId")}')

print()
print('=== Page URLs ===')
for name, _, ks_id in PAGES:
    pid = page_ids.get(ks_id)
    if pid:
        print(f'  {name}: http://localhost:3999/page/{pid}')
