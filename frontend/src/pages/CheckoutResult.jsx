import { Link, useLocation } from 'react-router-dom';
import { Badge, PageHeader } from '../components/ui';
import { formatDate } from '../format';

function inr(value) {
  const amount = Number(value);
  if (Number.isNaN(amount)) return '—';
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(amount);
}

export default function CheckoutResult() {
  const { state } = useLocation();
  const payment = state?.payment;
  const checkout = state?.checkout;
  const failed = state?.failed || payment?.paymentStatus === 'FAILED';

  return (
    <div>
      <PageHeader
        eyebrow="Payment"
        title={failed ? 'Payment not completed' : 'Payment verified'}
        text="This page only reflects a result the payment service verified. Closing Razorpay does not activate a membership."
      />
      {!payment && !failed ? (
        <section className="panel">
          <p>There is no verified payment in this session.</p>
          <Link to="/plans">Back to plans</Link>
        </section>
      ) : null}
      {failed ? (
        <section className="panel">
          <Badge tone="bad">Inactive</Badge>
          <h2>The membership was not activated</h2>
          <p>The test payment failed or the checkout was closed. You can try again. A failed attempt does not block a later purchase.</p>
          <p className="muted">Order {state?.orderId || payment?.transactionId || '—'}</p>
          <Link className="btn btn-primary" to="/plans">Try another checkout</Link>
        </section>
      ) : null}
      {payment && !failed ? (
        <section className="panel">
          <Badge tone="good">{payment.paymentStatus}</Badge>
          <h2>{checkout?.planName || 'Membership'}</h2>
          <p>{inr(payment.amount)} · reference {payment.transactionId}</p>
          <p className="muted">{payment.providerMessage}</p>
          {checkout ? (
            <p>
              Plan window is set when the subscription service activates the pending membership. Duration {checkout.durationInDays} days
              {checkout.subscriptionId ? ` · subscription #${checkout.subscriptionId}` : ''}.
            </p>
          ) : null}
          <div className="row">
            <Link className="btn btn-primary" to="/">Dashboard</Link>
            <Link className="btn btn-secondary" to="/subscriptions">Membership details</Link>
          </div>
          <p className="muted">Dates on the membership page are the source of truth. Today is {formatDate(new Date().toISOString())}.</p>
        </section>
      ) : null}
    </div>
  );
}
