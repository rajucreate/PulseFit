import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, errorText } from '../api';
import { useAuth } from '../auth';
import { Alert, Button, Field } from '../components/ui';
import { formatDateTime } from '../format';
import AuthFrame from './AuthFrame';

export default function Claim() {
  const { completeClaim } = useAuth();
  const navigate = useNavigate();
  const [step, setStep] = useState(1);
  const [email, setEmail] = useState('');
  const [otp, setOtp] = useState('');
  const [password, setPassword] = useState('');
  const [notice, setNotice] = useState(null);
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);

  async function initiate(event) {
    event.preventDefault();
    setError('');
    setPending(true);
    try {
      const data = await api('/api/auth/claim/initiate', {
        method: 'POST',
        auth: false,
        body: { email: email.trim() },
      });
      setNotice(data);
      setStep(2);
    } catch (err) {
      setError(errorText(err));
    } finally {
      setPending(false);
    }
  }

  async function complete(event) {
    event.preventDefault();
    setError('');
    if (password.length < 8) {
      setError('Password must be at least 8 characters long');
      return;
    }
    setPending(true);
    try {
      await completeClaim({ email: email.trim(), otp: otp.trim(), password });
      navigate('/', { replace: true });
    } catch (err) {
      setError(errorText(err));
    } finally {
      setPending(false);
    }
  }

  return (
    <AuthFrame
      title="Claim an existing profile"
      text="If staff already added you to the member list, request a one-time code and set a password."
    >
      <Alert>{error}</Alert>
      {step === 1 ? (
        <form onSubmit={initiate} className="stack">
          <Field label="Email on the member record">
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          </Field>
          <Button type="submit" disabled={pending}>
            {pending ? 'Sending code…' : 'Send code'}
          </Button>
        </form>
      ) : (
        <form onSubmit={complete} className="stack">
          {notice ? (
            <Alert tone="info">
              {notice.message || 'Code sent.'}
              {notice.expiresAt ? ` Expires ${formatDateTime(notice.expiresAt)}.` : ''}
              {notice.devOtp ? ` Development code: ${notice.devOtp}` : ''}
            </Alert>
          ) : null}
          <Field label="Email">
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          </Field>
          <Field label="6-digit code">
            <input inputMode="numeric" maxLength={6} value={otp} onChange={(e) => setOtp(e.target.value)} required />
          </Field>
          <Field label="New password" hint="At least 8 characters.">
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={8} />
          </Field>
          <div className="row">
            <Button variant="secondary" onClick={() => setStep(1)}>Back</Button>
            <Button type="submit" disabled={pending}>
              {pending ? 'Claiming…' : 'Claim profile'}
            </Button>
          </div>
        </form>
      )}
      <p className="auth-switch">
        <Link to="/login">Back to sign in</Link>
      </p>
    </AuthFrame>
  );
}
