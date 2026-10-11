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
        title={failed ? 'Payment not completed' : 'Demo payment recorded'}
        text="This is a simulated card payment. No money was transferred, and the membership starts only after the server records the demo payment."
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
          <p>The demo checkout was cancelled or did not finish. The membership stays inactive, and you can try again.</p>
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
