import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, errorText } from '../api';
import { useAuth } from '../auth';
import { Alert, Button, PageHeader, Spinner } from '../components/ui';
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

function loadRazorpay() {
  if (window.Razorpay) return Promise.resolve(true);
  return new Promise((resolve) => {
    const script = document.createElement('script');
    script.src = 'https://checkout.razorpay.com/v1/checkout.js';
    script.onload = () => resolve(true);
    script.onerror = () => resolve(false);
    document.body.appendChild(script);
  });
}

export default function Checkout() {
  const { planId } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [plan, setPlan] = useState(null);
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
        if (!ignore) {
          setPlan(nextPlan);
          setCurrent(blockingSubscription(rows));
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

  async function pay() {
    setPending(true);
    setError('');
    try {
      const checkout = await api('/api/payments/checkout', {
        method: 'POST',
        body: { planId: Number(planId) },
      });
      const ready = await loadRazorpay();
      if (!ready || !window.Razorpay) {
        setError('Razorpay Checkout could not be loaded. Check the network and try again.');
        setPending(false);
        return;
      }
      const razorpay = new window.Razorpay({
        key: checkout.keyId,
        amount: checkout.amountPaise,
        currency: 'INR',
        name: 'PulseFit',
        description: `${checkout.planName}. Test-mode UPI. No live transfer.`,
        order_id: checkout.orderId,
        prefill: { email: user?.email || '' },
        theme: { color: '#e85d04' },
        config: {
          display: {
            blocks: {
              upi: { name: 'Pay with UPI', instruments: [{ method: 'upi' }] },
            },
            sequence: ['block.upi'],
            preferences: { show_default_blocks: true },
          },
        },
        handler: async (response) => {
          try {
            const verified = await api('/api/payments/verify', {
              method: 'POST',
              body: {
                razorpayOrderId: response.razorpay_order_id,
                razorpayPaymentId: response.razorpay_payment_id,
                razorpaySignature: response.razorpay_signature,
              },
            });
            navigate('/checkout/result', { state: { payment: verified, checkout } });
          } catch (err) {
            setError(errorText(err));
            setPending(false);
          }
        },
        modal: {
          ondismiss: async () => {
            try {
              await api('/api/payments/checkout/cancel', {
                method: 'POST',
                body: { orderId: checkout.orderId },
              });
            } catch {
              // A pending order does not activate a membership.
            }
            setPending(false);
            setError('Checkout was closed. The membership stays inactive until payment is verified.');
          },
        },
      });
      razorpay.on('payment.failed', async (response) => {
        const orderId = response?.error?.metadata?.order_id || checkout.orderId;
        try {
          await api('/api/payments/checkout/cancel', { method: 'POST', body: { orderId } });
        } catch {
          // Failure still leaves the membership inactive.
        }
        navigate('/checkout/result', { state: { failed: true, orderId, checkout } });
      });
      razorpay.open();
    } catch (err) {
      setError(errorText(err));
      setPending(false);
    }
  }

  return (
    <div>
      <PageHeader
        eyebrow="Checkout"
        title={plan ? plan.planName : 'Checkout'}
        text="Razorpay test mode. UPI is offered in Checkout. A membership starts only after the server verifies the signature and amount."
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
      {!loading && plan && !current ? (
        <section className="panel checkout-card">
          <p className="eyebrow">Amount from the plan</p>
          <p className="price">{inr(plan.price)}</p>
          <p>{plan.durationInDays} days · {plan.description || 'Standard club access.'}</p>
          <p className="muted">Payable by Razorpay test UPI. The browser cannot change this amount.</p>
          <div className="row">
            <Link className="btn btn-secondary" to="/plans">Back to plans</Link>
            <Button onClick={pay} disabled={pending || !user?.memberId || !plan.active}>
              {pending ? 'Waiting for Razorpay…' : 'Pay with UPI (test)'}
            </Button>
          </div>
          {!user?.memberId ? <p className="muted">This account has no member profile, so checkout is unavailable.</p> : null}
          {!plan.active ? <p className="muted">This plan is not open for new memberships.</p> : null}
        </section>
      ) : null}
    </div>
  );
}
