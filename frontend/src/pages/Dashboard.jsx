import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, errorText } from '../api';
import { useAuth } from '../auth';
import { Alert, Badge, PageHeader, Spinner, statusTone } from '../components/ui';
import { byRecent, displayName, formatDate, formatDateTime, isToday } from '../format';

export default function Dashboard() {
  const { user } = useAuth();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [data, setData] = useState(null);

  useEffect(() => {
    let ignore = false;
    async function load() {
      setLoading(true);
      setError('');
      try {
        const plans = await api('/api/plans');
        if (user.role === 'MEMBER') {
          if (!user.memberId) {
            if (!ignore) setData({ plans, memberView: true, unlinked: true });
            return;
          }
          const [member, subscriptions, visits, validity] = await Promise.all([
            api(`/api/members/${user.memberId}`),
            api(`/api/subscriptions/member/${user.memberId}`),
            api(`/api/attendance/member/${user.memberId}`),
            api(`/api/subscriptions/member/${user.memberId}/valid`),
          ]);
          if (!ignore) {
            setData({ plans, member, subscriptions, visits, validity, memberView: true });
          }
          return;
        }
        const [members, subscriptions, visits] = await Promise.all([
          api('/api/members'),
          api('/api/subscriptions'),
          api('/api/attendance'),
        ]);
        if (!ignore) setData({ plans, members, subscriptions, visits, memberView: false });
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
  }, [user]);

  return (
    <div>
      <PageHeader
        eyebrow="Overview"
        title={`Hello, ${displayName(user).split(' ')[0]}`}
        text="Live view of membership and floor access through the API gateway."
      />
      <Alert>{error}</Alert>
      {loading ? <Spinner label="Loading club data" /> : null}
      {!loading && data?.unlinked ? (
        <div className="panel">
          <h2>Profile not linked yet</h2>
          <p className="lede">This account has no member id. Ask the desk to attach a profile, or claim one if staff already created it.</p>
          <Link className="btn btn-primary" to="/plans">Browse plans</Link>
        </div>
      ) : null}
      {!loading && data?.memberView && !data.unlinked ? <MemberHome data={data} /> : null}
      {!loading && data && !data.memberView ? <DeskHome data={data} /> : null}
    </div>
  );
}

function MemberHome({ data }) {
  const active = (data.subscriptions || []).find((item) => item.status === 'ACTIVE');
  const recent = byRecent(data.visits, 'checkInTime').slice(0, 5);
  return (
    <>
      <section className="stat-grid">
        <article className="stat highlight">
          <p>Access today</p>
          <strong>{data.validity?.valid ? 'Ready' : 'Not valid'}</strong>
          <Badge tone={data.validity?.valid ? 'good' : 'warn'}>
            {data.validity?.valid ? `Subscription ${data.validity.subscriptionId}` : 'No active window'}
          </Badge>
        </article>
        <article className="stat">
          <p>Profile</p>
          <strong>{data.member?.name}</strong>
          <span>{data.member?.email}</span>
        </article>
        <article className="stat">
          <p>Current plan window</p>
          <strong>{active ? `${formatDate(active.startDate)} – ${formatDate(active.expiryDate)}` : 'None'}</strong>
          <span>{active ? active.status : 'Subscribe from the plans page'}</span>
        </article>
      </section>
      <section className="panel">
        <div className="panel-head">
          <h2>Recent visits</h2>
          <Link to="/attendance">Check in</Link>
        </div>
        {recent.length === 0 ? <p className="muted">No visits recorded yet.</p> : <VisitList visits={recent} />}
      </section>
    </>
  );
}

function DeskHome({ data }) {
  const members = data.members || [];
  const subs = data.subscriptions || [];
  const visits = data.visits || [];
  const todayVisits = visits.filter((item) => isToday(item.checkInTime));
  const denied = todayVisits.filter((item) => item.accessStatus === 'DENIED').length;
  const activePlans = (data.plans || []).filter((plan) => plan.active).length;
  return (
    <>
      <section className="stat-grid four">
        <article className="stat"><p>Members</p><strong>{members.length}</strong></article>
        <article className="stat"><p>Subscriptions</p><strong>{subs.filter((item) => item.status === 'ACTIVE').length}</strong><span>active</span></article>
        <article className="stat"><p>Check-ins today</p><strong>{todayVisits.length}</strong><span>{denied} denied</span></article>
        <article className="stat"><p>Live plans</p><strong>{activePlans}</strong></article>
      </section>
      <section className="panel">
        <div className="panel-head">
          <h2>Latest access log</h2>
          <Link to="/attendance">Open attendance</Link>
        </div>
        {visits.length === 0 ? <p className="muted">No check-ins yet.</p> : <VisitList visits={byRecent(visits, 'checkInTime').slice(0, 8)} showMember />}
      </section>
    </>
  );
}

function VisitList({ visits, showMember = false }) {
  return (
    <ul className="feed">
      {visits.map((visit) => (
        <li key={visit.id}>
          <div>
            <strong>{showMember ? `Member ${visit.memberId}` : 'Check-in'}</strong>
            <span>{formatDateTime(visit.checkInTime)} · Facility {visit.facilityId}</span>
          </div>
          <Badge tone={statusTone(visit.accessStatus)}>{visit.accessStatus}</Badge>
        </li>
      ))}
    </ul>
  );
}
