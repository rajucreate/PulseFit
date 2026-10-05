import { useEffect, useState } from 'react';
import { api, errorText } from '../api';
import { useAuth } from '../auth';
import { Alert, Badge, Button, Empty, Field, Modal, PageHeader, Spinner, statusTone } from '../components/ui';
import { addDays, formatDate, todayISO } from '../format';

export default function Subscriptions() {
  const { user } = useAuth();
  const desk = user?.role === 'STAFF' || user?.role === 'ADMIN';
  const isAdmin = user?.role === 'ADMIN';
  const [rows, setRows] = useState([]);
  const [plans, setPlans] = useState([]);
  const [members, setMembers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [form, setForm] = useState(null);
  const [pending, setPending] = useState(false);

  async function load() {
    setLoading(true);
    setError('');
    try {
      const planList = await api('/api/plans');
      setPlans(planList);
      if (user.role === 'MEMBER') {
        if (!user.memberId) {
          setRows([]);
          return;
        }
        setRows(await api(`/api/subscriptions/member/${user.memberId}`));
        return;
      }
      const [subs, memberList] = await Promise.all([api('/api/subscriptions'), api('/api/members')]);
      setRows(subs);
      setMembers(memberList);
    } catch (err) {
      setError(errorText(err));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, [user]);

  function planName(id) {
    return plans.find((plan) => plan.id === id)?.planName || `Plan ${id}`;
  }

  function memberName(id) {
    return members.find((member) => member.id === id)?.name || `Member ${id}`;
  }

  function openCreate() {
    const start = todayISO();
    const firstPlan = plans.find((plan) => plan.active) || plans[0];
    setForm({
      mode: 'create',
      memberId: user.role === 'MEMBER' ? user.memberId : members[0]?.id || '',
      planId: firstPlan?.id || '',
      startDate: start,
      expiryDate: firstPlan ? addDays(start, firstPlan.durationInDays) : start,
      status: 'ACTIVE',
    });
  }

  function openEdit(row) {
    setForm({ mode: 'edit', ...row });
  }

  function onPlanChange(planId) {
    const plan = plans.find((item) => String(item.id) === String(planId));
    setForm((current) => ({
      ...current,
      planId,
      expiryDate: plan ? addDays(current.startDate, plan.durationInDays) : current.expiryDate,
    }));
  }

  async function save(event) {
    event.preventDefault();
    setPending(true);
    setError('');
    const body = {
      memberId: Number(form.memberId),
      planId: Number(form.planId),
      startDate: form.startDate,
      expiryDate: form.expiryDate,
      status: form.status,
    };
    try {
      if (form.mode === 'create') await api('/api/subscriptions', { method: 'POST', body });
      else await api(`/api/subscriptions/${form.id}`, { method: 'PUT', body });
      setForm(null);
      await load();
    } catch (err) {
      setError(errorText(err));
    } finally {
      setPending(false);
    }
  }

  async function remove(row) {
    if (!window.confirm(`Delete subscription #${row.id}?`)) return;
    try {
      await api(`/api/subscriptions/${row.id}`, { method: 'DELETE' });
      await load();
    } catch (err) {
      setError(errorText(err));
    }
  }

  const canCreate = user.role === 'MEMBER' ? Boolean(user.memberId) : desk;

  return (
    <div>
      <PageHeader
        eyebrow="Billing"
        title="Subscriptions"
        text="A subscription is valid when it is active and today falls inside the start and expiry dates."
        actions={canCreate ? <Button onClick={openCreate} disabled={plans.length === 0 || (desk && members.length === 0)}>New subscription</Button> : null}
      />
      <Alert>{error}</Alert>
      {loading ? <Spinner label="Loading subscriptions" /> : null}
      {!loading && rows.length === 0 ? (
        <Empty title="No subscriptions" text="Choose a plan and set the membership window." />
      ) : null}
      {!loading && rows.length > 0 ? (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Member</th>
                <th>Plan</th>
                <th>Window</th>
                <th>Status</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id}>
                  <td>{desk ? memberName(row.memberId) : `You · #${row.memberId}`}</td>
                  <td>{planName(row.planId)}</td>
                  <td>{formatDate(row.startDate)} – {formatDate(row.expiryDate)}</td>
                  <td><Badge tone={statusTone(row.status)}>{row.status}</Badge></td>
                  <td className="actions">
                    {desk || user.role === 'MEMBER' ? <Button variant="secondary" onClick={() => openEdit(row)}>Edit</Button> : null}
                    {isAdmin ? <Button variant="danger" onClick={() => remove(row)}>Delete</Button> : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
      {form ? (
        <Modal title={form.mode === 'create' ? 'New subscription' : 'Edit subscription'} onClose={() => setForm(null)}>
          <form onSubmit={save} className="stack">
            <Field label="Member">
              {user.role === 'MEMBER' ? (
                <input value={`Member ${user.memberId}`} disabled />
              ) : (
                <select value={form.memberId} onChange={(e) => setForm({ ...form, memberId: e.target.value })} required>
                  {members.map((member) => (
                    <option key={member.id} value={member.id}>{member.name} (#{member.id})</option>
                  ))}
                </select>
              )}
            </Field>
            <Field label="Plan">
              <select value={form.planId} onChange={(e) => onPlanChange(e.target.value)} required>
                {plans.map((plan) => (
                  <option key={plan.id} value={plan.id}>
                    {plan.planName} · {plan.durationInDays} days {plan.active ? '' : '(inactive)'}
                  </option>
                ))}
              </select>
            </Field>
            <div className="split">
              <Field label="Start">
                <input type="date" value={form.startDate} onChange={(e) => setForm({ ...form, startDate: e.target.value })} required />
              </Field>
              <Field label="Expiry">
                <input type="date" value={form.expiryDate} onChange={(e) => setForm({ ...form, expiryDate: e.target.value })} required />
              </Field>
            </div>
            <Field label="Status">
              <select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value })}>
                <option value="ACTIVE">ACTIVE</option>
                <option value="EXPIRED">EXPIRED</option>
                <option value="CANCELLED">CANCELLED</option>
              </select>
            </Field>
            <div className="row end">
              <Button variant="secondary" onClick={() => setForm(null)}>Cancel</Button>
              <Button type="submit" disabled={pending}>{pending ? 'Saving…' : 'Save subscription'}</Button>
            </div>
          </form>
        </Modal>
      ) : null}
    </div>
  );
}
