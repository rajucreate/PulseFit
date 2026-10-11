import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, errorText } from '../api';
import { useAuth } from '../auth';
import { Alert, Button, Field, PageHeader, Spinner } from '../components/ui';
import { formatDate, todayISO } from '../format';

function inr(value) {
  const amount = Number(value);
  if (Number.isNaN(amount)) return '—';
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(amount);
}

function blockingSubscription(rows) {
  const today = todayISO();
  return (rows || []).find((row) => row.status === 'ACTIVE' && String(row.expiryDate) >= today) || null;
}

export default function Checkout() {
  const { planId } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [plan, setPlan] = useState(null);
  const [checkout, setCheckout] = useState(null);
  const [current, setCurrent] = useState(null);
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    let ignore = false;
    async function load() {
      setLoading(true);
      setError('');
      try {
        const nextPlan = await api(`/api/plans/${planId}`);
        let rows = [];
        if (user?.memberId) rows = await api(`/api/subscriptions/member/${user.memberId}`);
        const blocked = blockingSubscription(rows);
        if (ignore) return;
        setPlan(nextPlan);
        setCurrent(blocked);
        if (!blocked && user?.memberId && nextPlan?.active) {
          const started = await api('/api/payments/checkout', {
            method: 'POST',
            body: { planId: Number(planId) },
          });
          if (!ignore) setCheckout(started);
        }
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
  }, [planId, user]);

  async function pay(event) {
    event.preventDefault();
    if (!checkout?.paymentId || pending) return;
    setPending(true);
    setError('');
    try {
      const payment = await api('/api/payments/checkout/demo', {
        method: 'POST',
        body: { paymentId: checkout.paymentId },
      });
      navigate('/checkout/result', { state: { payment, checkout } });
    } catch (err) {
      setError(errorText(err));
      setPending(false);
    }
  }

  async function cancel() {
    if (checkout?.orderId) {
      try {
        await api('/api/payments/checkout/cancel', { method: 'POST', body: { orderId: checkout.orderId } });
      } catch {
        // Leaving checkout does not activate a membership.
      }
    }
    navigate('/plans');
  }

  return (
    <div>
      <PageHeader
        eyebrow="Checkout"
        title={plan ? plan.planName : 'Checkout'}
        text="Demo card checkout only. No card details are sent to the server, and no money is transferred."
      />
      <Alert>{error}</Alert>
      {loading ? <Spinner label="Loading checkout" /> : null}
      {!loading && current ? (
        <section className="panel">
          <h2>Membership already active</h2>
          <p>
            You already have an active membership until {formatDate(current.expiryDate)}. You can purchase another plan after it expires.
          </p>
          <Link to="/subscriptions">View membership</Link>
        </section>
      ) : null}
      {!loading && checkout && !current ? (
        <section className="panel checkout-card">
          <p className="eyebrow">Demo payment</p>
          <p className="price">{inr(checkout.amount)}</p>
          <p>{checkout.durationInDays} days · {checkout.description || plan?.description || 'Standard club access.'}</p>
          <p className="muted">Amount comes from the plan. This browser cannot change it. Status: {checkout.paymentStatus}.</p>
          <form className="stack" onSubmit={pay}>
            <Field label="Cardholder name" hint="Shown for the demo only. It is not submitted.">
              <input value="Demo Member" readOnly />
            </Field>
            <Field label="Card number" hint="Sample value. It is not sent to PulseFit.">
              <input value="4242 4242 4242 4242" readOnly />
            </Field>
            <div className="split">
              <Field label="Expiry" hint="Demo display only.">
                <input value="12 / 28" readOnly />
              </Field>
              <Field label="CVV" hint="Demo display only. Not submitted.">
                <input value="•••" readOnly />
              </Field>
            </div>
            <div className="row">
              <Button type="button" variant="secondary" onClick={cancel}>Cancel</Button>
              <Button type="submit" disabled={pending}>{pending ? 'Recording demo payment…' : 'Pay Now (Demo)'}</Button>
            </div>
          </form>
        </section>
      ) : null}
    </div>
  );
}
