import { useEffect, useMemo, useState } from 'react';
import { api, errorText } from '../api';
import { useAuth } from '../auth';
import { Alert, Badge, Button, Empty, Field, PageHeader, Spinner, statusTone } from '../components/ui';
import { byRecent, formatDate, formatDateTime, todayISO } from '../format';

function dayKey(value) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value).slice(0, 10);
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

function shiftDay(iso, delta) {
  const date = new Date(`${iso}T00:00:00`);
  date.setDate(date.getDate() + delta);
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

function streakFrom(days, today) {
  const present = new Set(days);
  let cursor = present.has(today) ? today : shiftDay(today, -1);
  if (!present.has(cursor)) return 0;
  let count = 0;
  while (present.has(cursor)) {
    count += 1;
    cursor = shiftDay(cursor, -1);
  }
  return count;
}

function monthCells(month, marked) {
  const [year, monthNumber] = month.split('-').map(Number);
  const first = new Date(year, monthNumber - 1, 1);
  const blanks = first.getDay();
  const days = new Date(year, monthNumber, 0).getDate();
  const cells = Array.from({ length: blanks }, () => null);
  for (let day = 1; day <= days; day += 1) {
    const key = `${month}-${String(day).padStart(2, '0')}`;
    cells.push({ day, key, marked: marked.has(key) });
  }
  return cells;
}

export default function Attendance() {
  const { user } = useAuth();
  const desk = user?.role === 'STAFF' || user?.role === 'ADMIN';
  const [rows, setRows] = useState([]);
  const [members, setMembers] = useState([]);
  const [facilities, setFacilities] = useState([]);
  const [validity, setValidity] = useState(null);
  const [subscription, setSubscription] = useState(null);
  const [plans, setPlans] = useState([]);
  const [memberId, setMemberId] = useState(user?.memberId || '');
  const [facilityId, setFacilityId] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [filterFacility, setFilterFacility] = useState('');
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState(null);

  async function load() {
    setLoading(true);
    setError('');
    try {
      const facilityRows = await api('/api/facilities').catch(() => []);
      setFacilities(facilityRows);
      setFacilityId((current) => current || facilityRows.find((item) => item.status === 'ACTIVE')?.id || facilityRows[0]?.id || '');
      if (user.role === 'MEMBER') {
        if (!user.memberId) {
          setRows([]);
          return;
        }
        setMemberId(user.memberId);
        const [visits, valid, subs, planList] = await Promise.all([
          api(`/api/attendance/member/${user.memberId}`),
          api(`/api/subscriptions/member/${user.memberId}/valid`),
          api(`/api/subscriptions/member/${user.memberId}`),
          api('/api/plans'),
        ]);
        setRows(visits);
        setValidity(valid);
        setPlans(planList);
        const today = todayISO();
        setSubscription(subs.find((row) => row.status === 'ACTIVE' && String(row.expiryDate) >= today) || null);
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
    if (pending) return;
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

  const facilityName = (id) => facilities.find((facility) => String(facility.id) === String(id))?.name || `Facility ${id}`;
  const memberName = (id) => members.find((member) => member.id === id)?.name || `Member ${id}`;
  const today = todayISO();
  const month = today.slice(0, 7);

  const filtered = useMemo(() => {
    return byRecent(rows, 'checkInTime').filter((row) => {
      const key = dayKey(row.checkInTime);
      if (from && key < from) return false;
      if (to && key > to) return false;
      if (filterFacility && String(row.facilityId) !== String(filterFacility)) return false;
      return true;
    });
  }, [rows, from, to, filterFacility]);

  const granted = rows.filter((row) => row.accessStatus === 'GRANTED');
  const grantedDays = [...new Set(granted.map((row) => dayKey(row.checkInTime)))];
  const monthVisits = granted.filter((row) => dayKey(row.checkInTime).startsWith(month));
  const todayVisit = rows.find((row) => dayKey(row.checkInTime) === today);
  const cells = monthCells(month, new Set(grantedDays.filter((day) => day.startsWith(month))));
  const pageSize = 8;
  const visible = filtered.slice(page * pageSize, page * pageSize + pageSize);
  const todayRows = rows.filter((row) => dayKey(row.checkInTime) === today);
  const facilityCounts = facilities.map((facility) => ({
    name: facility.name,
    count: todayRows.filter((row) => String(row.facilityId) === String(facility.id) && row.accessStatus === 'GRANTED').length,
  }));
  const daily = grantedDays
    .filter((day) => day.startsWith(month))
    .reduce((map, day) => map.set(day, (map.get(day) || 0) + granted.filter((row) => dayKey(row.checkInTime) === day).length), new Map());

  return (
    <div>
      <PageHeader
        eyebrow="Floor"
        title="Attendance"
        text="Check-in records every attempt. Access is granted only when the attendance service finds a valid membership."
      />
      <Alert>{error}</Alert>
      {!desk ? (
        <section className="stat-grid four">
          <article className="stat"><p>Today</p><strong>{todayVisit ? todayVisit.accessStatus : 'Not yet'}</strong></article>
          <article className="stat"><p>This month</p><strong>{monthVisits.length}</strong><span>granted visits</span></article>
          <article className="stat"><p>All visits</p><strong>{granted.length}</strong></article>
          <article className="stat"><p>Streak</p><strong>{streakFrom(grantedDays, today)}</strong><span>consecutive days</span></article>
        </section>
      ) : (
        <section className="stat-grid four">
          <article className="stat"><p>Today</p><strong>{todayRows.length}</strong></article>
          <article className="stat"><p>Granted today</p><strong>{todayRows.filter((row) => row.accessStatus === 'GRANTED').length}</strong></article>
          <article className="stat"><p>Denied today</p><strong>{todayRows.filter((row) => row.accessStatus === 'DENIED').length}</strong></article>
          <article className="stat"><p>This month</p><strong>{monthVisits.length}</strong><span>granted</span></article>
        </section>
      )}
      {!desk && subscription ? (
        <section className="panel">
          <h2>Membership</h2>
          <p>
            {plans.find((plan) => plan.id === subscription.planId)?.planName || `Plan ${subscription.planId}`} · {formatDate(subscription.startDate)} to {formatDate(subscription.expiryDate)} · {Math.max(0, Math.round((new Date(`${subscription.expiryDate}T00:00:00`) - new Date(`${today}T00:00:00`)) / 86400000))} days remaining. {validity?.valid ? 'Check-in is eligible.' : 'The attendance service will deny check-in until the membership is valid.'}
          </p>
        </section>
      ) : null}
      {!desk && !loading && user.memberId && !subscription ? (
        <section className="panel">
          <h2>No current membership</h2>
          <p>An expired or missing membership is rejected at check-in. Buy a plan after the previous one expires.</p>
        </section>
      ) : null}
      <section className="panel checkin">
        <h2>Check in</h2>
        <form onSubmit={checkIn} className="checkin-form">
          <Field label="Member">
            {desk ? (
              <select value={memberId} onChange={(e) => setMemberId(e.target.value)} required>
                {members.map((member) => <option key={member.id} value={member.id}>{member.name}</option>)}
              </select>
            ) : (
              <input value={user.memberId ? `Member ${user.memberId}` : 'No linked profile'} disabled />
            )}
          </Field>
          <Field label="Facility">
            <select value={facilityId} onChange={(e) => setFacilityId(e.target.value)} required>
              {facilities.map((facility) => (
                <option key={facility.id} value={facility.id}>{facility.name} · {facility.status}</option>
              ))}
            </select>
          </Field>
          <Button type="submit" disabled={pending || !facilityId || (user.role === 'MEMBER' && !user.memberId)}>
            {pending ? 'Recording…' : 'Check in'}
          </Button>
        </form>
        {result ? (
          <div className={`result result-${result.accessStatus === 'GRANTED' ? 'good' : 'bad'}`}>
            <Badge tone={statusTone(result.accessStatus)}>{result.accessStatus}</Badge>
            <p>
              {result.accessStatus === 'GRANTED'
                ? `Checked in at ${facilityName(result.facilityId)} on subscription ${result.subscriptionId}.`
                : result.denialReason || 'Access denied.'}
            </p>
          </div>
        ) : null}
      </section>
      {!desk ? (
        <section className="panel">
          <h2>This month</h2>
          <div className="calendar">
            {['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].map((label) => <span key={label} className="cal-label">{label}</span>)}
            {cells.map((cell, index) => cell ? (
              <span key={cell.key} className={cell.marked ? 'cal-day marked' : 'cal-day'}>{cell.day}</span>
            ) : <span key={`blank-${index}`} />)}
          </div>
        </section>
      ) : (
        <section className="card-grid">
          <section className="panel">
            <h2>Today by facility</h2>
            <div className="bars">
              {facilityCounts.map((row) => (
                <div className="bar-row" key={row.name}>
                  <span>{row.name}</span>
                  <div className="bar-track"><div className="bar-fill" style={{ width: `${Math.max(8, row.count * 18)}px` }} /></div>
                  <strong>{row.count}</strong>
                </div>
              ))}
            </div>
          </section>
          <section className="panel">
            <h2>Granted days this month</h2>
            <div className="bars">
              {[...daily.entries()].slice(-8).map(([day, count]) => (
                <div className="bar-row" key={day}>
                  <span>{day.slice(8)}</span>
                  <div className="bar-track"><div className="bar-fill" style={{ width: `${Math.max(8, count * 18)}px` }} /></div>
                  <strong>{count}</strong>
                </div>
              ))}
            </div>
          </section>
        </section>
      )}
      <form className="toolbar split" onSubmit={(event) => { event.preventDefault(); setPage(0); }}>
        <input type="date" value={from} onChange={(e) => { setFrom(e.target.value); setPage(0); }} />
        <input type="date" value={to} onChange={(e) => { setTo(e.target.value); setPage(0); }} />
        <select value={filterFacility} onChange={(e) => { setFilterFacility(e.target.value); setPage(0); }}>
          <option value="">All facilities</option>
          {facilities.map((facility) => <option key={facility.id} value={facility.id}>{facility.name}</option>)}
        </select>
      </form>
      {loading ? <Spinner label="Loading history" /> : null}
      {!loading && filtered.length === 0 ? <Empty title="No visits in this filter" text="Check in, or widen the dates." /> : null}
      {!loading && filtered.length > 0 ? (
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
              {visible.map((row) => (
                <tr key={row.id}>
                  <td>{formatDateTime(row.checkInTime)}</td>
                  {desk ? <td>{memberName(row.memberId)}</td> : null}
                  <td>{facilityName(row.facilityId)}</td>
                  <td><Badge tone={statusTone(row.accessStatus)}>{row.accessStatus}</Badge></td>
                  <td>{row.denialReason || (row.subscriptionId ? `Subscription ${row.subscriptionId}` : '—')}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="row">
            <Button variant="secondary" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>Previous</Button>
            <span className="muted">Page {page + 1}</span>
            <Button variant="secondary" disabled={(page + 1) * pageSize >= filtered.length} onClick={() => setPage((value) => value + 1)}>Next</Button>
          </div>
        </div>
      ) : null}
    </div>
  );
}
