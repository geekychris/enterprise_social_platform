# Test Accounts & Login Information

## Authentication

All test accounts share the same password: **`password`**

You can login via:
1. **Username/password**: Use any generated username with password `password`
2. **Register**: Create a new account from the login page
3. **Debug mode**: Toggle "Debug Mode" on the login page and enter a raw user ID (no password needed)

### Debug Mode

Debug mode is enabled by default in development (`social.auth.debug-bypass: true` in application.yml). When active:
- The login page shows a "Debug Mode" toggle
- Enter any valid user ID to instantly log in as that user
- A yellow "DEBUG" badge appears next to your name in the header
- Every API request sends an `X-Debug-User-Id` header instead of a JWT token
- No password is needed

This is the fastest way to test - just enter a user ID and go.

## Admin Users

The first 3 users are admins and can access the Admin Dashboard at `/admin`.

| Username | Display Name | User ID | Email | Password |
|----------|-------------|---------|-------|----------|
| lamar.lehner | Lamar Lehner | `72057594037927937` | lamar.lehner@enterprise.com | password |
| joshua.padberg | Joshua Padberg | `72057594037927938` | joshua.padberg@enterprise.com | password |
| cecilia.watsica | Cecilia Watsica | `72057594037927939` | cecilia.watsica@enterprise.com | password |

## All Test Users (First 10)

| Username | Display Name | User ID | Email | Posts | Groups | Following | Admin |
|----------|-------------|---------|-------|-------|--------|-----------|-------|
| lamar.lehner | Lamar Lehner | `72057594037927937` | lamar.lehner@enterprise.com | 38 | 9 | 22 | Yes |
| joshua.padberg | Joshua Padberg | `72057594037927938` | joshua.padberg@enterprise.com | 39 | 4 | 12 | Yes |
| cecilia.watsica | Cecilia Watsica | `72057594037927939` | cecilia.watsica@enterprise.com | 39 | 5 | 22 | Yes |
| marcellus.beatty | Marcellus Beatty | `72057594037927940` | marcellus.beatty@enterprise.com | 51 | 12 | 12 | No |
| lila.parisian | Lila Parisian | `72057594037927941` | lila.parisian@enterprise.com | 30 | 5 | 16 | No |
| harlan.schaden | Harlan Schaden | `72057594037927942` | harlan.schaden@enterprise.com | 37 | 4 | 17 | No |
| giovanna.lind | Giovanna Lind | `72057594037927943` | giovanna.lind@enterprise.com | 28 | 6 | 10 | No |
| steve.kovacek | Steve Kovacek | `72057594037927944` | steve.kovacek@enterprise.com | 54 | 7 | 26 | No |
| lester.lang | Lester Lang | `72057594037927945` | lester.lang@enterprise.com | 36 | 5 | 7 | No |
| huey.jaskolski | Huey Jaskolski | `72057594037927946` | huey.jaskolski@enterprise.com | 35 | 8 | 23 | No |

## Quick Reference User IDs

| User # | User ID | Notes |
|--------|---------|-------|
| 1 | `72057594037927937` | Owns "Coffee Lovers" group |
| 4 | `72057594037927940` | Owns "Book Club" group |
| 5 | `72057594037927941` | Regular member |
| 7 | `72057594037927943` | Owns "Running Group" group |
| 10 | `72057594037927946` | Owns "Photography" group |

To find actual usernames, query the database:
```sql
SELECT id, username, display_name FROM users ORDER BY id LIMIT 20;
```

## Sample Group IDs

| Group # | Group ID | Group Name |
|---------|----------|------------|
| 1 | `360287970189639681` | Coffee Lovers |
| 2 | `360287970189639682` | Book Club |
| 3 | `360287970189639683` | Running Group |
| 4 | `360287970189639684` | Photography |
| 5 | `360287970189639685` | Board Games |

## Sample Page IDs

| Page # | Page ID | Page Name |
|--------|---------|-----------|
| 1 | `432345564227567617` | Company Announcements |
| 2 | `432345564227567618` | Engineering Blog |
| 3 | `432345564227567619` | Product Updates |
| 4 | `432345564227567620` | CEO Corner |
| 5 | `432345564227567621` | HR Updates |

## Quick Test Scenarios

### Post with images and links
1. Login as User 1 (debug: `72057594037927937`)
2. Type a message in the post composer
3. Click "Media" to attach images/videos
4. Paste a YouTube link - it will embed as a player
5. Paste any website URL - a preview card appears

### Create and manage a group
1. Login as any user
2. In the sidebar, click "+ New" under "My Groups"
3. Enter a name, description, and upload an avatar
4. Click Create - you'll land on the group page
5. Click "Edit" to change the name, description, avatar, or cover image
6. Post content to the group feed

### Join a restricted group
1. Login as User 5 (debug: `72057594037927941`)
2. Search for a group you don't belong to
3. Click "Join Group" - if restricted, you'll see "Pending Approval"
4. Login as the group owner to approve the request

### Send messages with attachments
1. Login as User 1
2. Click a friend's name in the sidebar to start a chat
3. Type a message and/or attach a file with the paperclip icon
4. Send - the message appears with the attachment

### Edit posts and comments
1. Create a post, then hover over it
2. Click the "..." menu and select "Edit"
3. Modify the content and click Save
4. Same for comments - hover to see the pencil icon

### Pin a post in a group
1. Login as a group owner
2. Go to the group page
3. Hover over any post, click "..." > "Pin"
4. The post appears at the top with a "Pinned" badge
5. Click "Unpin" to remove the pin

## Database Queries

```sql
-- Find users who own groups
SELECT u.id, u.username, g.id AS group_id, g.name AS group_name
FROM memberships m
JOIN users u ON u.id = m.user_id
JOIN groups_ g ON g.id = m.group_id
WHERE m.role = 'OWNER' ORDER BY g.id;

-- Find users with most group memberships
SELECT u.id, u.username, COUNT(*) AS group_count
FROM memberships m JOIN users u ON u.id = m.user_id
GROUP BY u.id, u.username ORDER BY group_count DESC LIMIT 10;

-- Check messages between two users
SELECT m.id, m.sender_id, m.recipient_id, m.content, m.read, m.created_at
FROM messages m
WHERE (m.sender_id = 72057594037927937 AND m.recipient_id = 72057594037927945)
   OR (m.sender_id = 72057594037927945 AND m.recipient_id = 72057594037927937)
ORDER BY m.created_at DESC LIMIT 10;

-- Check attachment deduplication
SELECT content_hash, COUNT(*) FROM attachments
WHERE content_hash IS NOT NULL
GROUP BY content_hash HAVING COUNT(*) > 1;
```
