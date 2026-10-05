import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { errorText } from '../api';
import { useAuth } from '../auth';
import { Alert, Button, Field } from '../components/ui';
import AuthFrame from './AuthFrame';

const EMPTY = {
  firstName: '',
  lastName: '',
  email: '',
  contact: '',
  dateOfBirth: '',
  password: '',
};

export default function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState(EMPTY);
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);

  function set(key, value) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  async function onSubmit(event) {
    event.preventDefault();
    setError('');
    if (form.password.length < 8) {
      setError('Password must be at least 8 characters long');
      return;
    }
    setPending(true);
    try {
      await register({
        email: form.email.trim(),
        password: form.password,
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        contact: form.contact.trim(),
        dateOfBirth: form.dateOfBirth || null,
      });
      navigate('/', { replace: true });
    } catch (err) {
      setError(errorText(err));
    } finally {
      setPending(false);
    }
  }

  return (
    <AuthFrame title="Create your membership" text="Registration opens an account and a member profile together.">
      <form onSubmit={onSubmit} className="stack">
        <Alert>{error}</Alert>
        <div className="split">
          <Field label="First name">
            <input value={form.firstName} onChange={(e) => set('firstName', e.target.value)} required />
          </Field>
          <Field label="Last name">
            <input value={form.lastName} onChange={(e) => set('lastName', e.target.value)} required />
          </Field>
        </div>
        <Field label="Email">
          <input type="email" value={form.email} onChange={(e) => set('email', e.target.value)} required />
        </Field>
        <div className="split">
          <Field label="Contact">
            <input value={form.contact} onChange={(e) => set('contact', e.target.value)} placeholder="555-0100" required />
          </Field>
          <Field label="Date of birth">
            <input type="date" value={form.dateOfBirth} onChange={(e) => set('dateOfBirth', e.target.value)} />
          </Field>
        </div>
        <Field label="Password" hint="At least 8 characters.">
          <input type="password" value={form.password} onChange={(e) => set('password', e.target.value)} required minLength={8} />
        </Field>
        <Button type="submit" disabled={pending}>
          {pending ? 'Creating account…' : 'Create account'}
        </Button>
      </form>
      <p className="auth-switch">
        Already registered? <Link to="/login">Sign in</Link>
      </p>
    </AuthFrame>
  );
}
