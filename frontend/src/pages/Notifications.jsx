import { useEffect, useState } from 'react';
import { api, errorText } from '../api';
import { useAuth } from '../auth';
import { Alert, Badge, Button, Empty, Modal, PageHeader, Spinner, statusTone } from '../components/ui';
import { formatDateTime } from '../format';

export default function Notifications() {
  const { user } = useAuth();
  const desk = user?.role === 'STAFF' || user?.role === 'ADMIN';
  const [rows, setRows] = useState([]);
  const [filters, setFilters] = useState({ type: '', status: '' });
  const [selected, setSelected] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  async function load(next = filters) {
    setLoading(true);
    setError('');
    try {
      if (!desk) {
        setRows(user.memberId ? await api(`/api/notifications/member/${user.memberId}`) : []);
      } else {
        const params = new URLSearchParams();
        if (next.type) params.set('type', next.type);
        if (next.status) params.set('status', next.status);
        const query = params.toString();
        setRows(await api(`/api/notifications${query ? `?${query}` : ''}`));
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

  const visible = desk
    ? rows
    : rows.filter((row) => (!filters.type || row.type === filters.type) && (!filters.status || row.status === filters.status));

  async function retry(row) {
    try {
      await api(`/api/notifications/${row.id}/retry`, { method: 'POST' });
      await load();
    } catch (err) {
      setError(errorText(err));
    }
  }

  return (
    <div>
      <PageHeader
        eyebrow="Inbox"
        title="Notifications"
        text="Development mode stores each notice and does not mark it sent unless SMTP actually delivers it."
      />
      <Alert>{error}</Alert>
      <div className="toolbar split">
        <select value={filters.type} onChange={(e) => setFilters({ ...filters, type: e.target.value })}>
          <option value="">All types</option>
          {['PAYMENT_SUCCESS', 'PAYMENT_FAILED', 'MEMBERSHIP_CREATED', 'MEMBERSHIP_EXPIRY', 'ATTENDANCE', 'GENERAL'].map((type) => (
            <option key={type}>{type}</option>
          ))}
        </select>
        <select value={filters.status} onChange={(e) => setFilters({ ...filters, status: e.target.value })}>
          <option value="">Any status</option>
          <option>PENDING</option>
          <option>SENT</option>
          <option>FAILED</option>
        </select>
        {desk ? <Button onClick={() => load()}>Apply</Button> : null}
      </div>
      {loading ? <Spinner label="Loading notifications" /> : null}
      {!loading && visible.length === 0 ? <Empty title="No notifications" text="Payment and check-in events will show up here." /> : null}
      {!loading && visible.length > 0 ? (
        <ul className="feed">
          {visible.map((row) => (
            <li key={row.id}>
              <div>
                <strong>{row.subject}</strong>
                <span>{formatDateTime(row.createdAt)} · {row.type} · {row.recipient}</span>
              </div>
              <div className="row">
                <Badge tone={statusTone(row.status === 'SENT' ? 'ACTIVE' : row.status === 'FAILED' ? 'DENIED' : 'INACTIVE')}>{row.status}</Badge>
                <Button variant="secondary" onClick={() => setSelected(row)}>Open</Button>
                {desk && row.status !== 'SENT' ? <Button variant="secondary" onClick={() => retry(row)}>Retry</Button> : null}
              </div>
            </li>
          ))}
        </ul>
      ) : null}
      {selected ? (
        <Modal title={selected.subject} onClose={() => setSelected(null)}>
          <div className="detail">
            <p>{selected.message}</p>
            <p className="muted">{selected.deliveryNote}</p>
            <p className="muted">Attempts {selected.attempts}</p>
          </div>
        </Modal>
      ) : null}
    </div>
  );
}
