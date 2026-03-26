import { useState, useEffect, useRef, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import api from '../api/client';
import type { ConversationDto, MessageDto, AuthorDto } from '../api/types';
import { useAuth } from '../hooks/useAuth';
import { formatRelativeTime } from '../utils';
import AiAssistant from '../components/ai/AiAssistant';

export default function MessagesPage() {
  const { conversationId: convIdParam } = useParams<{ conversationId: string }>();
  const [selectedConvId, setSelectedConvId] = useState<string | null>(null);
  const [showNewConversation, setShowNewConversation] = useState(false);
  const navigate = useNavigate();
  const { userId } = useAuth();

  // Handle both conversation IDs and legacy user IDs in the URL param
  useEffect(() => {
    if (!convIdParam) {
      setSelectedConvId(null);
      return;
    }
    // Try to load as conversation; if it fails, treat as user ID and create DM
    api.get(`/conversations/${convIdParam}`).then(() => {
      setSelectedConvId(convIdParam);
    }).catch(() => {
      api.post(`/conversations/direct/${convIdParam}`).then(({ data }) => {
        const newConvId = String(data.id);
        setSelectedConvId(newConvId);
        navigate(`/messages/${newConvId}`, { replace: true });
      }).catch(() => {
        navigate('/messages', { replace: true });
      });
    });
  }, [convIdParam, navigate]);

  const handleSelectConversation = (id: string | number) => {
    setSelectedConvId(String(id));
    navigate(`/messages/${id}`, { replace: true });
  };

  return (
    <div className="flex gap-0 -mx-4 -my-6 h-[calc(100vh-3.5rem)]">
      {/* Conversation list (left panel) */}
      <div
        className={`w-full sm:w-80 sm:min-w-[20rem] border-r border-gray-100 bg-white flex flex-col ${
          selectedConvId ? 'hidden sm:flex' : 'flex'
        }`}
      >
        <div className="p-4 border-b border-gray-100 flex items-center justify-between">
          <h2 className="text-lg font-bold text-gray-900">Messages</h2>
          <button
            onClick={() => setShowNewConversation(true)}
            className="p-1.5 text-gray-500 hover:text-primary-500 hover:bg-gray-50 rounded-lg transition-colors"
            title="New conversation"
          >
            <PenSquareIcon className="w-5 h-5" />
          </button>
        </div>
        <ConversationList
          selectedConvId={selectedConvId}
          onSelect={handleSelectConversation}
          currentUserId={userId!}
        />
      </div>

      {/* Message thread (right panel) */}
      <div
        className={`flex-1 flex flex-col bg-gray-50 ${
          selectedConvId ? 'flex' : 'hidden sm:flex'
        }`}
      >
        {selectedConvId ? (
          <MessageThread
            conversationId={selectedConvId}
            currentUserId={userId!}
            onBack={() => {
              setSelectedConvId(null);
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

      {/* New conversation modal */}
      {showNewConversation && (
        <NewConversationModal
          currentUserId={userId!}
          onClose={() => setShowNewConversation(false)}
          onCreated={(convId) => {
            setShowNewConversation(false);
            handleSelectConversation(convId);
          }}
        />
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Helpers                                                             */
/* ------------------------------------------------------------------ */

function getConversationDisplayName(conv: ConversationDto, currentUserId: string | number): string {
  if (conv.name) return conv.name;
  if (conv.type === 'DIRECT') {
    const other = conv.participants.find((p) => String(p.id) !== String(currentUserId));
    return other?.displayName || other?.username || 'Unknown';
  }
  return conv.participants
    .filter((p) => String(p.id) !== String(currentUserId))
    .map((p) => p.displayName || p.username)
    .join(', ');
}

function getConversationAvatar(conv: ConversationDto, currentUserId: string | number, size: 'sm' | 'md' = 'md') {
  const dim = size === 'sm' ? 'w-8 h-8' : 'w-10 h-10';
  const textSize = size === 'sm' ? 'text-xs' : 'text-sm';

  if (conv.type === 'DIRECT') {
    const other = conv.participants.find((p) => String(p.id) !== String(currentUserId));
    if (other?.avatarUrl) {
      return <img src={other.avatarUrl} alt="" className={`${dim} rounded-full object-cover shrink-0`} />;
    }
    const initial = other?.displayName?.[0]?.toUpperCase() ?? other?.username?.[0]?.toUpperCase() ?? '?';
    return (
      <div className={`${dim} bg-primary-500 text-white rounded-full flex items-center justify-center ${textSize} font-semibold shrink-0`}>
        {initial}
      </div>
    );
  }
  const others = conv.participants.filter((p) => String(p.id) !== String(currentUserId));
  const first = others[0];
  const second = others[1];
  return (
    <div className={`relative ${dim} shrink-0`}>
      <div className="absolute top-0 left-0 w-7 h-7 bg-primary-500 text-white rounded-full flex items-center justify-center text-[10px] font-semibold border-2 border-white z-10">
        {first?.displayName?.[0]?.toUpperCase() ?? '?'}
      </div>
      <div className="absolute bottom-0 right-0 w-7 h-7 bg-primary-400 text-white rounded-full flex items-center justify-center text-[10px] font-semibold border-2 border-white">
        {second ? second.displayName?.[0]?.toUpperCase() ?? '?' : `+${others.length}`}
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Conversation List                                                   */
/* ------------------------------------------------------------------ */

function ConversationList({
  selectedConvId,
  onSelect,
  currentUserId,
}: {
  selectedConvId: string | null;
  onSelect: (id: number) => void;
  currentUserId: string | number;
}) {
  const { data: conversations, isLoading } = useQuery<ConversationDto[]>({
    queryKey: ['conversations'],
    queryFn: async () => {
      const { data } = await api.get('/conversations');
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
        const isActive = String(conv.id) === selectedConvId;
        const displayName = getConversationDisplayName(conv, currentUserId);
        return (
          <button
            key={conv.id}
            onClick={() => onSelect(conv.id)}
            className={`w-full flex items-center gap-3 p-3 text-left transition-colors ${
              isActive ? 'bg-primary-50' : 'hover:bg-gray-50'
            }`}
          >
            {getConversationAvatar(conv, currentUserId)}
            <div className="flex-1 min-w-0">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-1.5 min-w-0">
                  <span className="text-sm font-semibold text-gray-900 truncate">
                    {displayName}
                  </span>
                  {conv.type === 'GROUP' && (
                    <span className="text-[9px] text-gray-400 bg-gray-100 px-1.5 py-0.5 rounded shrink-0">
                      {conv.participants.length}
                    </span>
                  )}
                </div>
                {conv.lastMessage && (
                  <span className="text-[10px] text-gray-400 shrink-0 ml-2">
                    {formatRelativeTime(conv.lastMessage.createdAt)}
                  </span>
                )}
              </div>
              <div className="flex items-center justify-between">
                <p className="text-xs text-gray-500 truncate">
                  {conv.type === 'GROUP' && conv.lastMessage
                    ? `${conv.lastMessage.sender.displayName || conv.lastMessage.sender.username}: ${conv.lastMessage.content}`
                    : conv.lastMessage?.content ?? 'No messages yet'}
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
  conversationId,
  currentUserId,
  onBack,
}: {
  conversationId: string | number;
  currentUserId: string | number;
  onBack: () => void;
}) {
  const [messageText, setMessageText] = useState('');
  const [files, setFiles] = useState<File[]>([]);
  const [showInfo, setShowInfo] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const queryClient = useQueryClient();

  const { data: conversation } = useQuery<ConversationDto>({
    queryKey: ['conversation', conversationId],
    queryFn: async () => {
      const { data } = await api.get(`/conversations/${conversationId}`);
      return data;
    },
    staleTime: 10000,
  });

  const { data: messages, isLoading } = useQuery<MessageDto[]>({
    queryKey: ['messages', conversationId],
    queryFn: async () => {
      const { data } = await api.get(`/conversations/${conversationId}/messages`);
      return [...data].reverse();
    },
    refetchInterval: 3000,
  });

  useEffect(() => {
    api.post(`/conversations/${conversationId}/read`).catch(() => {});
    queryClient.invalidateQueries({ queryKey: ['conversations'] });
    queryClient.invalidateQueries({ queryKey: ['unread-count'] });
  }, [conversationId, queryClient]);

  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, []);

  useEffect(() => {
    scrollToBottom();
  }, [messages, scrollToBottom]);

  const isGroup = conversation?.type === 'GROUP';
  const displayName = conversation
    ? getConversationDisplayName(conversation, currentUserId)
    : 'Loading...';

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

      return api.post(`/conversations/${conversationId}/messages`, {
        content: currentText || '',
        attachmentIds: attachmentIds.length ? attachmentIds : undefined,
      });
    },
    onSuccess: () => {
      setMessageText('');
      setFiles([]);
      queryClient.invalidateQueries({ queryKey: ['messages', conversationId] });
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
        {conversation && getConversationAvatar(conversation, currentUserId, 'sm')}
        <div className="flex-1 min-w-0">
          <span className="text-sm font-semibold text-gray-900 truncate block">
            {displayName}
          </span>
          {conversation && conversation.participants.length > 2 && (
            <span className="text-[10px] text-gray-400">
              {conversation.participants.length} members
            </span>
          )}
        </div>
        {/* Add people / info button - always visible */}
        <button
          onClick={() => setShowInfo(!showInfo)}
          className={`p-1.5 rounded-lg transition-colors ${
            showInfo ? 'text-primary-500 bg-primary-50' : 'text-gray-400 hover:text-primary-500 hover:bg-gray-50'
          }`}
          title="Add people & conversation info"
        >
          <UsersIcon className="w-5 h-5" />
        </button>
      </div>

      {/* Info / add people panel */}
      {showInfo && conversation && (
        <ConversationInfoPanel
          conversation={conversation}
          currentUserId={currentUserId}
          onClose={() => setShowInfo(false)}
        />
      )}

      {/* AI Assistant */}
      <div className="px-4 pt-3">
        <AiAssistant context="conversation" contextId={conversationId} />
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto p-4 space-y-3">
        {isLoading ? (
          <div className="space-y-3">
            {[1, 2, 3].map((i) => (
              <div key={i} className={`flex ${i % 2 === 0 ? 'justify-end' : 'justify-start'}`}>
                <div className="skeleton h-10 w-48 rounded-2xl" />
              </div>
            ))}
          </div>
        ) : messages && messages.length > 0 ? (
          messages.map((msg) => {
            const isSent = String(msg.sender.id) === String(currentUserId);
            return (
              <div key={msg.id} className={`flex ${isSent ? 'justify-end' : 'justify-start'}`}>
                <div className="flex items-end gap-2 max-w-[75%]">
                  {!isSent && (
                    <div className="w-6 h-6 bg-primary-500 text-white rounded-full flex items-center justify-center text-[10px] font-semibold shrink-0">
                      {msg.sender.displayName?.[0]?.toUpperCase() ??
                        msg.sender.username?.[0]?.toUpperCase() ?? '?'}
                    </div>
                  )}
                  <div>
                    {isGroup && !isSent && (
                      <span className="text-[10px] text-gray-500 font-medium ml-1">
                        {msg.sender.displayName || msg.sender.username}
                      </span>
                    )}
                    <div
                      className={`px-3.5 py-2 rounded-2xl text-sm ${
                        isSent
                          ? 'bg-primary-500 text-white rounded-br-md'
                          : 'bg-gray-100 text-gray-800 rounded-bl-md'
                      }`}
                    >
                      {msg.content}
                    </div>
                    {msg.attachments?.length > 0 && (
                      <div className="mt-1 flex flex-wrap gap-1">
                        {msg.attachments.map((att) =>
                          att.mediaType?.startsWith('image/') ? (
                            <img key={att.id} src={att.fileUrl} alt="" className="w-32 h-24 object-cover rounded-lg" />
                          ) : (
                            <a key={att.id} href={att.fileUrl} target="_blank" rel="noreferrer" className="text-xs text-primary-500 underline">
                              Attachment
                            </a>
                          ),
                        )}
                      </div>
                    )}
                    <div className={`flex items-center gap-1 mt-0.5 ${isSent ? 'justify-end' : 'justify-start'}`}>
                      <span className="text-[10px] text-gray-400">
                        {formatRelativeTime(msg.createdAt)}
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            );
          })
        ) : (
          <div className="flex-1 flex items-center justify-center h-full">
            <p className="text-sm text-gray-400">No messages yet. Say hello!</p>
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
                <img src={URL.createObjectURL(f)} alt={f.name} className="w-14 h-14 object-cover rounded-lg border border-gray-200" />
              ) : (
                <div className="w-14 h-14 bg-gray-100 rounded-lg flex items-center justify-center border border-gray-200">
                  <span className="text-[10px] text-gray-500 truncate w-12 text-center">{f.name.slice(0, 10)}</span>
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

      {/* Input area */}
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
            if (newFiles.length > 0) setFiles((prev) => [...prev, ...newFiles]);
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
        <button type="submit" disabled={!canSend} className="btn-primary text-sm px-4">
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
/* Conversation Info Panel (available on all conversations)            */
/* ------------------------------------------------------------------ */

function ConversationInfoPanel({
  conversation,
  currentUserId,
  onClose,
}: {
  conversation: ConversationDto;
  currentUserId: string | number;
  onClose: () => void;
}) {
  const [editName, setEditName] = useState(conversation.name ?? '');
  const [isEditing, setIsEditing] = useState(false);
  const [addSearch, setAddSearch] = useState('');
  const [shareHistory, setShareHistory] = useState(true);
  const queryClient = useQueryClient();

  const renameMutation = useMutation({
    mutationFn: (name: string) => api.put(`/conversations/${conversation.id}`, { name }),
    onSuccess: () => {
      setIsEditing(false);
      queryClient.invalidateQueries({ queryKey: ['conversation', String(conversation.id)] });
      queryClient.invalidateQueries({ queryKey: ['conversations'] });
    },
  });

  const { data: searchResults } = useQuery<AuthorDto[]>({
    queryKey: ['user-search-add', addSearch],
    queryFn: async () => {
      if (!addSearch.trim()) return [];
      const { data } = await api.get(`/users/search?q=${encodeURIComponent(addSearch)}&limit=10`);
      return data;
    },
    enabled: addSearch.trim().length > 0,
  });

  const addMutation = useMutation({
    mutationFn: (userId: number) =>
      api.post(`/conversations/${conversation.id}/participants`, { userId, shareHistory }),
    onSuccess: () => {
      setAddSearch('');
      queryClient.invalidateQueries({ queryKey: ['conversation', String(conversation.id)] });
      queryClient.invalidateQueries({ queryKey: ['conversations'] });
    },
  });

  const removeMutation = useMutation({
    mutationFn: (userId: number) =>
      api.delete(`/conversations/${conversation.id}/participants/${userId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['conversation', String(conversation.id)] });
      queryClient.invalidateQueries({ queryKey: ['conversations'] });
    },
  });

  const existingIds = new Set(conversation.participants.map((p) => p.id));
  const filteredResults = searchResults?.filter(
    (u) => String(u.id) !== String(currentUserId) && !existingIds.has(u.id),
  );

  return (
    <div className="bg-white border-b border-gray-100 p-4 space-y-4 max-h-[50vh] overflow-y-auto">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-gray-900">Conversation</h3>
        <button onClick={onClose} className="text-xs text-gray-400 hover:text-gray-600">
          Close
        </button>
      </div>

      {/* Name */}
      <div>
        <label className="text-[10px] uppercase tracking-wider text-gray-400 font-medium">Name</label>
        {isEditing ? (
          <div className="flex gap-2 mt-1">
            <input
              type="text"
              value={editName}
              onChange={(e) => setEditName(e.target.value)}
              className="input-field flex-1 text-sm"
              placeholder="Give this conversation a name..."
              autoFocus
            />
            <button
              onClick={() => renameMutation.mutate(editName)}
              className="btn-primary text-xs px-3"
              disabled={renameMutation.isPending}
            >
              Save
            </button>
            <button
              onClick={() => { setIsEditing(false); setEditName(conversation.name ?? ''); }}
              className="text-xs text-gray-400 hover:text-gray-600 px-2"
            >
              Cancel
            </button>
          </div>
        ) : (
          <div className="flex items-center gap-2 mt-1">
            <span className="text-sm text-gray-700">
              {conversation.name || <span className="text-gray-400 italic">None</span>}
            </span>
            <button
              onClick={() => setIsEditing(true)}
              className="text-[10px] text-primary-500 hover:underline"
            >
              {conversation.name ? 'Edit' : 'Add name'}
            </button>
          </div>
        )}
      </div>

      {/* Members */}
      <div>
        <label className="text-[10px] uppercase tracking-wider text-gray-400 font-medium">
          Members ({conversation.participants.length})
        </label>
        <div className="mt-1.5 space-y-1">
          {conversation.participants.map((p) => (
            <div key={p.id} className="flex items-center gap-2 py-1">
              {p.avatarUrl ? (
                <img src={p.avatarUrl} alt="" className="w-6 h-6 rounded-full object-cover" />
              ) : (
                <div className="w-6 h-6 bg-primary-500 text-white rounded-full flex items-center justify-center text-[10px] font-semibold">
                  {p.displayName?.[0]?.toUpperCase() ?? '?'}
                </div>
              )}
              <span className="text-sm text-gray-700 flex-1">
                {p.displayName || p.username}
                {String(p.id) === String(currentUserId) && (
                  <span className="text-[10px] text-gray-400 ml-1">(you)</span>
                )}
              </span>
              {String(p.id) !== String(currentUserId) && conversation.participants.length > 2 && (
                <button
                  onClick={() => removeMutation.mutate(p.id)}
                  className="text-[10px] text-red-400 hover:text-red-600"
                  title="Remove from conversation"
                >
                  Remove
                </button>
              )}
            </div>
          ))}
        </div>
      </div>

      {/* Add people */}
      <div>
        <label className="text-[10px] uppercase tracking-wider text-gray-400 font-medium">
          Add people
        </label>
        <input
          type="text"
          value={addSearch}
          onChange={(e) => setAddSearch(e.target.value)}
          placeholder="Search by name..."
          className="input-field w-full text-sm mt-1"
        />

        {/* History visibility toggle */}
        {addSearch.trim() && (
          <div className="mt-2 flex items-center gap-2">
            <label className="flex items-center gap-1.5 cursor-pointer">
              <input
                type="checkbox"
                checked={shareHistory}
                onChange={(e) => setShareHistory(e.target.checked)}
                className="w-3.5 h-3.5 rounded border-gray-300 text-primary-500 focus:ring-primary-500"
              />
              <span className="text-xs text-gray-600">
                Let them see full conversation history
              </span>
            </label>
          </div>
        )}

        {/* Search results */}
        {filteredResults && filteredResults.length > 0 && (
          <div className="mt-2 border border-gray-100 rounded-lg max-h-32 overflow-y-auto">
            {filteredResults.map((user) => (
              <button
                key={user.id}
                onClick={() => addMutation.mutate(user.id)}
                disabled={addMutation.isPending}
                className="w-full flex items-center gap-2 p-2 text-left hover:bg-gray-50 transition-colors"
              >
                {user.avatarUrl ? (
                  <img src={user.avatarUrl} alt="" className="w-6 h-6 rounded-full object-cover" />
                ) : (
                  <div className="w-6 h-6 bg-primary-500 text-white rounded-full flex items-center justify-center text-[10px] font-semibold">
                    {user.displayName?.[0]?.toUpperCase() ?? '?'}
                  </div>
                )}
                <span className="text-sm text-gray-700">{user.displayName || user.username}</span>
                <span className="ml-auto text-[10px] text-primary-500">Add</span>
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* New Conversation Modal (for pencil button)                          */
/* ------------------------------------------------------------------ */

function NewConversationModal({
  currentUserId,
  onClose,
  onCreated,
}: {
  currentUserId: string | number;
  onClose: () => void;
  onCreated: (conversationId: number) => void;
}) {
  const [search, setSearch] = useState('');
  const [selectedUsers, setSelectedUsers] = useState<AuthorDto[]>([]);
  const [conversationName, setConversationName] = useState('');

  const { data: searchResults } = useQuery<AuthorDto[]>({
    queryKey: ['user-search', search],
    queryFn: async () => {
      if (!search.trim()) return [];
      const { data } = await api.get(`/users/search?q=${encodeURIComponent(search)}&limit=10`);
      return data;
    },
    enabled: search.trim().length > 0,
  });

  const createMutation = useMutation({
    mutationFn: async () => {
      const { data } = await api.post('/conversations', {
        participantIds: selectedUsers.map((u) => u.id),
        name: conversationName.trim() || null,
      });
      return data;
    },
    onSuccess: (data) => {
      onCreated(data.id);
    },
  });

  const toggleUser = (user: AuthorDto) => {
    setSelectedUsers((prev) => {
      const exists = prev.find((u) => u.id === user.id);
      if (exists) return prev.filter((u) => u.id !== user.id);
      return [...prev, user];
    });
  };

  const isGroup = selectedUsers.length > 1;

  return (
    <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4" onClick={onClose}>
      <div
        className="bg-white rounded-xl shadow-xl w-full max-w-md max-h-[80vh] flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="p-4 border-b border-gray-100">
          <h3 className="text-lg font-bold text-gray-900">New Conversation</h3>
        </div>

        <div className="p-4 space-y-3 flex-1 overflow-y-auto">
          {selectedUsers.length > 0 && (
            <div className="flex flex-wrap gap-1.5">
              {selectedUsers.map((u) => (
                <span
                  key={u.id}
                  className="inline-flex items-center gap-1 bg-primary-50 text-primary-700 text-xs px-2 py-1 rounded-full"
                >
                  {u.displayName || u.username}
                  <button onClick={() => toggleUser(u)} className="text-primary-400 hover:text-primary-600">x</button>
                </span>
              ))}
            </div>
          )}

          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search users..."
            className="input-field w-full text-sm"
            autoFocus
          />

          {searchResults && searchResults.length > 0 && (
            <div className="border border-gray-100 rounded-lg max-h-40 overflow-y-auto">
              {searchResults
                .filter((u) => String(u.id) !== String(currentUserId))
                .map((user) => {
                  const isSelected = selectedUsers.some((u) => u.id === user.id);
                  return (
                    <button
                      key={user.id}
                      onClick={() => toggleUser(user)}
                      className={`w-full flex items-center gap-2 p-2 text-left hover:bg-gray-50 transition-colors ${
                        isSelected ? 'bg-primary-50' : ''
                      }`}
                    >
                      {user.avatarUrl ? (
                        <img src={user.avatarUrl} alt="" className="w-7 h-7 rounded-full object-cover" />
                      ) : (
                        <div className="w-7 h-7 bg-primary-500 text-white rounded-full flex items-center justify-center text-[10px] font-semibold">
                          {user.displayName?.[0]?.toUpperCase() ?? '?'}
                        </div>
                      )}
                      <span className="text-sm text-gray-700">{user.displayName || user.username}</span>
                      {isSelected && (
                        <svg className="w-4 h-4 text-primary-500 ml-auto" fill="currentColor" viewBox="0 0 20 20">
                          <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                        </svg>
                      )}
                    </button>
                  );
                })}
            </div>
          )}

          {isGroup && (
            <input
              type="text"
              value={conversationName}
              onChange={(e) => setConversationName(e.target.value)}
              placeholder="Group name (optional)"
              className="input-field w-full text-sm"
            />
          )}
        </div>

        <div className="p-4 border-t border-gray-100 flex justify-end gap-2">
          <button onClick={onClose} className="btn-secondary text-sm px-4">Cancel</button>
          <button
            onClick={() => createMutation.mutate()}
            disabled={selectedUsers.length === 0 || createMutation.isPending}
            className="btn-primary text-sm px-4"
          >
            {createMutation.isPending ? 'Creating...' : isGroup ? 'Create Group' : 'Start Chat'}
          </button>
        </div>
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Icons                                                               */
/* ------------------------------------------------------------------ */

function MailIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M21.75 6.75v10.5a2.25 2.25 0 01-2.25 2.25h-15a2.25 2.25 0 01-2.25-2.25V6.75m19.5 0A2.25 2.25 0 0019.5 4.5h-15a2.25 2.25 0 00-2.25 2.25m19.5 0v.243a2.25 2.25 0 01-1.07 1.916l-7.5 4.615a2.25 2.25 0 01-2.36 0L3.32 8.91a2.25 2.25 0 01-1.07-1.916V6.75" />
    </svg>
  );
}

function PenSquareIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M16.862 4.487l1.687-1.688a1.875 1.875 0 112.652 2.652L10.582 16.07a4.5 4.5 0 01-1.897 1.13L6 18l.8-2.685a4.5 4.5 0 011.13-1.897l8.932-8.931zm0 0L19.5 7.125M18 14v4.75A2.25 2.25 0 0115.75 21H5.25A2.25 2.25 0 013 18.75V8.25A2.25 2.25 0 015.25 6H10" />
    </svg>
  );
}

function UsersIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M18 18.72a9.094 9.094 0 003.741-.479 3 3 0 00-4.682-2.72m.94 3.198l.001.031c0 .225-.012.447-.037.666A11.944 11.944 0 0112 21c-2.17 0-4.207-.576-5.963-1.584A6.062 6.062 0 016 18.719m12 0a5.971 5.971 0 00-.941-3.197m0 0A5.995 5.995 0 0012 12.75a5.995 5.995 0 00-5.058 2.772m0 0a3 3 0 00-4.681 2.72 8.986 8.986 0 003.74.477m.94-3.197a5.971 5.971 0 00-.94 3.197M15 6.75a3 3 0 11-6 0 3 3 0 016 0zm6 3a2.25 2.25 0 11-4.5 0 2.25 2.25 0 014.5 0zm-13.5 0a2.25 2.25 0 11-4.5 0 2.25 2.25 0 014.5 0z" />
    </svg>
  );
}
