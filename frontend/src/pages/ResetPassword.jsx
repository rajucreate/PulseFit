import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, errorText } from '../api';
import { Alert, Button, Field } from '../components/ui';
import AuthFrame from './AuthFrame';

export default function ResetPassword() {
  const { token } = useParams();
  const [password, setPassword] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const [done, setDone] = useState('');

  async function submit(event) {
    event.preventDefault();
    setPending(true);
    setError('');
    try {
      const result = await api('/api/auth/password/reset', {
        method: 'POST',
        auth: false,
        body: { token, password },
      });
      setDone(result.message);
    } catch (err) {
      setError(errorText(err));
    } finally {
      setPending(false);
    }
  }

  return (
    <AuthFrame title="Choose a new password" text="This reset link works once. After it succeeds, sign in with the new password.">
      <Alert>{error}</Alert>
      {done ? (
        <div className="stack">
          <Alert tone="info">{done}</Alert>
          <Link className="btn btn-primary" to="/login">Sign in</Link>
        </div>
      ) : (
        <form className="stack" onSubmit={submit}>
          <Field label="New password" hint="At least 8 characters.">
            <input type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} minLength={8} required />
          </Field>
          <Button type="submit" disabled={pending}>{pending ? 'Updating…' : 'Update password'}</Button>
        </form>
      )}
      {!done ? <p className="auth-switch"><Link to="/forgot">Request a new link</Link></p> : null}
    </AuthFrame>
  );
}
