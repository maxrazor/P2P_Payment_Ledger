import { request } from './http.js';

export const accountApi = {
  getAccounts: () => request('/accounts'),
  createAccount: (currency, email) => request('/accounts', {
    method: 'POST',
    body: JSON.stringify({ currency, email }),
  }),
  getBalance: (accountId) => request(`/accounts/${encodeURIComponent(accountId)}/balance`),
  getStatement: (accountId, { page = 0, size = 10 } = {}) => {
    const query = new URLSearchParams({ page: String(page), size: String(size) });
    return request(`/accounts/${encodeURIComponent(accountId)}/statement?${query}`);
  },
};
