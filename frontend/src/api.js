const STORAGE = 'pulsefit.session';

let onUnauthorized = null;

export function setUnauthorizedHandler(handler) {
  onUnauthorized = handler;
}

export function readToken() {
  try {
    const raw = localStorage.getItem(STORAGE);
    if (!raw) return null;
    const session = JSON.parse(raw);
    if (!session?.accessToken) return null;
    if (session.expiresAt && session.expiresAt <= Date.now()) return null;
    return session.accessToken;
  } catch {
    return null;
  }
}

export class ApiError extends Error {
  constructor(message, status, data) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.data = data;
  }
}

export function errorText(err) {
  const fields = err?.data?.fieldErrors;
  if (fields && typeof fields === 'object') {
    const parts = Object.entries(fields).map(([key, value]) => `${key}: ${value}`);
    if (parts.length) return parts.join(' · ');
  }
  return err?.message || 'Something went wrong';
}

export async function api(path, options = {}) {
  const { method = 'GET', body, auth = true } = options;
  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth) {
    const token = readToken();
    if (token) headers.Authorization = `Bearer ${token}`;
  }

  let response;
  try {
    response = await fetch(path, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError(
      'Cannot reach the API gateway at localhost:8080. Start the PulseFit services and try again.',
      0,
      null
    );
  }

  if (response.status === 204) return null;

  const text = await response.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = { message: text };
    }
  }

  if (!response.ok) {
    if (response.status === 401 && auth && onUnauthorized) onUnauthorized();
    const message = data?.message || data?.error || `Request failed (${response.status})`;
    throw new ApiError(message, response.status, data);
  }

  return data;
}
