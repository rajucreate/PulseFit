import { useEffect, useState } from 'react';
import { api, errorText } from '../api';
import { useAuth } from '../auth';
import { Alert, Badge, Button, Empty, Field, Modal, PageHeader, Spinner, statusTone } from '../components/ui';
import { formatDate } from '../format';

const EMPTY = { name: '', email: '', contact: '', dateOfBirth: '', status: 'ACTIVE' };

export default function Members() {
  const { user } = useAuth();
  const isMember = user?.role === 'MEMBER';
  const trainee = user?.role === 'TRAINEE';
  const canManage = user?.role === 'STAFF' || user?.role === 'ADMIN';
  const isAdmin = user?.role === 'ADMIN';
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [form, setForm] = useState(null);
  const [pending, setPending] = useState(false);
  const [query, setQuery] = useState('');
  const [notice, setNotice] = useState('');
  const [devLink, setDevLink] = useState('');

  async function load() {
    setLoading(true);
    setError('');
    try {
      if (trainee) {
        const roster = await api('/api/trainers/me/roster');
        setRows((roster.members || []).map((member) => ({
          id: member.memberId,
          name: member.name,
          email: member.email,
          contact: member.contact,
          status: member.status,
        })));
      } else if (isMember) {
        if (!user.memberId) {
          setRows([]);
          return;
        }
        setRows([await api(`/api/members/${user.memberId}`)]);
      } else {
        setRows(await api('/api/members'));
      }
    } catch (err) {
      setError(errorText(err));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, [user]);

  function openCreate() {
    setForm({ mode: 'create', ...EMPTY });
  }

  function openEdit(member) {
    setForm({
      mode: 'edit',
      id: member.id,
      name: member.name,
      email: member.email,
      contact: member.contact || '',
      dateOfBirth: member.dateOfBirth || '',
      status: member.status || 'ACTIVE',
    });
  }

  async function save(event) {
    event.preventDefault();
    setPending(true);
    setError('');
    setNotice('');
    setDevLink('');
    const body = {
      name: form.name.trim(),
      email: form.email.trim().toLowerCase(),
      contact: form.contact.trim(),
      dateOfBirth: form.dateOfBirth || null,
      status: form.status,
    };
    try {
      if (form.mode === 'create') {
        await api('/api/members', { method: 'POST', body });
        if (isAdmin) {
          try {
            const invite = await api('/api/auth/invitations', {
              method: 'POST',
              body: { fullName: body.name, email: body.email },
            });
            setNotice(invite.message);
            setDevLink(invite.inviteUrl || '');
          } catch (err) {
            setError(`Member saved, but the email was not sent. ${errorText(err)}`);
          }
        }
      } else {
        await api(`/api/members/${form.id}`, { method: 'PUT', body });
      }
      setForm(null);
      await load();
    } catch (err) {
      setError(errorText(err));
    } finally {
      setPending(false);
    }
  }

  async function remove(member) {
    if (!window.confirm(`Delete ${member.name}? This cannot be undone.`)) return;
    try {
      await api(`/api/members/${member.id}`, { method: 'DELETE' });
      await load();
    } catch (err) {
      setError(errorText(err));
    }
  }

  const visible = rows.filter((member) => {
    const hay = `${member.name} ${member.email} ${member.contact || ''}`.toLowerCase();
    return hay.includes(query.trim().toLowerCase());
  });

  return (
    <div>
      <PageHeader
        eyebrow="People"
        title={isMember ? 'My profile' : 'Members'}
        text={trainee ? 'Members assigned to you. Profiles, plans, and attendance are read-only.' : isMember ? 'You can update only the profile linked to this account.' : 'Adding a member saves the profile. An administrator also emails that person a link to choose a password.'}
        actions={canManage ? <Button onClick={openCreate}>Add member</Button> : null}
      />
      <Alert>{error}</Alert>
      {notice ? <Alert tone="info">{notice}</Alert> : null}
      {devLink ? (
        <Alert tone="info">
          SMTP is not configured, so the link was not emailed. Copy it for this demo: {devLink}
        </Alert>
      ) : null}
      {loading ? <Spinner label="Loading members" /> : null}
      {!loading && isMember && !user.memberId ? (
        <Empty title="No profile linked" text="Registering creates a profile automatically. A staff-created record can be claimed from the sign-in page." />
      ) : null}
      {!loading && canManage ? (
        <div className="toolbar">
          <input placeholder="Search name, email, or contact" value={query} onChange={(e) => setQuery(e.target.value)} />
        </div>
      ) : null}
      {!loading && visible.length > 0 ? (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Email</th>
                <th>Contact</th>
                <th>Status</th>
                <th>Born</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {visible.map((member) => (
                <tr key={member.id}>
                  <td>
                    <strong>{member.name}</strong>
                    <div className="muted">#{member.id}</div>
                  </td>
                  <td>{member.email}</td>
                  <td>{member.contact || '—'}</td>
                  <td><Badge tone={statusTone(member.status)}>{member.status}</Badge></td>
                  <td>{formatDate(member.dateOfBirth)}</td>
                  <td className="actions">
                    {trainee ? null : <Button variant="secondary" onClick={() => openEdit(member)}>Edit</Button>}
                    {isAdmin ? <Button variant="danger" onClick={() => remove(member)}>Delete</Button> : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
      {!loading && canManage && rows.length > 0 && visible.length === 0 ? (
        <Empty title="No matches" text="Try a different name or email." />
      ) : null}
      {form ? (
        <Modal title={form.mode === 'create' ? 'Add member' : 'Edit member'} onClose={() => setForm(null)}>
          <form onSubmit={save} className="stack">
            {form.mode === 'create' && isAdmin ? (
              <p className="muted">Saving sends this person an email with a single-use link to set their password.</p>
            ) : null}
            <Field label="Full name">
              <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
            </Field>
            <Field label="Email">
              <input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} required />
            </Field>
            <div className="split">
              <Field label="Contact">
                <input value={form.contact} onChange={(e) => setForm({ ...form, contact: e.target.value })} required />
              </Field>
              <Field label="Date of birth">
                <input type="date" value={form.dateOfBirth || ''} onChange={(e) => setForm({ ...form, dateOfBirth: e.target.value })} />
              </Field>
            </div>
            <Field label="Status">
              <select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value })}>
                <option value="ACTIVE">ACTIVE</option>
                <option value="INACTIVE">INACTIVE</option>
                <option value="SUSPENDED">SUSPENDED</option>
              </select>
            </Field>
            <div className="row end">
              <Button variant="secondary" onClick={() => setForm(null)}>Cancel</Button>
              <Button type="submit" disabled={pending}>{pending ? 'Saving…' : 'Save member'}</Button>
            </div>
          </form>
        </Modal>
      ) : null}
    </div>
  );
}
