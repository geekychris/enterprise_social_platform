import { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import api from '../api/client';

interface TenantOption {
  id: number;
  name: string;
  slug: string;
  plan: string;
}

export default function LoginPage() {
  const [mode, setMode] = useState<'login' | 'register' | 'debug'>('login');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [bio, setBio] = useState('');
  const [debugUserId, setDebugUserId] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [tenants, setTenants] = useState<TenantOption[]>([]);
  const [selectedTenant, setSelectedTenant] = useState(localStorage.getItem('tenantId') || '1');
  const { login, loginDebug } = useAuth();
  const navigate = useNavigate();

  // Fetch available tenants
  useEffect(() => {
    api.get('/tenants/list')
      .then(({ data }) => {
        setTenants(data);
      })
      .catch(() => {
        setTenants([]);
      });
  }, []);

  // Store tenant selection
  useEffect(() => {
    localStorage.setItem('tenantId', selectedTenant);
  }, [selectedTenant]);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const { data } = await api.post('/auth/login', { username, password });
      login(data.token, data.userId, data.username, data.admin ?? false);
      navigate('/');
    } catch (err: any) {
      setError(err.response?.data?.message ?? 'Login failed');
    } finally {
      setLoading(false);
    }
  };

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await api.post('/auth/register', {
        username,
        displayName,
        email,
        password,
        bio,
      });
      // Auto-login after register
      const { data } = await api.post('/auth/login', { username, password });
      login(data.token, data.userId, data.username, data.admin ?? false);
      navigate('/');
    } catch (err: any) {
      setError(err.response?.data?.message ?? 'Registration failed');
    } finally {
      setLoading(false);
    }
  };

  const handleDebugLogin = (e: React.FormEvent) => {
    e.preventDefault();
    if (!debugUserId.trim()) {
      setError('Enter a valid user ID');
      return;
    }
    loginDebug(debugUserId.trim() as any);
    navigate('/');
  };

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center px-4">
      <div className="w-full max-w-md">
        {/* Logo */}
        <div className="text-center mb-8">
          <img src="/worksphere-logo.jpg" alt="WorkSphere" className="w-16 h-16 rounded-2xl object-cover mx-auto mb-4" />
          <h1 className="text-2xl font-bold text-gray-900">
            WorkSphere
          </h1>
          <p className="text-sm text-gray-500 mt-1">
            Connect with your team
          </p>
        </div>

        {/* Tenant selector */}
        {tenants.length > 1 && (
          <div className="mb-4 bg-white rounded-lg border border-gray-200 p-4">
            <label className="block text-xs font-medium text-gray-500 mb-2 uppercase tracking-wider">
              Organization
            </label>
            <div className="grid gap-2">
              {tenants.map((t) => (
                <button
                  key={t.id}
                  onClick={() => setSelectedTenant(String(t.id))}
                  className={`flex items-center gap-3 px-4 py-3 rounded-lg border text-left transition-all ${
                    selectedTenant === String(t.id)
                      ? 'border-primary-500 bg-primary-50 ring-1 ring-primary-500'
                      : 'border-gray-200 hover:border-gray-300 hover:bg-gray-50'
                  }`}
                >
                  <div className={`w-3 h-3 rounded-full ${selectedTenant === String(t.id) ? 'bg-primary-500' : 'bg-gray-300'}`} />
                  <div className="flex-1">
                    <div className="font-medium text-sm text-gray-900">{t.name}</div>
                    <div className="text-xs text-gray-400">{t.slug}.worksphere.com</div>
                  </div>
                  <span className={`text-xs px-2 py-0.5 rounded-full ${
                    t.plan === 'enterprise' ? 'bg-purple-100 text-purple-600' :
                    t.plan === 'pro' ? 'bg-blue-100 text-blue-600' :
                    'bg-gray-100 text-gray-500'
                  }`}>{t.plan}</span>
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Manual tenant ID input (when tenant list not available) */}
        {tenants.length <= 1 && (
          <div className="mb-4 bg-white rounded-lg border border-gray-200 p-3">
            <label className="block text-xs font-medium text-gray-500 mb-1">Tenant ID</label>
            <input
              value={selectedTenant}
              onChange={(e) => setSelectedTenant(e.target.value)}
              className="w-full border rounded px-3 py-1.5 text-sm font-mono"
              placeholder="1"
            />
          </div>
        )}

        {/* Mode tabs */}
        <div className="flex bg-white rounded-t-lg border border-b-0 border-gray-200">
          {(['login', 'register', 'debug'] as const).map((m) => (
            <button
              key={m}
              onClick={() => {
                setMode(m);
                setError('');
              }}
              className={`flex-1 py-3 text-sm font-medium transition-colors ${
                mode === m
                  ? 'text-primary-500 border-b-2 border-primary-500'
                  : 'text-gray-500 hover:text-gray-700'
              }`}
            >
              {m === 'debug' ? 'Debug Mode' : m.charAt(0).toUpperCase() + m.slice(1)}
            </button>
          ))}
        </div>

        {/* Form */}
        <div className="bg-white rounded-b-lg shadow-sm border border-gray-200 p-6">
          {error && (
            <div className="mb-4 p-3 bg-red-50 border border-red-200 text-red-700 rounded-lg text-sm">
              {error}
            </div>
          )}

          {mode === 'login' && (
            <form onSubmit={handleLogin} className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Username
                </label>
                <input
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  className="input-field"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Password
                </label>
                <input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="input-field"
                  required
                />
              </div>
              <button
                type="submit"
                disabled={loading}
                className="btn-primary w-full"
              >
                {loading ? 'Signing in...' : 'Sign In'}
              </button>
            </form>
          )}

          {mode === 'register' && (
            <form onSubmit={handleRegister} className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Username
                </label>
                <input
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  className="input-field"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Display Name
                </label>
                <input
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  className="input-field"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Email
                </label>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="input-field"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Password
                </label>
                <input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="input-field"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Bio (optional)
                </label>
                <textarea
                  value={bio}
                  onChange={(e) => setBio(e.target.value)}
                  className="input-field resize-none"
                  rows={2}
                />
              </div>
              <button
                type="submit"
                disabled={loading}
                className="btn-primary w-full"
              >
                {loading ? 'Creating account...' : 'Create Account'}
              </button>
            </form>
          )}

          {mode === 'debug' && (
            <form onSubmit={handleDebugLogin} className="space-y-4">
              <div className="p-3 bg-yellow-50 border border-yellow-200 text-yellow-800 rounded-lg text-sm">
                Debug mode bypasses authentication. Enter any user ID to log in
                directly using the X-Debug-User-Id header.
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  User ID
                </label>
                <input
                  type="number"
                  value={debugUserId}
                  onChange={(e) => setDebugUserId(e.target.value)}
                  placeholder="e.g. 1"
                  className="input-field"
                  required
                  min={1}
                />
              </div>
              <button type="submit" className="btn-primary w-full bg-yellow-500 hover:bg-yellow-600">
                Debug Login
              </button>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}
