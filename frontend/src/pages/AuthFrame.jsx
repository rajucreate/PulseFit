import { Link } from 'react-router-dom';

export default function AuthFrame({ title, text, children }) {
  return (
    <div className="auth-screen">
      <section className="auth-hero">
        <Link to="/login" className="brand brand-light">
          <span className="mark" aria-hidden="true" />
          <strong>PulseFit</strong>
        </Link>
        <div className="hero-copy">
          <p className="eyebrow light">Multi-facility membership</p>
          <h1>The floor, the plan, and the front desk in one place.</h1>
          <ul>
            <li>Members register once and carry a profile across facilities.</li>
            <li>Staff open plans, subscriptions, and check-in from the gateway.</li>
            <li>Every visit is recorded, granted or denied.</li>
          </ul>
        </div>
      </section>
      <section className="auth-panel">
        <div className="auth-card">
          <h2>{title}</h2>
          {text ? <p className="lede">{text}</p> : null}
          {children}
        </div>
      </section>
    </div>
  );
}
