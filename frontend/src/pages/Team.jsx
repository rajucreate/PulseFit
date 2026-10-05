import { useState } from 'react';
import { api, errorText } from '../api';
import { useAuth } from '../auth';
import { Alert, Button, Field, PageHeader } from '../components/ui';

export default function Team() {
  const { user } = useAuth();
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');
  const [staff, setStaff] = useState({ email: '', temporaryPassword: '', firstName: '', lastName: '' });
  const [admin, setAdmin] = useState({ email: '', temporaryPassword: '', firstName: '', lastName: '' });
  const [roleForm, setRoleForm] = useState({ id: '', role: 'STAFF' });
  const [statusForm, setStatusForm] = useState({ id: '', enabled: 'true' });
  const [pending, setPending] = useState('');

  async function run(key, action) {
    setPending(key);
    setError('');
    setNotice('');
    try {
      const result = await action();
      if (result?.email) {
        setNotice(`${result.email} is now ${result.role}${result.enabled ? '' : ' (disabled)'}. User id ${result.userId}.`);
      } else {
        setNotice('Saved.');
      }
    } catch (err) {
      setError(errorText(err));
    } finally {
      setPending('');
    }
  }

  return (
    <div>
      <PageHeader
        eyebrow="Administration"
        title="Team access"
        text={`Signed in as user ${user.userId}. Staff and admin accounts are created here. User ids for role changes also appear in the audit log.`}
      />
      <Alert>{error}</Alert>
      {notice ? <Alert tone="info">{notice}</Alert> : null}
      <div className="form-grid">
        <form
          className="panel stack"
          onSubmit={(event) => {
            event.preventDefault();
            run('staff', () => api('/api/auth/users/staff', { method: 'POST', body: trimAccount(staff) }));
          }}
        >
          <h2>New staff</h2>
          <AccountFields value={staff} onChange={setStaff} />
          <Button type="submit" disabled={pending === 'staff'}>{pending === 'staff' ? 'Creating…' : 'Create staff'}</Button>
        </form>
        <form
          className="panel stack"
          onSubmit={(event) => {
            event.preventDefault();
            run('admin', () => api('/api/auth/users/admin', { method: 'POST', body: trimAccount(admin) }));
          }}
        >
          <h2>New admin</h2>
          <AccountFields value={admin} onChange={setAdmin} />
          <Button type="submit" disabled={pending === 'admin'}>{pending === 'admin' ? 'Creating…' : 'Create admin'}</Button>
        </form>
        <form
          className="panel stack"
          onSubmit={(event) => {
            event.preventDefault();
            run('role', () => api(`/api/auth/users/${roleForm.id}/role`, { method: 'PUT', body: { role: roleForm.role } }));
          }}
        >
          <h2>Change role</h2>
          <Field label="User id">
            <input type="number" min="1" value={roleForm.id} onChange={(e) => setRoleForm({ ...roleForm, id: e.target.value })} required />
          </Field>
          <Field label="Role">
            <select value={roleForm.role} onChange={(e) => setRoleForm({ ...roleForm, role: e.target.value })}>
              <option value="MEMBER">MEMBER</option>
              <option value="STAFF">STAFF</option>
              <option value="ADMIN">ADMIN</option>
            </select>
          </Field>
          <Button type="submit" disabled={pending === 'role'}>{pending === 'role' ? 'Saving…' : 'Update role'}</Button>
        </form>
        <form
          className="panel stack"
          onSubmit={(event) => {
            event.preventDefault();
            run('status', () =>
              api(`/api/auth/users/${statusForm.id}/status`, {
                method: 'PUT',
                body: { enabled: statusForm.enabled === 'true' },
              })
            );
          }}
        >
          <h2>Enable or disable</h2>
          <Field label="User id">
            <input type="number" min="1" value={statusForm.id} onChange={(e) => setStatusForm({ ...statusForm, id: e.target.value })} required />
          </Field>
          <Field label="Account">
            <select value={statusForm.enabled} onChange={(e) => setStatusForm({ ...statusForm, enabled: e.target.value })}>
              <option value="true">Enabled</option>
              <option value="false">Disabled</option>
            </select>
          </Field>
          <Button type="submit" disabled={pending === 'status'}>{pending === 'status' ? 'Saving…' : 'Update status'}</Button>
        </form>
      </div>
    </div>
  );
}

function trimAccount(value) {
  return {
    email: value.email.trim(),
    temporaryPassword: value.temporaryPassword,
    firstName: value.firstName.trim(),
    lastName: value.lastName.trim(),
  };
}

function AccountFields({ value, onChange }) {
  function set(key, next) {
    onChange({ ...value, [key]: next });
  }
  return (
    <>
      <div className="split">
        <Field label="First name">
          <input value={value.firstName} onChange={(e) => set('firstName', e.target.value)} required />
        </Field>
        <Field label="Last name">
          <input value={value.lastName} onChange={(e) => set('lastName', e.target.value)} required />
        </Field>
      </div>
      <Field label="Email">
        <input type="email" value={value.email} onChange={(e) => set('email', e.target.value)} required />
      </Field>
      <Field label="Temporary password" hint="At least 8 characters.">
        <input type="password" minLength={8} value={value.temporaryPassword} onChange={(e) => set('temporaryPassword', e.target.value)} required />
      </Field>
    </>
  );
}
