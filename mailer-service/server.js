const fs = require('fs');
const path = require('path');
const express = require('express');
const nodemailer = require('nodemailer');

const envFile = path.join(__dirname, '.env');
if (fs.existsSync(envFile)) {
  for (const line of fs.readFileSync(envFile, 'utf8').split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const eq = trimmed.indexOf('=');
    if (eq < 1) continue;
    const key = trimmed.slice(0, eq).trim();
    let value = trimmed.slice(eq + 1).trim();
    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1);
    }
    if (!process.env[key]) process.env[key] = value;
  }
}

const port = Number(process.env.MAILER_PORT || 8090);
const smtpHost = (process.env.SMTP_HOST || '').trim();
const from = process.env.SMTP_FROM || 'noreply@pulsefit.local';

function createTransport() {
  if (!smtpHost) return null;
  const user = (process.env.SMTP_USERNAME || '').trim();
  const pass = process.env.SMTP_PASSWORD || '';
  return nodemailer.createTransport({
    host: smtpHost,
    port: Number(process.env.SMTP_PORT || 587),
    secure: process.env.SMTP_SECURE === 'true',
    auth: user ? { user, pass } : undefined,
  });
}

const app = express();
app.disable('x-powered-by');
app.use(express.json({ limit: '32kb' }));

app.get('/health', (_req, res) => {
  res.json({ configured: Boolean(smtpHost) });
});

app.post('/send', async (req, res) => {
  const to = typeof req.body?.to === 'string' ? req.body.to.trim() : '';
  const subject = typeof req.body?.subject === 'string' ? req.body.subject.trim() : '';
  const text = typeof req.body?.text === 'string' ? req.body.text : '';
  if (!to || !subject || !text) {
    res.status(400).json({ sent: false, message: 'Missing recipient, subject, or message.' });
    return;
  }
  const transport = createTransport();
  if (!transport) {
    res.status(503).json({ sent: false, message: 'SMTP is not configured' });
    return;
  }
  try {
    await transport.sendMail({ from, to, subject, text });
    res.json({ sent: true });
  } catch {
    res.status(502).json({ sent: false, message: 'SMTP send failed' });
  }
});

app.listen(port, '127.0.0.1', () => {
  console.log(`PulseFit mailer listening on 127.0.0.1:${port}`);
});
