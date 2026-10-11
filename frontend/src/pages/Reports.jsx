import { useEffect, useState } from 'react';
import { api, errorText } from '../api';
import { Alert, PageHeader, Spinner } from '../components/ui';
import { formatMoney } from '../format';

export default function Reports() {
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [facilityId, setFacilityId] = useState('');
  const [report, setReport] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  async function load(next = { from, to, facilityId }) {
    setLoading(true);
    setError('');
    const params = new URLSearchParams();
    if (next.from) params.set('from', next.from);
    if (next.to) params.set('to', next.to);
    if (next.facilityId) params.set('facilityId', next.facilityId);
    const query = params.toString();
    try {
      setReport(await api(`/api/reports/dashboard${query ? `?${query}` : ''}`));
    } catch (err) {
      setError(errorText(err));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  return (
    <div>
      <PageHeader
        eyebrow="Analytics"
        title="Reports"
        text="Figures come from the reporting service. A missing upstream service is shown as unavailable, not as zero."
      />
      <Alert>{error}</Alert>
      <form className="toolbar split" onSubmit={(event) => { event.preventDefault(); load(); }}>
        <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
        <input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
        <input type="number" min="1" placeholder="Facility id" value={facilityId} onChange={(e) => setFacilityId(e.target.value)} />
        <button className="btn btn-primary" type="submit">Apply</button>
      </form>
      {loading ? <Spinner label="Loading reports" /> : null}
      {report ? (
        <>
          <section className="stat-grid four">
            <Stat title="Members" section={report.members} value={(data) => data.total} hint={(data) => `${data.active} active`} />
            <Stat title="Subscriptions" section={report.subscriptions} value={(data) => data.active} hint={(data) => `${data.expired} expired · ${data.cancelled} cancelled`} />
            <Stat title="Attendance" section={report.attendance} value={(data) => data.today} hint={(data) => `${data.granted} granted · ${data.denied} denied`} />
            <Stat title="Revenue" section={report.revenue} value={(data) => formatMoney(data.total)} hint={(data) => `${data.successfulPayments} paid · ${data.failedPayments} failed`} />
          </section>
          <section className="card-grid">
            <Chart title="Daily revenue" section={report.revenue} rows={report.revenue?.data?.daily || []} labelKey="date" valueKey="amount" money />
            <Chart title="Attendance by facility" section={report.attendance} rows={(report.attendance?.data?.byFacility || []).map((row) => ({ label: `Facility ${row.facilityId}`, amount: row.granted }))} labelKey="label" valueKey="amount" />
          </section>
        </>
      ) : null}
    </div>
  );
}

function Stat({ title, section, value, hint }) {
  if (!section?.available) {
    return <article className="stat"><p>{title}</p><strong>Unavailable</strong><span>{section?.error || 'Service did not respond'}</span></article>;
  }
  return <article className="stat"><p>{title}</p><strong>{value(section.data)}</strong><span>{hint(section.data)}</span></article>;
}

function Chart({ title, section, rows, labelKey, valueKey, money = false }) {
  const max = Math.max(...rows.map((row) => Number(row[valueKey]) || 0), 1);
  return (
    <section className="panel">
      <h2>{title}</h2>
      {!section?.available ? <p className="muted">{section?.error || 'Unavailable'}</p> : null}
      {section?.available && rows.length === 0 ? <p className="muted">No rows in this range.</p> : null}
      <div className="bars">
        {rows.map((row) => (
          <div className="bar-row" key={row[labelKey]}>
            <span>{row[labelKey]}</span>
            <div className="bar-track"><div className="bar-fill" style={{ width: `${(Number(row[valueKey]) / max) * 100}%` }} /></div>
            <span>{money ? formatMoney(row[valueKey]) : row[valueKey]}</span>
          </div>
        ))}
      </div>
    </section>
  );
}
