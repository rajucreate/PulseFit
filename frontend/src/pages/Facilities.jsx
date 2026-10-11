import { useEffect, useState } from 'react';
import { api, errorText } from '../api';
import { useAuth } from '../auth';
import { Alert, Badge, Button, Empty, Field, Modal, PageHeader, Spinner, statusTone } from '../components/ui';

const EMPTY = {
  name: '',
  address: '',
  location: '',
  contact: '',
  operatingHours: '06:00-22:00',
  capacity: 100,
  status: 'ACTIVE',
};

export default function Facilities() {
  const { user } = useAuth();
  const desk = user?.role === 'STAFF' || user?.role === 'ADMIN';
  const [rows, setRows] = useState([]);
  const [filters, setFilters] = useState({ name: '', location: '', status: '' });
  const [form, setForm] = useState(null);
  const [selected, setSelected] = useState(null);
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');

  async function load(next = filters) {
    setLoading(true);
    setError('');
    const params = new URLSearchParams();
    if (next.name) params.set('name', next.name);
    if (next.location) params.set('location', next.location);
    if (next.status) params.set('status', next.status);
    const query = params.toString();
    try {
      setRows(await api(`/api/facilities${query ? `?${query}` : ''}`));
    } catch (err) {
      setError(errorText(err));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  async function save(event) {
    event.preventDefault();
    setPending(true);
    setError('');
    const body = {
      ...form,
      capacity: Number(form.capacity),
      name: form.name.trim(),
      address: form.address.trim(),
      location: form.location.trim(),
      contact: form.contact.trim(),
      operatingHours: form.operatingHours.trim(),
    };
    try {
      if (form.mode === 'create') await api('/api/facilities', { method: 'POST', body });
      else await api(`/api/facilities/${form.id}`, { method: 'PUT', body });
      setForm(null);
      await load();
    } catch (err) {
      setError(errorText(err));
    } finally {
      setPending(false);
    }
  }

  async function deactivate(facility) {
    if (!window.confirm(`Deactivate ${facility.name}? Historical check-ins stay on file.`)) return;
    try {
      await api(`/api/facilities/${facility.id}`, { method: 'DELETE' });
      await load();
    } catch (err) {
      setError(errorText(err));
    }
  }

  return (
    <div>
      <PageHeader
        eyebrow="Branches"
        title="Facilities"
        text="Each club location keeps its own address, hours, and capacity. Deactivating a branch does not erase past visits."
        actions={desk ? <Button onClick={() => setForm({ mode: 'create', ...EMPTY })}>Add facility</Button> : null}
      />
      <Alert>{error}</Alert>
      <form
        className="toolbar split"
        onSubmit={(event) => {
          event.preventDefault();
          load();
        }}
      >
        <input placeholder="Name" value={filters.name} onChange={(e) => setFilters({ ...filters, name: e.target.value })} />
        <input placeholder="Location" value={filters.location} onChange={(e) => setFilters({ ...filters, location: e.target.value })} />
        <select value={filters.status} onChange={(e) => setFilters({ ...filters, status: e.target.value })}>
          <option value="">Any status</option>
          <option value="ACTIVE">ACTIVE</option>
          <option value="INACTIVE">INACTIVE</option>
        </select>
        <Button type="submit">Filter</Button>
      </form>
      {loading ? <Spinner label="Loading facilities" /> : null}
      {!loading && rows.length === 0 ? <Empty title="No facilities" text="Add a branch or widen the filters." /> : null}
      <div className="card-grid">
        {rows.map((facility) => (
          <article key={facility.id} className="plan-card">
            <div className="panel-head">
              <Badge tone={statusTone(facility.status)}>{facility.status}</Badge>
              <span className="muted">#{facility.id}</span>
            </div>
            <h2>{facility.name}</h2>
            <p>{facility.address}</p>
            <p className="muted">{facility.location} · {facility.contact}</p>
            <p className="muted">{facility.operatingHours} · capacity {facility.capacity}</p>
            <div className="row">
              <Button variant="secondary" onClick={() => setSelected(facility)}>Details</Button>
              {desk ? <Button variant="secondary" onClick={() => setForm({ mode: 'edit', ...facility })}>Edit</Button> : null}
              {desk && facility.status === 'ACTIVE' ? <Button variant="danger" onClick={() => deactivate(facility)}>Deactivate</Button> : null}
            </div>
          </article>
        ))}
      </div>
      {selected ? (
        <Modal title={selected.name} onClose={() => setSelected(null)}>
          <div className="detail">
            <p>{selected.address}, {selected.location}</p>
            <p>Contact {selected.contact}</p>
            <p>Hours {selected.operatingHours}</p>
            <p>Capacity {selected.capacity}</p>
            <Badge tone={statusTone(selected.status)}>{selected.status}</Badge>
          </div>
        </Modal>
      ) : null}
      {form ? (
        <Modal title={form.mode === 'create' ? 'New facility' : 'Edit facility'} onClose={() => setForm(null)}>
          <form className="stack" onSubmit={save}>
            <Field label="Name"><input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required /></Field>
            <Field label="Address"><input value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} required /></Field>
            <div className="split">
              <Field label="Location"><input value={form.location} onChange={(e) => setForm({ ...form, location: e.target.value })} required /></Field>
              <Field label="Contact"><input value={form.contact} onChange={(e) => setForm({ ...form, contact: e.target.value })} required /></Field>
            </div>
            <div className="split">
              <Field label="Hours"><input value={form.operatingHours} onChange={(e) => setForm({ ...form, operatingHours: e.target.value })} required /></Field>
              <Field label="Capacity"><input type="number" min="1" value={form.capacity} onChange={(e) => setForm({ ...form, capacity: e.target.value })} required /></Field>
            </div>
            <Field label="Status">
              <select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value })}>
                <option value="ACTIVE">ACTIVE</option>
                <option value="INACTIVE">INACTIVE</option>
              </select>
            </Field>
            <div className="row end">
              <Button variant="secondary" onClick={() => setForm(null)}>Cancel</Button>
              <Button type="submit" disabled={pending}>{pending ? 'Saving…' : 'Save facility'}</Button>
            </div>
          </form>
        </Modal>
      ) : null}
    </div>
  );
}
