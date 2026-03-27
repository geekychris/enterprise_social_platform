import { useState, useRef } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import api from '../../api/client';
import type { UserDto } from '../../api/types';
import { useAuth } from '../../hooks/useAuth';
import FollowListModal from './FollowListModal';

interface Props {
  userId: number | string;
}

export default function UserProfile({ userId }: Props) {
  const { userId: currentUserId } = useAuth();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const isOwnProfile = String(currentUserId) === String(userId);
  const [showList, setShowList] = useState<'followers' | 'following' | null>(null);
  const [editing, setEditing] = useState(false);

  const { data: user, isLoading } = useQuery<UserDto>({
    queryKey: ['user', userId],
    queryFn: async () => {
      const { data } = await api.get(`/users/${userId}`);
      return data;
    },
  });

  // Friend / follow status
  const { data: friendStatus } = useQuery<{ status: string; requestId?: number }>({
    queryKey: ['friend-status', userId],
    queryFn: () => api.get(`/friend-requests/status/${userId}`).then(r => r.data),
    enabled: !isOwnProfile,
  });

  const invalidateRelationship = () => {
    queryClient.invalidateQueries({ queryKey: ['user', userId] });
    queryClient.invalidateQueries({ queryKey: ['friend-status', userId] });
    queryClient.removeQueries({ queryKey: ['following', currentUserId] });
    queryClient.invalidateQueries({ queryKey: ['following'] });
  };

  const follow = useMutation({
    mutationFn: () => api.post(`/follow/${userId}`),
    onSuccess: invalidateRelationship,
  });

  const unfollow = useMutation({
    mutationFn: () => api.delete(`/follow/${userId}`),
    onSuccess: invalidateRelationship,
  });

  const sendFriendRequest = useMutation({
    mutationFn: () => api.post(`/friend-requests/${userId}`),
    onSuccess: () => queryClient.refetchQueries({ queryKey: ['friend-status', userId] }),
  });

  const acceptFriendRequest = useMutation({
    mutationFn: () => api.post(`/friend-requests/${friendStatus?.requestId}/accept`),
    onSuccess: invalidateRelationship,
  });

  const rejectFriendRequest = useMutation({
    mutationFn: () => api.post(`/friend-requests/${friendStatus?.requestId}/reject`),
    onSuccess: () => queryClient.refetchQueries({ queryKey: ['friend-status', userId] }),
  });

  if (isLoading) {
    return (
      <div className="card overflow-hidden">
        <div className="skeleton h-32 rounded-none" />
        <div className="p-4 flex items-end gap-4 -mt-8">
          <div className="skeleton w-24 h-24 rounded-full border-4 border-white" />
          <div className="space-y-2 flex-1 pb-1">
            <div className="skeleton h-5 w-40" />
            <div className="skeleton h-3 w-24" />
          </div>
        </div>
      </div>
    );
  }

  if (!user) {
    return (
      <div className="card p-8 text-center text-gray-400">User not found</div>
    );
  }

  if (editing && isOwnProfile) {
    return (
      <>
        <ProfileEditor user={user} onClose={() => setEditing(false)} />
        {showList && (
          <FollowListModal userId={userId} type={showList} onClose={() => setShowList(null)} />
        )}
      </>
    );
  }

  return (
    <>
      <div className="card overflow-hidden">
        {/* Cover */}
        {user.coverUrl ? (
          <div className="h-40 bg-gray-200">
            <img src={user.coverUrl} alt="" className="w-full h-full object-cover" />
          </div>
        ) : (
          <div className="h-40 bg-gradient-to-r from-primary-500 to-primary-600" />
        )}

        {/* Avatar - overlaps cover */}
        <div className="px-4 -mt-12">
          {user.avatarUrl ? (
            <img
              src={user.avatarUrl}
              alt=""
              className="w-24 h-24 rounded-full object-cover border-4 border-white shadow"
            />
          ) : (
            <div className="w-24 h-24 bg-primary-500 text-white rounded-full flex items-center justify-center text-3xl font-bold border-4 border-white shadow">
              {user.displayName?.[0]?.toUpperCase() ?? '?'}
            </div>
          )}
        </div>

        {/* Profile info - below cover */}
        <div className="px-4 pt-2 flex items-start gap-4 bg-white">
          <div className="flex-1">
            <div className="flex items-center gap-2">
              <h1 className="text-xl font-bold text-gray-900">{user.displayName}</h1>
              {user.pronouns && (
                <span className="text-xs text-gray-500">({user.pronouns})</span>
              )}
            </div>
            <p className="text-sm text-gray-500">@{user.username}</p>
            {user.jobTitle && (
              <p className="text-sm text-gray-600 mt-0.5">
                {user.jobTitle}
                {user.department && <span className="text-gray-400"> · {user.department}</span>}
              </p>
            )}
          </div>
          {isOwnProfile ? (
            <button
              onClick={() => setEditing(true)}
              className="text-sm border border-gray-300 text-gray-700 px-3 py-1.5 rounded-lg font-medium hover:bg-gray-50 transition-colors mb-1"
            >
              Edit Profile
            </button>
          ) : (
            <div className="pb-1 flex items-center gap-2">
              <button
                onClick={async () => {
                  try {
                    const { data } = await api.post(`/conversations/direct/${userId}`);
                    navigate(`/messages/${data.id}`);
                  } catch {
                    navigate('/messages');
                  }
                }}
                className="text-sm border border-gray-300 text-gray-700 px-3 py-1.5 rounded-lg font-medium hover:bg-gray-50 transition-colors flex items-center gap-1.5"
              >
                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
                </svg>
                Message
              </button>
              {friendStatus?.status === 'FRIENDS' ? (
                <span className="text-sm bg-green-50 text-green-700 border border-green-200 px-3 py-1.5 rounded-lg font-medium flex items-center gap-1.5">
                  <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                  </svg>
                  Friends
                </span>
              ) : friendStatus?.status === 'REQUEST_SENT' ? (
                <span className="text-sm bg-gray-100 text-gray-500 px-3 py-1.5 rounded-lg font-medium">Request Sent</span>
              ) : friendStatus?.status === 'REQUEST_RECEIVED' ? (
                <>
                  <button onClick={() => acceptFriendRequest.mutate()} disabled={acceptFriendRequest.isPending} className="btn-primary text-sm">Accept</button>
                  <button onClick={() => rejectFriendRequest.mutate()} disabled={rejectFriendRequest.isPending} className="text-sm border border-gray-300 text-gray-700 px-3 py-1.5 rounded-lg font-medium hover:bg-gray-50">Decline</button>
                </>
              ) : (
                <>
                  <button onClick={() => sendFriendRequest.mutate()} disabled={sendFriendRequest.isPending} className="btn-primary text-sm">
                    {sendFriendRequest.isPending ? 'Sending...' : 'Add Friend'}
                  </button>
                  <button onClick={() => follow.mutate()} disabled={follow.isPending} className="text-sm border border-gray-300 text-gray-700 px-3 py-1.5 rounded-lg font-medium hover:bg-gray-50">Follow</button>
                </>
              )}
            </div>
          )}
        </div>

        {/* Bio + details */}
        <div className="px-4 pb-4 space-y-3">
          {user.bio && <p className="text-sm text-gray-700">{user.bio}</p>}

          {/* Info pills */}
          <div className="flex flex-wrap gap-2 text-xs text-gray-500">
            {user.location && (
              <span className="flex items-center gap-1 bg-gray-50 px-2 py-1 rounded-md">
                <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" /><path strokeLinecap="round" strokeLinejoin="round" d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" /></svg>
                {user.location}
              </span>
            )}
            {user.joinedCompanyAt && (
              <span className="flex items-center gap-1 bg-gray-50 px-2 py-1 rounded-md">
                <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" /></svg>
                Joined {user.joinedCompanyAt}
              </span>
            )}
            {user.timezone && (
              <span className="flex items-center gap-1 bg-gray-50 px-2 py-1 rounded-md">
                <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>
                {user.timezone}
              </span>
            )}
            {user.managerName && (
              <Link to={`/profile/${user.managerId}`} className="flex items-center gap-1 bg-gray-50 px-2 py-1 rounded-md hover:bg-gray-100">
                <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" /></svg>
                Reports to {user.managerName}
              </Link>
            )}
            {user.phone && (
              <span className="flex items-center gap-1 bg-gray-50 px-2 py-1 rounded-md">
                <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M3 5a2 2 0 012-2h3.28a1 1 0 01.948.684l1.498 4.493a1 1 0 01-.502 1.21l-2.257 1.13a11.042 11.042 0 005.516 5.516l1.13-2.257a1 1 0 011.21-.502l4.493 1.498a1 1 0 01.684.949V19a2 2 0 01-2 2h-1C9.716 21 3 14.284 3 6V5z" /></svg>
                {user.phone}
              </span>
            )}
          </div>

          {/* Skills */}
          {user.skills && (
            <div>
              <div className="text-xs font-semibold text-gray-400 uppercase mb-1">Skills</div>
              <div className="flex flex-wrap gap-1.5">
                {user.skills.split(',').map((s, i) => (
                  <span key={i} className="px-2 py-0.5 bg-primary-50 text-primary-700 rounded-full text-xs">{s.trim()}</span>
                ))}
              </div>
            </div>
          )}

          {/* Interests */}
          {user.interests && (
            <div>
              <div className="text-xs font-semibold text-gray-400 uppercase mb-1">Interests</div>
              <div className="flex flex-wrap gap-1.5">
                {user.interests.split(',').map((s, i) => (
                  <span key={i} className="px-2 py-0.5 bg-gray-100 text-gray-600 rounded-full text-xs">{s.trim()}</span>
                ))}
              </div>
            </div>
          )}

          {/* Stats */}
          <div className="flex gap-6 text-sm pt-1">
            <button onClick={() => setShowList('followers')} className="hover:underline">
              <span className="font-semibold text-gray-900">{user.followerCount}</span>{' '}
              <span className="text-gray-500">followers</span>
            </button>
            <button onClick={() => setShowList('following')} className="hover:underline">
              <span className="font-semibold text-gray-900">{user.followingCount}</span>{' '}
              <span className="text-gray-500">following</span>
            </button>
          </div>
        </div>
      </div>

      {showList && (
        <FollowListModal userId={userId} type={showList} onClose={() => setShowList(null)} />
      )}
    </>
  );
}

/* ────────────────────────── Profile Editor ────────────────────────── */

function ProfileEditor({ user, onClose }: { user: UserDto; onClose: () => void }) {
  const queryClient = useQueryClient();
  const avatarInputRef = useRef<HTMLInputElement>(null);
  const coverInputRef = useRef<HTMLInputElement>(null);

  const [form, setForm] = useState({
    displayName: user.displayName || '',
    bio: user.bio || '',
    avatarUrl: user.avatarUrl || '',
    coverUrl: user.coverUrl || '',
    phone: user.phone || '',
    location: user.location || '',
    jobTitle: user.jobTitle || '',
    department: user.department || '',
    joinedCompanyAt: user.joinedCompanyAt || '',
    interests: user.interests || '',
    skills: user.skills || '',
    linkedinUrl: user.linkedinUrl || '',
    timezone: user.timezone || '',
    pronouns: user.pronouns || '',
    managerId: user.managerId ? String(user.managerId) : '',
  });

  const [uploading, setUploading] = useState(false);

  const uploadImage = async (file: File): Promise<string> => {
    const fd = new FormData();
    fd.append('file', file);
    const { data } = await api.post('/attachments/upload', fd);
    return data.fileUrl;
  };

  const handleAvatarUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setUploading(true);
    try {
      const url = await uploadImage(file);
      setForm(f => ({ ...f, avatarUrl: url }));
    } catch { /* ignore */ }
    setUploading(false);
    e.target.value = '';
  };

  const handleCoverUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setUploading(true);
    try {
      const url = await uploadImage(file);
      setForm(f => ({ ...f, coverUrl: url }));
    } catch { /* ignore */ }
    setUploading(false);
    e.target.value = '';
  };

  const save = useMutation({
    mutationFn: () => api.put('/users/me/profile', {
      ...form,
      managerId: form.managerId || null,
      joinedCompanyAt: form.joinedCompanyAt || null,
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['user', user.id] });
      queryClient.invalidateQueries({ queryKey: ['current-user-header'] });
      onClose();
    },
  });

  const set = (key: string) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) =>
    setForm(f => ({ ...f, [key]: e.target.value }));

  // Manager search
  const [mgrSearch, setMgrSearch] = useState('');
  const [mgrOpen, setMgrOpen] = useState(false);
  const { data: mgrResults } = useQuery<{ id: number; displayName: string; username: string }[]>({
    queryKey: ['mgr-search', mgrSearch],
    queryFn: () => api.get('/users/search', { params: { q: mgrSearch } }).then(r => r.data),
    enabled: mgrSearch.length >= 1 && mgrOpen,
  });

  return (
    <div className="card overflow-hidden">
      {/* Cover photo */}
      <div className="relative h-40 bg-gray-200 group cursor-pointer" onClick={() => coverInputRef.current?.click()}>
        {form.coverUrl ? (
          <img src={form.coverUrl} alt="" className="w-full h-full object-cover" />
        ) : (
          <div className="w-full h-full bg-gradient-to-r from-primary-500 to-primary-600" />
        )}
        <div className="absolute inset-0 bg-black/30 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
          <span className="text-white text-sm font-medium">Change Cover Photo</span>
        </div>
        <input ref={coverInputRef} type="file" accept="image/*" className="hidden" onChange={handleCoverUpload} />
      </div>

      {/* Avatar */}
      <div className="px-4 -mt-12 mb-4">
        <div className="relative w-24 h-24 group cursor-pointer" onClick={() => avatarInputRef.current?.click()}>
          {form.avatarUrl ? (
            <img src={form.avatarUrl} alt="" className="w-24 h-24 rounded-full object-cover border-4 border-white shadow" />
          ) : (
            <div className="w-24 h-24 bg-primary-500 text-white rounded-full flex items-center justify-center text-3xl font-bold border-4 border-white shadow">
              {form.displayName?.[0]?.toUpperCase() ?? '?'}
            </div>
          )}
          <div className="absolute inset-0 rounded-full bg-black/30 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
            <svg className="w-6 h-6 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M3 9a2 2 0 012-2h.93a2 2 0 001.664-.89l.812-1.22A2 2 0 0110.07 4h3.86a2 2 0 011.664.89l.812 1.22A2 2 0 0018.07 7H19a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V9z" />
              <path strokeLinecap="round" strokeLinejoin="round" d="M15 13a3 3 0 11-6 0 3 3 0 016 0z" />
            </svg>
          </div>
          <input ref={avatarInputRef} type="file" accept="image/*" className="hidden" onChange={handleAvatarUpload} />
        </div>
        {uploading && <div className="text-xs text-gray-400 mt-1">Uploading...</div>}
      </div>

      {/* Form */}
      <div className="px-4 pb-6 space-y-4">
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <Field label="Display Name" value={form.displayName} onChange={set('displayName')} />
          <Field label="Pronouns" value={form.pronouns} onChange={set('pronouns')} placeholder="e.g. she/her, he/him, they/them" />
        </div>

        <div>
          <label className="block text-xs font-medium text-gray-500 mb-1">Bio</label>
          <textarea value={form.bio} onChange={set('bio')} rows={3} className="input-field resize-none" placeholder="Tell people about yourself..." />
        </div>

        <div className="border-t border-gray-100 pt-4">
          <h3 className="text-sm font-semibold text-gray-700 mb-3">Work</h3>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Field label="Job Title" value={form.jobTitle} onChange={set('jobTitle')} placeholder="e.g. Senior Engineer" />
            <Field label="Department" value={form.department} onChange={set('department')} placeholder="e.g. Engineering" />
            <Field label="Joined Company" value={form.joinedCompanyAt} onChange={set('joinedCompanyAt')} type="date" />
            <div>
              <label className="block text-xs font-medium text-gray-500 mb-1">Reports To</label>
              <div className="relative">
                <input
                  value={mgrOpen ? mgrSearch : (user.managerName || (form.managerId ? `ID: ${form.managerId}` : ''))}
                  onChange={e => { setMgrSearch(e.target.value); setMgrOpen(true); }}
                  onFocus={() => setMgrOpen(true)}
                  onBlur={() => setTimeout(() => setMgrOpen(false), 200)}
                  placeholder="Search for manager..."
                  className="input-field"
                />
                {form.managerId && !mgrOpen && (
                  <button type="button" onClick={() => setForm(f => ({ ...f, managerId: '' }))}
                    className="absolute right-2 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600">
                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" /></svg>
                  </button>
                )}
                {mgrOpen && mgrResults && mgrResults.length > 0 && (
                  <div className="absolute top-full left-0 right-0 mt-1 bg-white border border-gray-200 rounded-lg shadow-lg z-50 max-h-48 overflow-y-auto">
                    {mgrResults.filter((u: any) => String(u.id) !== String(user.id)).slice(0, 8).map((u: any) => (
                      <button key={u.id} type="button"
                        onMouseDown={e => { e.preventDefault(); setForm(f => ({ ...f, managerId: String(u.id) })); setMgrSearch(u.displayName); setMgrOpen(false); }}
                        className="w-full flex items-center gap-2 px-3 py-2 hover:bg-gray-50 text-left text-sm">
                        <span className="font-medium">{u.displayName}</span>
                        <span className="text-xs text-gray-400">@{u.username}</span>
                      </button>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>

        <div className="border-t border-gray-100 pt-4">
          <h3 className="text-sm font-semibold text-gray-700 mb-3">Contact & Location</h3>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Field label="Location" value={form.location} onChange={set('location')} placeholder="e.g. San Francisco, CA" />
            <Field label="Phone" value={form.phone} onChange={set('phone')} placeholder="e.g. +1 555-0123" />
            <Field label="Timezone" value={form.timezone} onChange={set('timezone')} placeholder="e.g. America/Los_Angeles" />
            <Field label="LinkedIn" value={form.linkedinUrl} onChange={set('linkedinUrl')} placeholder="https://linkedin.com/in/..." />
          </div>
        </div>

        <div className="border-t border-gray-100 pt-4">
          <h3 className="text-sm font-semibold text-gray-700 mb-3">Skills & Interests</h3>
          <div className="space-y-4">
            <div>
              <label className="block text-xs font-medium text-gray-500 mb-1">Skills (comma separated)</label>
              <input value={form.skills} onChange={set('skills')} className="input-field" placeholder="e.g. React, Java, Machine Learning, Public Speaking" />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-500 mb-1">Interests (comma separated)</label>
              <input value={form.interests} onChange={set('interests')} className="input-field" placeholder="e.g. Photography, Hiking, Coffee, Open Source" />
            </div>
          </div>
        </div>

        {/* Actions */}
        <div className="flex justify-end gap-3 pt-2">
          <button onClick={onClose} className="btn-secondary text-sm">Cancel</button>
          <button onClick={() => save.mutate()} disabled={save.isPending} className="btn-primary text-sm">
            {save.isPending ? 'Saving...' : 'Save Profile'}
          </button>
        </div>
        {save.isError && (
          <div className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded-lg">Failed to save. Please try again.</div>
        )}
      </div>
    </div>
  );
}

function Field({ label, value, onChange, placeholder, type = 'text' }: {
  label: string; value: string; onChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
  placeholder?: string; type?: string;
}) {
  return (
    <div>
      <label className="block text-xs font-medium text-gray-500 mb-1">{label}</label>
      <input type={type} value={value} onChange={onChange} placeholder={placeholder} className="input-field" />
    </div>
  );
}
