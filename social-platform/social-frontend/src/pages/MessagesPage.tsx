import { useState, useEffect, useRef, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import api from '../api/client';
import type { ConversationDto, MessageDto } from '../api/types';
import { useAuth } from '../hooks/useAuth';
import { formatRelativeTime } from '../utils';

export default function MessagesPage() {
  const { partnerId: partnerIdParam } = useParams<{ partnerId: string }>();
  const [selectedPartnerId, setSelectedPartnerId] = useState<string | null>(
    partnerIdParam ?? null,
  );
  const navigate = useNavigate();
  const { userId } = useAuth();

  useEffect(() => {
    if (partnerIdParam) {
      setSelectedPartnerId(partnerIdParam);
    }
  }, [partnerIdParam]);

  const handleSelectPartner = (id: string | number) => {
    setSelectedPartnerId(String(id));
    navigate(`/messages/${id}`, { replace: true });
  };

  return (
    <div className="flex gap-0 -mx-4 -my-6 h-[calc(100vh-3.5rem)]">
      {/* Conversation list (left panel) */}
      <div
        className={`w-full sm:w-80 sm:min-w-[20rem] border-r border-gray-100 bg-white flex flex-col ${
          selectedPartnerId ? 'hidden sm:flex' : 'flex'
        }`}
      >
        <div className="p-4 border-b border-gray-100">
          <h2 className="text-lg font-bold text-gray-900">Messages</h2>
        </div>
        <ConversationList
          selectedPartnerId={selectedPartnerId}
          onSelect={handleSelectPartner}
        />
      </div>

      {/* Message thread (right panel) */}
      <div
        className={`flex-1 flex flex-col bg-gray-50 ${
          selectedPartnerId ? 'flex' : 'hidden sm:flex'
        }`}
      >
        {selectedPartnerId ? (
          <MessageThread
            partnerId={selectedPartnerId}
            currentUserId={userId!}
            onBack={() => {
              setSelectedPartnerId(null);
              navigate('/messages', { replace: true });
            }}
          />
        ) : (
          <div className="flex-1 flex items-center justify-center">
            <div className="text-center text-gray-400">
              <MailIcon className="w-12 h-12 mx-auto mb-3 text-gray-300" />
              <p className="text-sm">Select a conversation to start messaging</p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Conversation List                                                   */
/* ------------------------------------------------------------------ */

function ConversationList({
  selectedPartnerId,
  onSelect,
}: {
  selectedPartnerId: string | number | null;
  onSelect: (id: number) => void;
}) {
  const { data: conversations, isLoading } = useQuery<ConversationDto[]>({
    queryKey: ['conversations'],
    queryFn: async () => {
      const { data } = await api.get('/messages/conversations');
      return data;
    },
    refetchInterval: 10000,
  });

  if (isLoading) {
    return (
      <div className="flex-1 overflow-y-auto p-2 space-y-1">
        {[1, 2, 3, 4].map((i) => (
          <div key={i} className="flex items-center gap-3 p-3">
            <div className="skeleton w-10 h-10 rounded-full" />
            <div className="flex-1 space-y-1.5">
              <div className="skeleton h-3 w-24" />
              <div className="skeleton h-2.5 w-40" />
            </div>
          </div>
        ))}
      </div>
    );
  }

  if (!conversations?.length) {
    return (
      <div className="flex-1 flex items-center justify-center p-4">
        <p className="text-sm text-gray-400">No conversations yet</p>
      </div>
    );
  }

  return (
    <div className="flex-1 overflow-y-auto">
      {conversations.map((conv) => {
        const isActive = String(conv.partner.id) === String(selectedPartnerId);
        return (
          <button
            key={conv.partner.id}
            onClick={() => onSelect(conv.partner.id)}
            className={`w-full flex items-center gap-3 p-3 text-left transition-colors ${
              isActive
                ? 'bg-primary-50'
                : 'hover:bg-gray-50'
            }`}
          >
            {conv.partner.avatarUrl ? (
              <img
                src={conv.partner.avatarUrl}
                alt=""
                className="w-10 h-10 rounded-full object-cover shrink-0"
              />
            ) : (
              <div className="w-10 h-10 bg-primary-500 text-white rounded-full flex items-center justify-center text-sm font-semibold shrink-0">
                {conv.partner.displayName?.[0]?.toUpperCase() ??
                  conv.partner.username?.[0]?.toUpperCase() ??
                  '?'}
              </div>
            )}
            <div className="flex-1 min-w-0">
              <div className="flex items-center justify-between">
                <span className="text-sm font-semibold text-gray-900 truncate">
                  {conv.partner.displayName || conv.partner.username}
                </span>
                <span className="text-[10px] text-gray-400 shrink-0 ml-2">
                  {formatRelativeTime(conv.lastMessage.createdAt)}
                </span>
              </div>
              <div className="flex items-center justify-between">
                <p className="text-xs text-gray-500 truncate">
                  {conv.lastMessage.content}
                </p>
                {conv.unreadCount > 0 && (
                  <span className="ml-2 shrink-0 w-5 h-5 bg-primary-500 text-white rounded-full text-[10px] flex items-center justify-center font-bold">
                    {conv.unreadCount > 9 ? '9+' : conv.unreadCount}
                  </span>
                )}
              </div>
            </div>
          </button>
        );
      })}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Message Thread                                                      */
/* ------------------------------------------------------------------ */

function MessageThread({
  partnerId,
  currentUserId,
  onBack,
}: {
  partnerId: string | number;
  currentUserId: string | number;
  onBack: () => void;
}) {
  const [messageText, setMessageText] = useState('');
  const [files, setFiles] = useState<File[]>([]);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const queryClient = useQueryClient();

  // Fetch partner user info
  const { data: partner } = useQuery<{ id: string | number; username: string; displayName: string; avatarUrl: string | null }>({
    queryKey: ['user-summary', partnerId],
    queryFn: async () => {
      const { data } = await api.get(`/users/${partnerId}`);
      return data;
    },
    staleTime: 5 * 60 * 1000,
  });

  const { data: messages, isLoading } = useQuery<MessageDto[]>({
    queryKey: ['messages', partnerId],
    queryFn: async () => {
      const { data } = await api.get(`/messages/conversation/${partnerId}`);
      return data;
    },
    refetchInterval: 3000,
  });

  // Mark as read when opening conversation
  useEffect(() => {
    api.post(`/messages/conversation/${partnerId}/read`).catch(() => {});
    queryClient.invalidateQueries({ queryKey: ['conversations'] });
    queryClient.invalidateQueries({ queryKey: ['unread-count'] });
  }, [partnerId, queryClient]);

  // Auto-scroll to bottom
  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, []);

  useEffect(() => {
    scrollToBottom();
  }, [messages, scrollToBottom]);

  const sendMessage = useMutation({
    mutationFn: async () => {
      const currentFiles = files;
      const currentText = messageText;

      const attachmentIds: number[] = [];
      for (const file of currentFiles) {
        const form = new FormData();
        form.append('file', file);
        const { data } = await api.post('/attachments/upload', form);
        attachmentIds.push(data.id);
      }

      return api.post('/messages', {
        recipientId: partnerId,
        content: currentText || '',
        attachmentIds: attachmentIds.length ? attachmentIds : undefined,
      });
    },
    onSuccess: () => {
      setMessageText('');
      setFiles([]);
      queryClient.invalidateQueries({ queryKey: ['messages', partnerId] });
      queryClient.invalidateQueries({ queryKey: ['conversations'] });
    },
    onError: (err) => {
      console.error('Failed to send message:', err);
    },
  });

  const canSend = (messageText.trim().length > 0 || files.length > 0) && !sendMessage.isPending;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!canSend) return;
    sendMessage.mutate();
  };

  return (
    <>
      {/* Thread header */}
      <div className="flex items-center gap-3 p-4 bg-white border-b border-gray-100">
        <button
          onClick={onBack}
          className="sm:hidden p-1 hover:bg-gray-100 rounded-lg transition-colors"
        >
          <svg className="w-5 h-5 text-gray-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
          </svg>
        </button>
        {partner?.avatarUrl ? (
          <img src={partner.avatarUrl} alt="" className="w-8 h-8 rounded-full object-cover" />
        ) : (
          <div className="w-8 h-8 bg-primary-500 text-white rounded-full flex items-center justify-center text-sm font-semibold">
            {partner?.displayName?.[0]?.toUpperCase() ?? partner?.username?.[0]?.toUpperCase() ?? '?'}
          </div>
        )}
        <span className="text-sm font-semibold text-gray-900">
          {partner?.displayName || partner?.username || 'Loading...'}
        </span>
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto p-4 space-y-3">
        {isLoading ? (
          <div className="space-y-3">
            {[1, 2, 3].map((i) => (
              <div
                key={i}
                className={`flex ${i % 2 === 0 ? 'justify-end' : 'justify-start'}`}
              >
                <div className="skeleton h-10 w-48 rounded-2xl" />
              </div>
            ))}
          </div>
        ) : messages && messages.length > 0 ? (
          messages.map((msg) => {
            const isSent = String(msg.sender.id) === String(currentUserId);
            return (
              <div
                key={msg.id}
                className={`flex ${isSent ? 'justify-end' : 'justify-start'}`}
              >
                <div className="flex items-end gap-2 max-w-[75%]">
                  {!isSent && (
                    <div className="w-6 h-6 bg-primary-500 text-white rounded-full flex items-center justify-center text-[10px] font-semibold shrink-0">
                      {msg.sender.displayName?.[0]?.toUpperCase() ??
                        msg.sender.username?.[0]?.toUpperCase() ??
                        '?'}
                    </div>
                  )}
                  <div>
                    <div
                      className={`px-3.5 py-2 rounded-2xl text-sm ${
                        isSent
                          ? 'bg-primary-500 text-white rounded-br-md'
                          : 'bg-gray-100 text-gray-800 rounded-bl-md'
                      }`}
                    >
                      {msg.content}
                    </div>
                    {/* Attachments */}
                    {msg.attachments?.length > 0 && (
                      <div className="mt-1 flex flex-wrap gap-1">
                        {msg.attachments.map((att) =>
                          att.mediaType?.startsWith('image/') ? (
                            <img
                              key={att.id}
                              src={att.fileUrl}
                              alt=""
                              className="w-32 h-24 object-cover rounded-lg"
                            />
                          ) : (
                            <a
                              key={att.id}
                              href={att.fileUrl}
                              target="_blank"
                              rel="noreferrer"
                              className="text-xs text-primary-500 underline"
                            >
                              Attachment
                            </a>
                          ),
                        )}
                      </div>
                    )}
                    <div
                      className={`flex items-center gap-1 mt-0.5 ${
                        isSent ? 'justify-end' : 'justify-start'
                      }`}
                    >
                      <span className="text-[10px] text-gray-400">
                        {formatRelativeTime(msg.createdAt)}
                      </span>
                      {isSent && (
                        <span className="text-[10px] text-gray-400">
                          {msg.read ? ' -- Read' : ''}
                        </span>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            );
          })
        ) : (
          <div className="flex-1 flex items-center justify-center h-full">
            <p className="text-sm text-gray-400">
              No messages yet. Say hello!
            </p>
          </div>
        )}
        <div ref={messagesEndRef} />
      </div>

      {/* File previews */}
      {files.length > 0 && (
        <div className="px-4 py-2 bg-white border-t border-gray-100 flex gap-2 flex-wrap">
          {files.map((f, i) => (
            <div key={`${f.name}-${i}`} className="relative group">
              {f.type.startsWith('image/') ? (
                <img
                  src={URL.createObjectURL(f)}
                  alt={f.name}
                  className="w-14 h-14 object-cover rounded-lg border border-gray-200"
                />
              ) : (
                <div className="w-14 h-14 bg-gray-100 rounded-lg flex items-center justify-center border border-gray-200">
                  <span className="text-[10px] text-gray-500 truncate w-12 text-center">
                    {f.name.slice(0, 10)}
                  </span>
                </div>
              )}
              <button
                type="button"
                onClick={() => setFiles((prev) => prev.filter((_, idx) => idx !== i))}
                className="absolute -top-1.5 -right-1.5 w-5 h-5 bg-red-500 text-white rounded-full text-xs flex items-center justify-center shadow hover:bg-red-600"
              >
                x
              </button>
            </div>
          ))}
        </div>
      )}

      {/* Input area with drag-and-drop */}
      <form
        onSubmit={handleSubmit}
        onDragOver={(e) => { e.preventDefault(); e.stopPropagation(); }}
        onDrop={(e) => {
          e.preventDefault();
          e.stopPropagation();
          if (e.dataTransfer.files.length > 0) {
            setFiles((prev) => [...prev, ...Array.from(e.dataTransfer.files)]);
          }
        }}
        className="p-3 bg-white border-t border-gray-100 flex items-center gap-2"
      >
        <input
          ref={fileInputRef}
          type="file"
          multiple
          accept="image/*,video/*,audio/*,.pdf,.doc,.docx,.txt"
          style={{ position: 'absolute', left: -9999, opacity: 0, width: 1, height: 1 }}
          onChange={(e) => {
            const newFiles = Array.from(e.target.files ?? []);
            if (newFiles.length > 0) {
              setFiles((prev) => [...prev, ...newFiles]);
            }
            // Reset after copying so same file can be selected again
            e.target.value = '';
          }}
        />
        <button
          type="button"
          onClick={() => fileInputRef.current?.click()}
          className="p-2 text-gray-400 hover:text-primary-500 hover:bg-gray-50 rounded-lg transition-colors shrink-0"
          title="Attach file"
        >
          <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M15.172 7l-6.586 6.586a2 2 0 102.828 2.828l6.414-6.586a4 4 0 00-5.656-5.656l-6.415 6.585a6 6 0 108.486 8.486L20.5 13" />
          </svg>
        </button>
        <input
          type="text"
          value={messageText}
          onChange={(e) => setMessageText(e.target.value)}
          placeholder={files.length > 0 ? 'Add a message (optional)...' : 'Type a message...'}
          className="input-field flex-1"
        />
        <button
          type="submit"
          disabled={!canSend}
          className="btn-primary text-sm px-4"
        >
          {sendMessage.isPending ? (
            <span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin inline-block" />
          ) : (
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
            </svg>
          )}
        </button>
      </form>
    </>
  );
}

/* ------------------------------------------------------------------ */
/* Icon                                                                */
/* ------------------------------------------------------------------ */

function MailIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M21.75 6.75v10.5a2.25 2.25 0 01-2.25 2.25h-15a2.25 2.25 0 01-2.25-2.25V6.75m19.5 0A2.25 2.25 0 0019.5 4.5h-15a2.25 2.25 0 00-2.25 2.25m19.5 0v.243a2.25 2.25 0 01-1.07 1.916l-7.5 4.615a2.25 2.25 0 01-2.36 0L3.32 8.91a2.25 2.25 0 01-1.07-1.916V6.75" />
    </svg>
  );
}
