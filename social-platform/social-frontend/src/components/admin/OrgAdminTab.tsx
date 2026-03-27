import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import api from '../../api/client';

interface OrgUnit {
  id: number; name: string; type: string; parentId: number | null;
  headUserId: number | null; headUserName: string | null;
  description: string | null; costCenter: string | null;
  childCount: number; memberCount: number;
}

interface OrgAssignment {
  id: number; userId: number; userName: string; userAvatarUrl: string | null;
  orgUnitId: number; orgUnitName: string; title: string;
  relationshipType: string; reportsToUserId: number | null;
  reportsToUserName: string | null; level: string;
}

export default function OrgAdminTab() {
  const [selectedUnit, setSelectedUnit] = useState<OrgUnit | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [showAssign, setShowAssign] = useState(false);
  const queryClient = useQueryClient();

  const { data: allUnits } = useQuery<OrgUnit[]>({
    queryKey: ['org-all'],
    queryFn: async () => { const { data } = await api.get('/org/units?all=true'); return data; },
  });

  const { data: roots } = useQuery<OrgUnit[]>({
    queryKey: ['org-roots'],
    queryFn: async () => { const { data } = await api.get('/org/units'); return data; },
  });

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-bold">Organization Management</h2>
        <button onClick={() => { setShowCreate(true); setSelectedUnit(null); }} className="btn-primary text-sm px-3 py-1.5">
          + New Unit
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Tree */}
        <div className="card p-4 max-h-[70vh] overflow-y-auto">
          <h3 className="text-sm font-semibold text-gray-500 mb-2">Org Tree</h3>
          {roots?.map((unit) => (
            <AdminOrgNode
              key={unit.id}
              unit={unit}
              depth={0}
              selectedId={selectedUnit?.id}
              onSelect={setSelectedUnit}
            />
          ))}
        </div>

        {/* Detail / Edit panel */}
        <div className="card p-4 max-h-[70vh] overflow-y-auto">
          {showCreate ? (
            <CreateUnitForm
              parentId={selectedUnit?.id ?? null}
              allUnits={allUnits ?? []}
              onDone={() => {
                setShowCreate(false);
                queryClient.invalidateQueries({ queryKey: ['org'] });
                queryClient.invalidateQueries({ queryKey: ['org-roots'] });
                queryClient.invalidateQueries({ queryKey: ['org-all'] });
              }}
              onCancel={() => setShowCreate(false)}
            />
          ) : selectedUnit ? (
            <UnitDetail
              unit={selectedUnit}
              allUnits={allUnits ?? []}
              onUpdated={() => {
                queryClient.invalidateQueries({ queryKey: ['org'] });
                queryClient.invalidateQueries({ queryKey: ['org-roots'] });
                queryClient.invalidateQueries({ queryKey: ['org-all'] });
              }}
              onCreateChild={() => setShowCreate(true)}
              onShowAssign={() => setShowAssign(true)}
            />
          ) : (
            <p className="text-sm text-gray-400 text-center py-8">Select a unit from the tree</p>
          )}

          {showAssign && selectedUnit && (
            <AssignUserForm
              orgUnitId={selectedUnit.id}
              onDone={() => {
                setShowAssign(false);
                queryClient.invalidateQueries({ queryKey: ['org-unit-members', selectedUnit.id] });
              }}
              onCancel={() => setShowAssign(false)}
            />
          )}
        </div>
      </div>
    </div>
  );
}

function AdminOrgNode({ unit, depth, selectedId, onSelect }: {
  unit: OrgUnit; depth: number; selectedId?: number; onSelect: (u: OrgUnit) => void;
}) {
  const [expanded, setExpanded] = useState(depth < 2);
  const { data: children } = useQuery<OrgUnit[]>({
    queryKey: ['org-children', unit.id],
    queryFn: async () => { const { data } = await api.get(`/org/units/${unit.id}/children`); return data; },
    enabled: expanded && unit.childCount > 0,
  });

  const isSelected = selectedId === unit.id;
  const typeColor: Record<string, string> = {
    COMPANY: 'text-purple-600', DIVISION: 'text-blue-600', DEPARTMENT: 'text-green-600', TEAM: 'text-orange-600',
  };

  return (
    <div style={{ marginLeft: `${depth * 12}px` }}>
      <div className={`flex items-center gap-1 py-1 px-1.5 rounded cursor-pointer transition-colors ${isSelected ? 'bg-primary-50 ring-1 ring-primary-300' : 'hover:bg-gray-50'}`}>
        <button onClick={() => setExpanded(!expanded)} className="text-gray-400 text-[10px] w-3">
          {unit.childCount > 0 ? (expanded ? '▼' : '▶') : '·'}
        </button>
        <button onClick={() => onSelect(unit)} className="flex items-center gap-1.5 flex-1 text-left min-w-0">
          <span className={`text-[9px] font-bold ${typeColor[unit.type] || 'text-gray-500'}`}>{unit.type.slice(0, 4)}</span>
          <span className="text-xs font-medium text-gray-800 truncate">{unit.name}</span>
          <span className="text-[9px] text-gray-400 ml-auto shrink-0">{unit.memberCount}</span>
        </button>
      </div>
      {expanded && children?.map((c) => (
        <AdminOrgNode key={c.id} unit={c} depth={depth + 1} selectedId={selectedId} onSelect={onSelect} />
      ))}
    </div>
  );
}

function UnitDetail({ unit, allUnits, onUpdated, onCreateChild, onShowAssign }: {
  unit: OrgUnit; allUnits: OrgUnit[];
  onUpdated: () => void; onCreateChild: () => void; onShowAssign: () => void;
}) {
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(unit.name);
  const [type, setType] = useState(unit.type);
  const [description, setDescription] = useState(unit.description ?? '');
  const queryClient = useQueryClient();

  const { data: members } = useQuery<OrgAssignment[]>({
    queryKey: ['org-unit-members', unit.id],
    queryFn: async () => { const { data } = await api.get(`/org/units/${unit.id}/members`); return data; },
  });

  const updateMutation = useMutation({
    mutationFn: () => api.put(`/org/units/${unit.id}`, { name, type, description: description || null, parentId: unit.parentId, headUserId: unit.headUserId }),
    onSuccess: () => { setEditing(false); onUpdated(); },
  });

  const deleteMutation = useMutation({
    mutationFn: () => api.delete(`/org/units/${unit.id}`),
    onSuccess: () => onUpdated(),
  });

  const removeAssignment = useMutation({
    mutationFn: ({ userId, orgUnitId }: { userId: number; orgUnitId: number }) =>
      api.delete(`/org/assignments/${userId}/${orgUnitId}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['org-unit-members', unit.id] }),
  });

  // Reset form when unit changes
  if (name !== unit.name && !editing) {
    setName(unit.name); setType(unit.type); setDescription(unit.description ?? '');
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-bold text-gray-900">{unit.name}</h3>
        <div className="flex gap-1">
          <button onClick={() => setEditing(!editing)} className="text-xs text-primary-600 hover:underline">
            {editing ? 'Cancel' : 'Edit'}
          </button>
          <button onClick={onCreateChild} className="text-xs text-green-600 hover:underline ml-2">+ Child</button>
          {unit.memberCount === 0 && unit.childCount === 0 && (
            <button onClick={() => { if (confirm('Delete this unit?')) deleteMutation.mutate(); }} className="text-xs text-red-500 hover:underline ml-2">Delete</button>
          )}
        </div>
      </div>

      {editing && (
        <div className="space-y-2 p-3 bg-gray-50 rounded-lg">
          <input value={name} onChange={(e) => setName(e.target.value)} className="input-field w-full text-sm" placeholder="Name" />
          <select value={type} onChange={(e) => setType(e.target.value)} className="input-field w-full text-sm">
            <option value="COMPANY">Company</option>
            <option value="DIVISION">Division</option>
            <option value="DEPARTMENT">Department</option>
            <option value="TEAM">Team</option>
          </select>
          <textarea value={description} onChange={(e) => setDescription(e.target.value)} className="input-field w-full text-sm" placeholder="Description" rows={2} />
          <button onClick={() => updateMutation.mutate()} disabled={updateMutation.isPending} className="btn-primary text-xs px-3 py-1">
            Save
          </button>
        </div>
      )}

      <div className="text-xs text-gray-500 space-y-1">
        <div>Type: <span className="font-medium">{unit.type}</span></div>
        {unit.headUserName && <div>Head: <span className="font-medium">{unit.headUserName}</span></div>}
        {unit.description && <div>Description: {unit.description}</div>}
        {unit.costCenter && <div>Cost Center: {unit.costCenter}</div>}
      </div>

      {/* Members */}
      <div>
        <div className="flex items-center justify-between mb-2">
          <h4 className="text-xs font-semibold text-gray-500">Members ({members?.length ?? 0})</h4>
          <button onClick={onShowAssign} className="text-[10px] text-primary-600 hover:underline">+ Assign User</button>
        </div>
        <div className="space-y-1 max-h-60 overflow-y-auto">
          {members?.map((m) => (
            <div key={m.id} className="flex items-center gap-2 py-1 px-2 rounded hover:bg-gray-50">
              {m.userAvatarUrl ? (
                <img src={m.userAvatarUrl} alt="" className="w-6 h-6 rounded-full object-cover" />
              ) : (
                <div className="w-6 h-6 rounded-full bg-primary-500 text-white flex items-center justify-center text-[9px] font-bold">
                  {m.userName[0]?.toUpperCase()}
                </div>
              )}
              <div className="flex-1 min-w-0">
                <span className="text-xs font-medium text-gray-800">{m.userName}</span>
                <span className="text-[10px] text-gray-500 ml-1">{m.title}</span>
              </div>
              <span className="text-[9px] bg-gray-100 text-gray-600 px-1.5 py-0.5 rounded">{m.level}</span>
              {m.relationshipType === 'DOTTED' && (
                <span className="text-[9px] bg-yellow-100 text-yellow-700 px-1.5 py-0.5 rounded">dotted</span>
              )}
              <button onClick={() => removeAssignment.mutate({ userId: m.userId, orgUnitId: unit.id })} className="text-[9px] text-red-400 hover:text-red-600">×</button>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function CreateUnitForm({ parentId, allUnits, onDone, onCancel }: {
  parentId: number | null; allUnits: OrgUnit[]; onDone: () => void; onCancel: () => void;
}) {
  const [name, setName] = useState('');
  const [type, setType] = useState('TEAM');
  const [parent, setParent] = useState<string>(parentId ? String(parentId) : '');
  const [description, setDescription] = useState('');

  const mutation = useMutation({
    mutationFn: () => api.post('/org/units', {
      name, type, parentId: parent ? Number(parent) : null, description: description || null,
    }),
    onSuccess: () => onDone(),
  });

  return (
    <div className="space-y-3">
      <h3 className="text-sm font-bold">Create Org Unit</h3>
      <input value={name} onChange={(e) => setName(e.target.value)} className="input-field w-full text-sm" placeholder="Unit name" />
      <select value={type} onChange={(e) => setType(e.target.value)} className="input-field w-full text-sm">
        <option value="COMPANY">Company</option>
        <option value="DIVISION">Division</option>
        <option value="DEPARTMENT">Department</option>
        <option value="TEAM">Team</option>
      </select>
      <select value={parent} onChange={(e) => setParent(e.target.value)} className="input-field w-full text-sm">
        <option value="">No parent (root)</option>
        {allUnits.map((u) => (
          <option key={u.id} value={u.id}>{u.type}: {u.name}</option>
        ))}
      </select>
      <textarea value={description} onChange={(e) => setDescription(e.target.value)} className="input-field w-full text-sm" placeholder="Description" rows={2} />
      <div className="flex gap-2">
        <button onClick={() => mutation.mutate()} disabled={!name.trim() || mutation.isPending} className="btn-primary text-xs px-3 py-1">Create</button>
        <button onClick={onCancel} className="text-xs text-gray-500 hover:underline">Cancel</button>
      </div>
    </div>
  );
}

function AssignUserForm({ orgUnitId, onDone, onCancel }: {
  orgUnitId: number; onDone: () => void; onCancel: () => void;
}) {
  const [search, setSearch] = useState('');
  const [selectedUser, setSelectedUser] = useState<{ id: number; displayName: string } | null>(null);
  const [title, setTitle] = useState('');
  const [level, setLevel] = useState('MID');
  const [relType, setRelType] = useState('SOLID');
  const [reportsTo, setReportsTo] = useState('');

  const { data: searchResults } = useQuery<any[]>({
    queryKey: ['user-search-assign', search],
    queryFn: async () => { const { data } = await api.get(`/users/search?q=${encodeURIComponent(search)}`); return data; },
    enabled: search.length > 1,
  });

  const mutation = useMutation({
    mutationFn: () => api.post('/org/assignments', {
      userId: selectedUser!.id, orgUnitId, title, relationshipType: relType, level,
      reportsToUserId: reportsTo ? Number(reportsTo) : null,
    }),
    onSuccess: () => onDone(),
  });

  return (
    <div className="mt-4 p-3 bg-blue-50 rounded-lg space-y-2">
      <h4 className="text-xs font-bold text-blue-800">Assign User</h4>
      {!selectedUser ? (
        <>
          <input value={search} onChange={(e) => setSearch(e.target.value)} className="input-field w-full text-sm" placeholder="Search user..." />
          {searchResults?.slice(0, 5).map((u: any) => (
            <button key={u.id} onClick={() => { setSelectedUser(u); setSearch(''); }}
              className="w-full text-left text-xs p-1.5 hover:bg-blue-100 rounded flex items-center gap-2">
              <span className="font-medium">{u.displayName}</span>
              <span className="text-gray-500">@{u.username}</span>
            </button>
          ))}
        </>
      ) : (
        <>
          <div className="text-xs">Assigning: <strong>{selectedUser.displayName}</strong>
            <button onClick={() => setSelectedUser(null)} className="text-red-500 ml-2">change</button>
          </div>
          <input value={title} onChange={(e) => setTitle(e.target.value)} className="input-field w-full text-sm" placeholder="Title (e.g. Software Engineer)" />
          <select value={level} onChange={(e) => setLevel(e.target.value)} className="input-field w-full text-sm">
            {['CEO', 'C_SUITE', 'SVP', 'VP', 'DIRECTOR', 'SENIOR_MANAGER', 'MANAGER', 'LEAD', 'SENIOR', 'MID', 'JUNIOR'].map((l) => (
              <option key={l} value={l}>{l}</option>
            ))}
          </select>
          <select value={relType} onChange={(e) => setRelType(e.target.value)} className="input-field w-full text-sm">
            <option value="SOLID">Solid Line</option>
            <option value="DOTTED">Dotted Line</option>
          </select>
          <div className="flex gap-2">
            <button onClick={() => mutation.mutate()} disabled={!title.trim() || mutation.isPending} className="btn-primary text-xs px-3 py-1">Assign</button>
            <button onClick={onCancel} className="text-xs text-gray-500 hover:underline">Cancel</button>
          </div>
        </>
      )}
    </div>
  );
}
