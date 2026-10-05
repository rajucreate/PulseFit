import { useEffect, useState } from 'react';
import { api, errorText } from '../api';
import { useAuth } from '../auth';
import { Alert, Badge, Button, Empty, Field, Modal, PageHeader, Spinner } from '../components/ui';
import { formatMoney } from '../format';

const EMPTY = {
  planName: '',
  durationInDays: 30,
  price: '29.99',
  description: '',
  active: true,
};

export default function Plans() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const [plans, setPlans] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [form, setForm] = useState(null);
  const [pending, setPending] = useState(false);

  async function load() {
    setLoading(true);
    setError('');
    try {
      setPlans(await api('/api/plans'));
    } catch (err) {
      setError(errorText(err));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  function openCreate() {
    setForm({ mode: 'create', ...EMPTY });
  }

  function openEdit(plan) {
    setForm({
      mode: 'edit',
      id: plan.id,
      planName: plan.planName,
      durationInDays: plan.durationInDays,
      price: String(plan.price),
      description: plan.description || '',
      active: Boolean(plan.active),
    });
  }

  async function save(event) {
    event.preventDefault();
    setPending(true);
    setError('');
    const body = {
      planName: form.planName.trim(),
      durationInDays: Number(form.durationInDays),
      price: Number(form.price),
      description: form.description.trim(),
      active: Boolean(form.active),
    };
    try {
      if (form.mode === 'create') await api('/api/plans', { method: 'POST', body });
      else await api(`/api/plans/${form.id}`, { method: 'PUT', body });
      setForm(null);
      await load();
    } catch (err) {
      setError(errorText(err));
    } finally {
      setPending(false);
    }
  }

  async function remove(plan) {
    if (!window.confirm(`Delete ${plan.planName}?`)) return;
    setError('');
    try {
      await api(`/api/plans/${plan.id}`, { method: 'DELETE' });
      await load();
    } catch (err) {
      setError(errorText(err));
    }
  }

  return (
    <div>
      <PageHeader
        eyebrow="Catalog"
        title="Membership plans"
        text="Public catalog. Only an admin can add or change a plan."
        actions={isAdmin ? <Button onClick={openCreate}>New plan</Button> : null}
      />
      <Alert>{error}</Alert>
      {loading ? <Spinner label="Loading plans" /> : null}
      {!loading && plans.length === 0 ? (
        <Empty title="No plans yet" text="An admin can publish the first membership plan." />
      ) : null}
      <div className="card-grid">
        {plans.map((plan) => (
          <article key={plan.id} className="plan-card">
            <div className="panel-head">
              <Badge tone={plan.active ? 'good' : 'neutral'}>{plan.active ? 'Active' : 'Hidden'}</Badge>
              <span className="muted">#{plan.id}</span>
            </div>
            <h2>{plan.planName}</h2>
            <p className="price">{formatMoney(plan.price)}<span> / {plan.durationInDays} days</span></p>
            <p className="lede">{plan.description || 'Standard club access.'}</p>
            {isAdmin ? (
              <div className="row">
                <Button variant="secondary" onClick={() => openEdit(plan)}>Edit</Button>
                <Button variant="danger" onClick={() => remove(plan)}>Delete</Button>
              </div>
            ) : null}
          </article>
        ))}
      </div>
      {form ? (
        <Modal title={form.mode === 'create' ? 'New plan' : 'Edit plan'} onClose={() => setForm(null)}>
          <form onSubmit={save} className="stack">
            <Field label="Name">
              <input value={form.planName} onChange={(e) => setForm({ ...form, planName: e.target.value })} required />
            </Field>
            <div className="split">
              <Field label="Duration (days)">
                <input type="number" min="1" value={form.durationInDays} onChange={(e) => setForm({ ...form, durationInDays: e.target.value })} required />
              </Field>
              <Field label="Price">
                <input type="number" min="0" step="0.01" value={form.price} onChange={(e) => setForm({ ...form, price: e.target.value })} required />
              </Field>
            </div>
            <Field label="Description">
              <textarea rows={3} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
            </Field>
            <label className="check">
              <input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} />
              Available for new subscriptions
            </label>
            <div className="row end">
              <Button variant="secondary" onClick={() => setForm(null)}>Cancel</Button>
              <Button type="submit" disabled={pending}>{pending ? 'Saving…' : 'Save plan'}</Button>
            </div>
          </form>
        </Modal>
      ) : null}
    </div>
  );
}
