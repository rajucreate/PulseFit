import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api, errorText } from '../api';
import { Alert, Button, Field } from '../components/ui';
import AuthFrame from './AuthFrame';

export default function ForgotPassword() {
  const [email, setEmail] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  async function submit(event) {
    event.preventDefault();
    setPending(true);
    setError('');
    setNotice('');
    try {
      const result = await api('/api/auth/password/forgot', {
        method: 'POST',
        auth: false,
        body: { email: email.trim() },
      });
      setNotice(result.message);
    } catch (err) {
      setError(errorText(err));
    } finally {
      setPending(false);
    }
  }

  return (
    <AuthFrame title="Forgot password" text="Enter your account email. You will see the same confirmation either way.">
      <Alert>{error}</Alert>
      {notice ? <Alert tone="info">{notice}</Alert> : null}
      {!notice ? (
        <form className="stack" onSubmit={submit}>
          <Field label="Email">
            <input type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} required />
          </Field>
          <Button type="submit" disabled={pending}>{pending ? 'Sending…' : 'Send reset link'}</Button>
        </form>
      ) : null}
      <p className="auth-switch"><Link to="/login">Back to sign in</Link></p>
    </AuthFrame>
  );
}
