import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useAuth } from './auth';
import Layout from './components/Layout';
import Attendance from './pages/Attendance';
import Audit from './pages/Audit';
import Claim from './pages/Claim';
import Dashboard from './pages/Dashboard';
import Login from './pages/Login';
import Members from './pages/Members';
import Plans from './pages/Plans';
import Register from './pages/Register';
import Subscriptions from './pages/Subscriptions';
import Team from './pages/Team';

function RequireAuth({ children }) {
  const { user } = useAuth();
  const location = useLocation();
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return children;
}

function GuestOnly({ children }) {
  const { user } = useAuth();
  if (user) return <Navigate to="/" replace />;
  return children;
}

function RequireRole({ roles, children }) {
  const { user } = useAuth();
  if (!user || !roles.includes(user.role)) return <Navigate to="/" replace />;
  return children;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<GuestOnly><Login /></GuestOnly>} />
      <Route path="/register" element={<GuestOnly><Register /></GuestOnly>} />
      <Route path="/claim" element={<GuestOnly><Claim /></GuestOnly>} />
      <Route
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route index element={<Dashboard />} />
        <Route path="plans" element={<Plans />} />
        <Route path="members" element={<Members />} />
        <Route path="subscriptions" element={<Subscriptions />} />
        <Route path="attendance" element={<Attendance />} />
        <Route path="team" element={<RequireRole roles={['ADMIN']}><Team /></RequireRole>} />
        <Route path="audit" element={<RequireRole roles={['ADMIN']}><Audit /></RequireRole>} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
