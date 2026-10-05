import { useEffect, useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth';
import { displayName, roleLabel } from '../format';

const ICONS = {
  overview:
    'M4 4h7v7H4V4zm9 0h7v7h-7V4zM4 13h7v7H4v-7zm9 3h7v4h-7v-4z',
  plans: 'M5 6h14M5 12h14M5 18h9',
  members: 'M8 11a3 3 0 1 0 0-6 3 3 0 0 0 0 6zm8 1a2.5 2.5 0 1 0 0-5M3 19a5 5 0 0 1 10 0m2 0a4.5 4.5 0 0 1 6 0',
  subs: 'M4 7h16v12H4V7zm0 4h16M8 7V5m8 2V5',
  check: 'M5 12l4 4L19 7',
  team: 'M12 12a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM5 19a7 7 0 0 1 14 0',
  audit: 'M6 4h9l3 3v13H6V4zm8 0v4h4M8 12h8M8 16h6',
};

function Icon({ name }) {
  return (
    <svg viewBox="0 0 24 24" className="nav-icon" aria-hidden="true">
      <path d={ICONS[name]} fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export default function Layout() {
  const { user, expiresAt, logout } = useAuth();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [minutes, setMinutes] = useState('');

  useEffect(() => {
    function tick() {
      if (!expiresAt) {
        setMinutes('');
        return;
      }
      const left = Math.max(0, Math.ceil((expiresAt - Date.now()) / 60000));
      setMinutes(`${left} min`);
    }
    tick();
    const id = setInterval(tick, 15000);
    return () => clearInterval(id);
  }, [expiresAt]);

  const links = [
    { to: '/', label: 'Overview', icon: 'overview', end: true },
    { to: '/plans', label: 'Plans', icon: 'plans' },
    { to: '/members', label: user?.role === 'MEMBER' ? 'My profile' : 'Members', icon: 'members' },
    { to: '/subscriptions', label: 'Subscriptions', icon: 'subs' },
    { to: '/attendance', label: 'Attendance', icon: 'check' },
  ];
  if (user?.role === 'ADMIN') {
    links.push({ to: '/team', label: 'Team', icon: 'team' });
    links.push({ to: '/audit', label: 'Audit log', icon: 'audit' });
  }

  async function onLogout() {
    await logout();
    navigate('/login', { replace: true });
  }

  return (
    <div className={`shell ${open ? 'nav-open' : ''}`}>
      <aside className="sidebar">
        <div className="brand">
          <span className="mark" aria-hidden="true" />
          <div>
            <strong>PulseFit</strong>
            <small>Club operations</small>
          </div>
        </div>
        <nav>
          {links.map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              end={link.end}
              className={({ isActive }) => (isActive ? 'active' : undefined)}
              onClick={() => setOpen(false)}
            >
              <Icon name={link.icon} />
              {link.label}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-foot">
          <p className="who">{displayName(user)}</p>
          <p className="who-meta">
            {roleLabel(user?.role)}
            {user?.memberId ? ` · Member ${user.memberId}` : ''}
          </p>
          {minutes ? <p className="session-note">Session {minutes}</p> : null}
          <button type="button" className="btn btn-ghost" onClick={onLogout}>
            Sign out
          </button>
        </div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <button type="button" className="icon-btn menu-btn" onClick={() => setOpen((value) => !value)} aria-label="Menu">
            ☰
          </button>
          <span className="topbar-title">PulseFit</span>
        </header>
        <main className="content">
          <Outlet />
        </main>
      </div>
      {open ? <button type="button" className="scrim" aria-label="Close menu" onClick={() => setOpen(false)} /> : null}
    </div>
  );
}
