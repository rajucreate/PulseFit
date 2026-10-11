import { useEffect, useState } from 'react';
import { api, errorText } from '../api';
import { Alert, Badge, Button, Empty, Field, PageHeader, Spinner, statusTone } from '../components/ui';

export default function Invitations() {
  const [rows, setRows] = useState([]);
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [devLink, setDevLink] = useState('');

  async function load() {
    setLoading(true);
    setError('');
    try {
      setRows(await api('/api/auth/invitations'));
    } catch (err) {
      setError(errorText(err));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  async function invite(event) {
    event.preventDefault();
    setPending(true);
    setError('');
    setNotice('');
    setDevLink('');
    try {
      const created = await api('/api/auth/invitations', {
        method: 'POST',
        body: { fullName: fullName.trim(), email: email.trim() },
      });
      setNotice(created.message);
      setDevLink(created.inviteUrl || '');
      setFullName('');
      setEmail('');
      await load();
    } catch (err) {
      setError(errorText(err));
    } finally {
      setPending(false);
    }
  }

  async function resend(row) {
    setError('');
    setNotice('');
    setDevLink('');
    try {
      const updated = await api(`/api/auth/invitations/${row.id}/resend`, { method: 'POST' });
      setNotice(updated.message);
      setDevLink(updated.inviteUrl || '');
      await load();
    } catch (err) {
      setError(errorText(err));
    }
  }

  return (
    <div>
      <PageHeader
        eyebrow="Members"
        title="Invite a member"
        text="Only an administrator can invite someone. The email contains a single-use link so they can choose their own password."
      />
      <Alert>{error}</Alert>
      {notice ? <Alert tone="info">{notice}</Alert> : null}
      {devLink ? (
        <Alert tone="info">
          SMTP is not configured, so the link was not emailed. Copy it for this demo: {devLink}
        </Alert>
      ) : null}
      <section className="panel">
        <h2>New invitation</h2>
        <form className="stack" onSubmit={invite}>
          <Field label="Full name">
            <input value={fullName} onChange={(e) => setFullName(e.target.value)} required />
          </Field>
          <Field label="Email">
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          </Field>
          <Button type="submit" disabled={pending}>{pending ? 'Sending…' : 'Send invitation'}</Button>
        </form>
      </section>
      {loading ? <Spinner label="Loading invitations" /> : null}
      {!loading && rows.length === 0 ? <Empty title="No invitations" text="Invite the first member by email." /> : null}
      {!loading && rows.length > 0 ? (
        <div className="table-wrap">
          <table>
            <thead>
              <tr><th>Name</th><th>Email</th><th>Status</th><th /></tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id}>
                  <td>{row.fullName}</td>
                  <td>{row.email}</td>
                  <td><Badge tone={statusTone(row.status === 'PENDING' ? 'ACTIVE' : row.status === 'EXPIRED' ? 'EXPIRED' : 'INACTIVE')}>{row.status}</Badge></td>
                  <td className="actions">
                    {row.status !== 'CONSUMED' ? <Button variant="secondary" onClick={() => resend(row)}>Resend</Button> : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </div>
  );
}
