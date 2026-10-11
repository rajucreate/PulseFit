import { useEffect, useState } from 'react';
import { api, errorText } from '../api';
import { useAuth } from '../auth';
import { Alert, Badge, Button, Empty, Field, Modal, PageHeader, Spinner, statusTone } from '../components/ui';

const EMPTY = {
  name: '',
  specialization: '',
  experienceYears: 1,
  facilityId: '',
  availability: 'Weekday mornings',
  status: 'ACTIVE',
};

export default function Trainers() {
  const { user } = useAuth();
  const desk = user?.role === 'STAFF' || user?.role === 'ADMIN';
  const [rows, setRows] = useState([]);
  const [facilities, setFacilities] = useState([]);
  const [members, setMembers] = useState([]);
  const [filters, setFilters] = useState({ facilityId: '', specialization: '', status: '' });
  const [form, setForm] = useState(null);
  const [selected, setSelected] = useState(null);
  const [assigned, setAssigned] = useState([]);
  const [memberId, setMemberId] = useState('');
  const [mine, setMine] = useState([]);
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');

  async function load(next = filters) {
    setLoading(true);
    setError('');
    const params = new URLSearchParams();
    if (next.facilityId) params.set('facilityId', next.facilityId);
    if (next.specialization) params.set('specialization', next.specialization);
    if (next.status) params.set('status', next.status);
    const query = params.toString();
    try {
      const [trainerRows, facilityRows] = await Promise.all([
        api(`/api/trainers${query ? `?${query}` : ''}`),
        api('/api/facilities'),
      ]);
      setRows(trainerRows);
      setFacilities(facilityRows);
      if (desk) setMembers(await api('/api/members'));
      if (user.role === 'MEMBER' && user.memberId) {
        setMine(await api(`/api/trainers/member/${user.memberId}`));
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

  async function openTrainer(trainer) {
    setSelected(trainer);
    setAssigned([]);
    if (!desk) return;
    try {
      setAssigned(await api(`/api/trainers/${trainer.id}/members`));
    } catch (err) {
      setError(errorText(err));
    }
  }

  async function save(event) {
    event.preventDefault();
    setPending(true);
    const body = { ...form, experienceYears: Number(form.experienceYears), facilityId: Number(form.facilityId) };
    try {
      if (form.mode === 'create') await api('/api/trainers', { method: 'POST', body });
      else await api(`/api/trainers/${form.id}`, { method: 'PUT', body });
      setForm(null);
      await load();
    } catch (err) {
      setError(errorText(err));
    } finally {
      setPending(false);
    }
  }

  async function assign(event) {
    event.preventDefault();
    try {
      await api(`/api/trainers/${selected.id}/members/${memberId}`, { method: 'POST' });
      setAssigned(await api(`/api/trainers/${selected.id}/members`));
      setMemberId('');
    } catch (err) {
      setError(errorText(err));
    }
  }

  async function unassign(assignment) {
    try {
      await api(`/api/trainers/${assignment.trainerId}/members/${assignment.memberId}`, { method: 'DELETE' });
      setAssigned(await api(`/api/trainers/${selected.id}/members`));
    } catch (err) {
      setError(errorText(err));
    }
  }

  function facilityName(id) {
    return facilities.find((facility) => facility.id === id)?.name || `Facility ${id}`;
  }

  return (
    <div>
      <PageHeader
        eyebrow="Coaching"
        title="Trainers"
        text="Trainers belong to a facility. Assignments point at real member records."
        actions={desk ? <Button onClick={() => setForm({ mode: 'create', ...EMPTY, facilityId: facilities.find((item) => item.status === 'ACTIVE')?.id || '' })}>Add trainer</Button> : null}
      />
      <Alert>{error}</Alert>
      {user.role === 'MEMBER' ? (
        <section className="panel">
          <h2>Your trainer</h2>
          {mine.length === 0 ? <p className="muted">No trainer is assigned yet.</p> : mine.map((item) => <p key={item.id}>Trainer #{item.trainerId}</p>)}
        </section>
      ) : null}
      <form className="toolbar split" onSubmit={(event) => { event.preventDefault(); load(); }}>
        <select value={filters.facilityId} onChange={(e) => setFilters({ ...filters, facilityId: e.target.value })}>
          <option value="">All facilities</option>
          {facilities.map((facility) => <option key={facility.id} value={facility.id}>{facility.name}</option>)}
        </select>
        <input placeholder="Specialization" value={filters.specialization} onChange={(e) => setFilters({ ...filters, specialization: e.target.value })} />
        <select value={filters.status} onChange={(e) => setFilters({ ...filters, status: e.target.value })}>
          <option value="">Any status</option>
          <option>ACTIVE</option>
          <option>INACTIVE</option>
        </select>
        <Button type="submit">Filter</Button>
      </form>
      {loading ? <Spinner label="Loading trainers" /> : null}
      {!loading && rows.length === 0 ? <Empty title="No trainers" text="Add a trainer for an active facility." /> : null}
      <div className="card-grid">
        {rows.map((trainer) => (
          <article key={trainer.id} className="plan-card">
            <div className="panel-head">
              <Badge tone={statusTone(trainer.status)}>{trainer.status}</Badge>
              <span className="muted">#{trainer.id}</span>
            </div>
            <h2>{trainer.name}</h2>
            <p>{trainer.specialization} · {trainer.experienceYears} years</p>
            <p className="muted">{facilityName(trainer.facilityId)}</p>
            <p className="muted">{trainer.availability}</p>
            <div className="row">
              <Button variant="secondary" onClick={() => openTrainer(trainer)}>Profile</Button>
              {desk ? <Button variant="secondary" onClick={() => setForm({ mode: 'edit', ...trainer })}>Edit</Button> : null}
              {desk && trainer.status === 'ACTIVE' ? <Button variant="danger" onClick={async () => { await api(`/api/trainers/${trainer.id}`, { method: 'DELETE' }); await load(); }}>Deactivate</Button> : null}
            </div>
          </article>
        ))}
      </div>
      {selected ? (
        <Modal title={selected.name} onClose={() => setSelected(null)}>
          <div className="detail">
            <p>{selected.specialization} · {facilityName(selected.facilityId)}</p>
            <p>{selected.availability}</p>
            {desk ? (
              <>
                <h2>Assigned members</h2>
                {assigned.length === 0 ? <p className="muted">None yet.</p> : assigned.map((item) => (
                  <div className="row" key={item.id}>
                    <span>Member #{item.memberId}</span>
                    <Button variant="danger" onClick={() => unassign(item)}>Remove</Button>
                  </div>
                ))}
                <form className="row" onSubmit={assign}>
                  <select value={memberId} onChange={(e) => setMemberId(e.target.value)} required>
                    <option value="">Choose member</option>
                    {members.map((member) => <option key={member.id} value={member.id}>{member.name}</option>)}
                  </select>
                  <Button type="submit">Assign</Button>
                </form>
              </>
            ) : null}
          </div>
        </Modal>
      ) : null}
      {form ? (
        <Modal title={form.mode === 'create' ? 'New trainer' : 'Edit trainer'} onClose={() => setForm(null)}>
          <form className="stack" onSubmit={save}>
            <Field label="Name"><input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required /></Field>
            <div className="split">
              <Field label="Specialization"><input value={form.specialization} onChange={(e) => setForm({ ...form, specialization: e.target.value })} required /></Field>
              <Field label="Experience (years)"><input type="number" min="0" value={form.experienceYears} onChange={(e) => setForm({ ...form, experienceYears: e.target.value })} required /></Field>
            </div>
            <Field label="Facility">
              <select value={form.facilityId} onChange={(e) => setForm({ ...form, facilityId: e.target.value })} required>
                <option value="">Choose</option>
                {facilities.filter((facility) => facility.status === 'ACTIVE' || facility.id === form.facilityId).map((facility) => (
                  <option key={facility.id} value={facility.id}>{facility.name}</option>
                ))}
              </select>
            </Field>
            <Field label="Availability"><input value={form.availability} onChange={(e) => setForm({ ...form, availability: e.target.value })} required /></Field>
            <Field label="Status">
              <select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value })}>
                <option>ACTIVE</option>
                <option>INACTIVE</option>
              </select>
            </Field>
            <div className="row end">
              <Button variant="secondary" onClick={() => setForm(null)}>Cancel</Button>
              <Button type="submit" disabled={pending}>{pending ? 'Saving…' : 'Save trainer'}</Button>
            </div>
          </form>
        </Modal>
      ) : null}
    </div>
  );
}
