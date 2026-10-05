import { useEffect, useState } from 'react';
import { api, errorText } from '../api';
import { Alert, Badge, Empty, PageHeader, Spinner, statusTone } from '../components/ui';
import { byRecent, formatDateTime } from '../format';

export default function Audit() {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let ignore = false;
    async function load() {
      try {
        const data = await api('/api/auth/audit-logs');
        if (!ignore) setRows(data);
      } catch (err) {
        if (!ignore) setError(errorText(err));
      } finally {
        if (!ignore) setLoading(false);
      }
    }
    load();
    return () => {
      ignore = true;
    };
  }, []);

  return (
    <div>
      <PageHeader
        eyebrow="Security"
        title="Audit log"
        text="Sign-ins, registrations, role changes, and account status updates recorded by the auth service."
      />
      <Alert>{error}</Alert>
      {loading ? <Spinner label="Loading audit log" /> : null}
      {!loading && rows.length === 0 ? <Empty title="No events yet" text="Actions taken by admins will show up here." /> : null}
      {!loading && rows.length > 0 ? (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>When</th>
                <th>Action</th>
                <th>Actor</th>
                <th>Target</th>
                <th>Outcome</th>
                <th>Details</th>
              </tr>
            </thead>
            <tbody>
              {byRecent(rows, 'timestamp').map((row) => (
                <tr key={row.id}>
                  <td>{formatDateTime(row.timestamp)}</td>
                  <td>{row.action}</td>
                  <td>
                    {row.actorEmail || '—'}
                    <div className="muted">{row.actorIpAddress}</div>
                  </td>
                  <td>{row.targetEmail || (row.targetUserId ? `User ${row.targetUserId}` : '—')}</td>
                  <td><Badge tone={statusTone(row.outcome === 'SUCCESS' ? 'ACTIVE' : 'DENIED')}>{row.outcome}</Badge></td>
                  <td>{row.failureReason || row.details || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </div>
  );
}
