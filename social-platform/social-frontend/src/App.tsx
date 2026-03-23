import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from './stores/authStore';
import AppShell from './components/layout/AppShell';
import LoginPage from './pages/LoginPage';
import HomePage from './pages/HomePage';
import ProfilePage from './pages/ProfilePage';
import TeamPage from './pages/TeamPage';
import SearchPage from './pages/SearchPage';
import GroupPage from './pages/GroupPage';
import PagePage from './pages/PagePage';
import MessagesPage from './pages/MessagesPage';
import AboutPage from './pages/AboutPage';
import AdminPage from './pages/AdminPage';

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAuthStore((s) => !!s.token || !!s.debugUserId);
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/*"
        element={
          <ProtectedRoute>
            <AppShell>
              <Routes>
                <Route path="/" element={<HomePage />} />
                <Route path="/profile/:id" element={<ProfilePage />} />
                <Route path="/team/:id" element={<TeamPage />} />
                <Route path="/group/:id" element={<GroupPage />} />
                <Route path="/page/:id" element={<PagePage />} />
                <Route path="/messages" element={<MessagesPage />} />
                <Route path="/messages/:partnerId" element={<MessagesPage />} />
                <Route path="/search" element={<SearchPage />} />
                <Route path="/about" element={<AboutPage />} />
                <Route path="/admin/*" element={<AdminPage />} />
              </Routes>
            </AppShell>
          </ProtectedRoute>
        }
      />
    </Routes>
  );
}
