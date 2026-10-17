import { request } from './http.js';

export const transferApi = {
  transfer: (payload, idempotencyKey) => request('/transfers', {
    method: 'POST',
    headers: { 'X-Idempotency-Key': idempotencyKey },
    body: JSON.stringify(payload),
  }),
};
