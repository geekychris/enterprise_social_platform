import { useEffect, useRef, useCallback } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useAuth } from './useAuth';

/**
 * Connects to the Netty WebSocket gateway for real-time message push.
 * When a message arrives, invalidates React Query caches so the UI refreshes instantly.
 * Falls back gracefully — if the gateway is unavailable, polling still works.
 */
export function useWebSocket() {
  const { userId, token } = useAuth();
  const queryClient = useQueryClient();
  const wsRef = useRef<WebSocket | null>(null);
  const reconnectTimer = useRef<ReturnType<typeof setTimeout>>();

  const connect = useCallback(() => {
    if (wsRef.current?.readyState === WebSocket.OPEN) return;
    if (!userId) return;

    // Gateway runs on port 8090
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = window.location.hostname;
    const authParam = token ? `token=${token}` : `userId=${userId}`;
    const url = `${protocol}//${host}:8090/ws?${authParam}`;

    try {
      const ws = new WebSocket(url);
      wsRef.current = ws;

      ws.onopen = () => {
        console.log('[WS] Connected to gateway');
      };

      ws.onmessage = (event) => {
        try {
          const msg = JSON.parse(event.data);
          if (msg.type === 'MESSAGE') {
            // Invalidate message and conversation queries for instant UI update
            queryClient.invalidateQueries({ queryKey: ['messages', msg.conversationId] });
            queryClient.invalidateQueries({ queryKey: ['messages', String(msg.conversationId)] });
            queryClient.invalidateQueries({ queryKey: ['conversations'] });
          } else if (msg.type === 'POST_UPDATE') {
            // New comment or reaction — refresh everything that shows this post
            const postId = msg.postId;
            queryClient.invalidateQueries({ queryKey: ['post', postId] });
            queryClient.invalidateQueries({ queryKey: ['post', String(postId)] });
            queryClient.invalidateQueries({ queryKey: ['comments', postId] });
            queryClient.invalidateQueries({ queryKey: ['comments', String(postId)] });
            // Invalidate all feed/post list queries (feed, group-posts, page-posts, etc.)
            queryClient.invalidateQueries({ queryKey: ['feed'] });
            queryClient.invalidateQueries({ predicate: (q) => {
              const key = q.queryKey[0];
              return key === 'group-posts' || key === 'page-posts' || key === 'feed' || key === 'pinned-post';
            }});
          }
        } catch {}
      };

      ws.onclose = () => {
        console.log('[WS] Disconnected, reconnecting in 3s...');
        wsRef.current = null;
        reconnectTimer.current = setTimeout(connect, 3000);
      };

      ws.onerror = () => {
        ws.close();
      };
    } catch {
      // Gateway not available — polling fallback
    }
  }, [userId, token, queryClient]);

  useEffect(() => {
    connect();
    return () => {
      clearTimeout(reconnectTimer.current);
      wsRef.current?.close();
      wsRef.current = null;
    };
  }, [connect]);
}
