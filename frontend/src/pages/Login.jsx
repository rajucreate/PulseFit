import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { errorText } from '../api';
import { useAuth } from '../auth';
import { Alert, Button, Field } from '../components/ui';
import AuthFrame from './AuthFrame';

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);

  async function onSubmit(event) {
    event.preventDefault();
    setError('');
    setPending(true);
    try {
      await login(email.trim(), password);
      navigate(location.state?.from || '/', { replace: true });
    } catch (err) {
      setError(errorText(err));
    } finally {
      setPending(false);
    }
  }

  return (
    <AuthFrame
      title="Sign in"
      text="Use the account issued by the club, or the bootstrap admin for desk setup."
    >
      <form onSubmit={onSubmit} className="stack">
        <Alert>{error}</Alert>
        <Field label="Email">
          <input type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </Field>
        <Field label="Password">
          <input type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </Field>
        <Button type="submit" disabled={pending}>
          {pending ? 'Signing in…' : 'Sign in'}
        </Button>
      </form>
      <p className="auth-switch">
        <Link to="/forgot">Forgot password?</Link>
      </p>
      <p className="auth-switch">
        New members join by invitation from a club administrator.
      </p>
      <p className="auth-switch">
        Already on the member list? <Link to="/claim">Claim your profile</Link>
      </p>
      <aside className="demo-card">
        <p>Local admin</p>
        <code>admin@pulsefit.com</code>
        <code>Admin@PulseFit2026</code>
      </aside>
      <aside className="demo-card">
        <p>Trainee</p>
        <code>trainee@pulsefit.com</code>
        <code>Trainee@PulseFit2026</code>
      </aside>
    </AuthFrame>
  );
}
