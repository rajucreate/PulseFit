import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, errorText } from '../api';
import { Alert, Button, Field } from '../components/ui';
import AuthFrame from './AuthFrame';

export default function AcceptInvite() {
  const { token } = useParams();
  const [preview, setPreview] = useState(null);
  const [password, setPassword] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const [done, setDone] = useState('');

  useEffect(() => {
    let ignore = false;
    api(`/api/auth/invitations/preview?token=${encodeURIComponent(token)}`, { auth: false })
      .then((data) => { if (!ignore) setPreview(data); })
      .catch((err) => { if (!ignore) setError(errorText(err)); });
    return () => { ignore = true; };
  }, [token]);

  async function submit(event) {
    event.preventDefault();
    setPending(true);
    setError('');
    try {
      const result = await api('/api/auth/invitations/accept', {
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

  const invalid = preview && !preview.valid;

  return (
    <AuthFrame title="Set your password" text="This invitation link is single-use. Choose the password you will use to sign in.">
      <Alert>{error}</Alert>
      {!preview && !error ? <p>Checking invitation…</p> : null}
      {invalid ? (
        <div className="stack">
          <p>This invitation is {preview.reason === 'EXPIRED' ? 'expired' : preview.reason === 'USED' ? 'already used' : 'invalid'}.</p>
          <p className="muted">Ask the club administrator to send a new invitation.</p>
          <Link to="/login">Back to sign in</Link>
        </div>
      ) : null}
      {done ? (
        <div className="stack">
          <Alert tone="info">{done}</Alert>
          <Link className="btn btn-primary" to="/login">Sign in</Link>
        </div>
      ) : null}
      {preview?.valid && !done ? (
        <form className="stack" onSubmit={submit}>
          <p>{preview.fullName} · {preview.email}</p>
          <Field label="Password" hint="At least 8 characters.">
            <input type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} minLength={8} required />
          </Field>
          <Button type="submit" disabled={pending}>{pending ? 'Saving…' : 'Create account'}</Button>
        </form>
      ) : null}
    </AuthFrame>
  );
}
