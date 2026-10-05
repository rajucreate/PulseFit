import { useEffect, useState } from 'react';
import { api, errorText } from '../api';
import { useAuth } from '../auth';
import { Alert, Badge, Button, Empty, Field, PageHeader, Spinner, statusTone } from '../components/ui';
import { formatDateTime, byRecent } from '../format';

export default function Attendance() {
  const { user } = useAuth();
  const desk = user?.role === 'STAFF' || user?.role === 'ADMIN';
  const [rows, setRows] = useState([]);
  const [members, setMembers] = useState([]);
  const [memberId, setMemberId] = useState(user?.memberId || '');
  const [facilityId, setFacilityId] = useState('1');
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState(null);

  async function load() {
    setLoading(true);
    setError('');
    try {
      if (user.role === 'MEMBER') {
        if (!user.memberId) {
          setRows([]);
          return;
        }
        setMemberId(user.memberId);
        setRows(await api(`/api/attendance/member/${user.memberId}`));
        return;
      }
      const [visits, memberList] = await Promise.all([api('/api/attendance'), api('/api/members')]);
      setRows(visits);
      setMembers(memberList);
      setMemberId((current) => current || memberList[0]?.id || '');
    } catch (err) {
      setError(errorText(err));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, [user]);

  async function checkIn(event) {
    event.preventDefault();
    setPending(true);
    setError('');
    setResult(null);
    try {
      const created = await api('/api/attendance/check-in', {
        method: 'POST',
        body: { memberId: Number(memberId), facilityId: Number(facilityId) },
      });
      setResult(created);
      await load();
    } catch (err) {
      setError(errorText(err));
    } finally {
      setPending(false);
    }
  }

  const ordered = byRecent(rows, 'checkInTime');

  function nameFor(id) {
    return members.find((member) => member.id === id)?.name || `Member ${id}`;
  }

  return (
    <div>
      <PageHeader
        eyebrow="Floor"
        title="Attendance"
        text="Check-in always records the attempt. Access is granted only when the member has a valid subscription."
      />
      <Alert>{error}</Alert>
      <section className="panel checkin">
        <h2>Check in</h2>
        <form onSubmit={checkIn} className="checkin-form">
          <Field label="Member">
            {desk ? (
              <select value={memberId} onChange={(e) => setMemberId(e.target.value)} required>
                {members.map((member) => (
                  <option key={member.id} value={member.id}>{member.name} (#{member.id})</option>
                ))}
              </select>
            ) : (
              <input value={user.memberId ? `Member ${user.memberId}` : 'No linked profile'} disabled />
            )}
          </Field>
          <Field label="Facility id" hint="Use the club location number.">
            <input type="number" min="1" value={facilityId} onChange={(e) => setFacilityId(e.target.value)} required />
          </Field>
          <Button type="submit" disabled={pending || (user.role === 'MEMBER' && !user.memberId) || (desk && members.length === 0)}>
            {pending ? 'Recording…' : 'Record check-in'}
          </Button>
        </form>
        {result ? (
          <div className={`result result-${result.accessStatus === 'GRANTED' ? 'good' : 'bad'}`}>
            <Badge tone={statusTone(result.accessStatus)}>{result.accessStatus}</Badge>
            <p>
              {result.accessStatus === 'GRANTED'
                ? `Door opened on subscription ${result.subscriptionId}.`
                : result.denialReason || 'Access denied.'}
            </p>
          </div>
        ) : null}
      </section>
      {loading ? <Spinner label="Loading history" /> : null}
      {!loading && rows.length === 0 ? <Empty title="No visits yet" text="The first check-in will appear here." /> : null}
      {!loading && rows.length > 0 ? (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>When</th>
                {desk ? <th>Member</th> : null}
                <th>Facility</th>
                <th>Result</th>
                <th>Note</th>
              </tr>
            </thead>
            <tbody>
              {ordered.map((row) => (
                <tr key={row.id}>
                  <td>{formatDateTime(row.checkInTime)}</td>
                  {desk ? <td>{nameFor(row.memberId)}</td> : null}
                  <td>{row.facilityId}</td>
                  <td><Badge tone={statusTone(row.accessStatus)}>{row.accessStatus}</Badge></td>
                  <td>{row.denialReason || (row.subscriptionId ? `Subscription ${row.subscriptionId}` : '—')}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </div>
  );
}
