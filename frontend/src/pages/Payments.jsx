import { useEffect, useState } from 'react';
import { api, errorText } from '../api';
import { useAuth } from '../auth';
import { Alert, Badge, Button, Empty, Field, PageHeader, Spinner, statusTone } from '../components/ui';
import { formatDateTime, formatMoney } from '../format';

export default function Payments() {
  const { user } = useAuth();
  const desk = user?.role === 'STAFF' || user?.role === 'ADMIN';
  const [subscriptions, setSubscriptions] = useState([]);
  const [rows, setRows] = useState([]);
  const [subscriptionId, setSubscriptionId] = useState('');
  const [memberId, setMemberId] = useState(user?.memberId || '');
  const [method, setMethod] = useState('SIMULATED_CARD');
  const [idempotencyKey, setIdempotencyKey] = useState('');
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  async function load() {
    setLoading(true);
    setError('');
    try {
      const subs = user.role === 'MEMBER' && user.memberId
        ? await api(`/api/subscriptions/member/${user.memberId}`)
        : await api('/api/subscriptions');
      setSubscriptions(subs);
      setSubscriptionId((current) => current || subs[0]?.id || '');
      if (user.role === 'MEMBER') setMemberId(user.memberId || '');
      else if (subs[0]) setMemberId((current) => current || subs[0].memberId);
      const history = user.role === 'MEMBER' && user.memberId
        ? await api(`/api/payments/member/${user.memberId}`)
        : await api('/api/payments');
      setRows(history);
    } catch (err) {
      setError(errorText(err));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, [user]);

  function onSubscription(id) {
    setSubscriptionId(id);
    const match = subscriptions.find((item) => String(item.id) === String(id));
    if (match && desk) setMemberId(match.memberId);
  }

  async function pay(event) {
    event.preventDefault();
    setPending(true);
    setError('');
    setNotice('');
    try {
      const created = await api('/api/payments', {
        method: 'POST',
        body: {
          memberId: Number(memberId),
          subscriptionId: Number(subscriptionId),
          paymentMethod: method,
          idempotencyKey: idempotencyKey.trim() || null,
        },
      });
      setNotice(
        created.paymentStatus === 'SUCCESS'
          ? `Simulated payment succeeded. Reference ${created.transactionId}. Amount was taken from the plan, not this screen.`
          : `Simulated payment failed. Reference ${created.transactionId}. You can retry a failed card or UPI attempt.`
      );
      await load();
    } catch (err) {
      setError(errorText(err));
    } finally {
      setPending(false);
    }
  }

  async function retry(row) {
    setError('');
    try {
      const updated = await api(`/api/payments/${row.id}/retry`, { method: 'POST' });
      setNotice(`Retry finished as ${updated.paymentStatus}. ${updated.providerMessage}`);
      await load();
    } catch (err) {
      setError(errorText(err));
    }
  }

  async function refund(row) {
    if (!window.confirm('Mark this simulated payment as refunded?')) return;
    try {
      await api(`/api/payments/${row.id}/refund`, { method: 'POST' });
      await load();
    } catch (err) {
      setError(errorText(err));
    }
  }

  return (
    <div>
      <PageHeader
        eyebrow="Ledger"
        title="Payments"
        text={desk
          ? 'Staff can record a simulated ledger entry. Member plans are purchased on the checkout page and stay inactive until Razorpay verification.'
          : 'Membership purchases are completed on the checkout page. This list shows your payment records.'}
      />
      {desk ? <Alert tone="info">Simulated payments only. Choose SIMULATED_FAIL to record a failure without moving money.</Alert> : null}
      <Alert>{error}</Alert>
      {notice ? <Alert tone="info">{notice}</Alert> : null}
      {desk ? <section className="panel">
        <h2>Record a simulated payment</h2>
        <form className="stack" onSubmit={pay}>
          <Field label="Subscription">
            <select value={subscriptionId} onChange={(e) => onSubscription(e.target.value)} required>
              {subscriptions.map((item) => (
                <option key={item.id} value={item.id}>
                  #{item.id} · member {item.memberId} · plan {item.planId} · {item.status}
                </option>
              ))}
            </select>
          </Field>
          <div className="split">
            <Field label="Member">
              <input value={memberId || ''} onChange={(e) => setMemberId(e.target.value)} disabled={user.role === 'MEMBER'} required />
            </Field>
            <Field label="Method">
              <select value={method} onChange={(e) => setMethod(e.target.value)}>
                <option value="SIMULATED_CARD">SIMULATED_CARD</option>
                <option value="SIMULATED_UPI">SIMULATED_UPI</option>
                <option value="SIMULATED_FAIL">SIMULATED_FAIL</option>
              </select>
            </Field>
          </div>
          <Field label="Idempotency key" hint="Send the same key to safely repeat a request.">
            <input value={idempotencyKey} onChange={(e) => setIdempotencyKey(e.target.value)} placeholder="optional" />
          </Field>
          <Button type="submit" disabled={pending || !subscriptionId}>{pending ? 'Recording…' : 'Submit simulated payment'}</Button>
        </form>
      </section> : null}
      {loading ? <Spinner label="Loading payments" /> : null}
      {!loading && rows.length === 0 ? <Empty title="No payments" text="A successful or failed attempt will appear here." /> : null}
      {!loading && rows.length > 0 ? (
        <div className="table-wrap">
          <table>
            <thead>
              <tr><th>When</th><th>Member</th><th>Amount</th><th>Status</th><th>Reference</th><th /></tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id}>
                  <td>{formatDateTime(row.paymentDate)}</td>
                  <td>#{row.memberId}</td>
                  <td>{formatMoney(row.amount)}</td>
                  <td><Badge tone={statusTone(row.paymentStatus === 'SUCCESS' ? 'ACTIVE' : row.paymentStatus === 'FAILED' ? 'DENIED' : row.paymentStatus === 'REFUNDED' ? 'EXPIRED' : 'INACTIVE')}>{row.paymentStatus}</Badge></td>
                  <td>{row.transactionId}</td>
                  <td className="actions">
                    {desk && row.simulated && row.paymentStatus === 'FAILED' && row.paymentMethod !== 'SIMULATED_FAIL' ? <Button variant="secondary" onClick={() => retry(row)}>Retry</Button> : null}
                    {desk && row.paymentStatus === 'SUCCESS' ? <Button variant="danger" onClick={() => refund(row)}>Refund</Button> : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </div>
  );
}
