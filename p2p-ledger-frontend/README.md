# P2P Ledger Frontend

React + Vite frontend for the Spring P2P ledger backend. The UI is split into API modules, hooks, and feature components so individual pieces can be debugged independently.

## Run locally

```bash
npm install
cp .env.example .env
npm run dev
```

Open the Vite URL (normally `http://localhost:5173`). The default API URL is `http://localhost:8080/api/v1`; override it with `VITE_API_BASE_URL` in `.env` if needed.

## Backend assumptions used

- `POST /auth/login` returns `UserResponse` and sets the HttpOnly `access_token` cookie.
- `POST /auth/register` does not set a session cookie, so registration returns to the sign-in form.
- `GET /auth/me` restores the session after refresh.
- `GET /accounts` returns `[{ accountId, currency }]`.
- `GET /accounts/{accountId}/balance` returns a JSON number (`BigDecimal`).
- `GET /accounts/{accountId}/statement?page=0&size=10` returns `{ content, page, size, totalElements, totalPages }`.
- `POST /accounts` accepts `{ currency, email }` and returns `201` with an empty body.
- `POST /transfers` accepts `{ sourceAccountId, destinationEmail, amount, currency }` and uses `X-Idempotency-Key`.
- SSE `GET /notifications/stream` emits `connect` and `notification` events. The latter contains `{ type, transferId, message }`.

## CORS / cookies

The browser must be able to send cookies to the gateway. The gateway should return `Access-Control-Allow-Origin: http://localhost:5173` (or your exact Vite origin) and `Access-Control-Allow-Credentials: true`. Do not use `*` as the allowed origin with credentials. `EventSource` uses `{ withCredentials: true }` for the SSE stream.

For local HTTP development the provided backend cookie uses `secure(false)`. Use secure cookies and HTTPS in production.

## Manual test checklist

1. Start backend services and the gateway, then run `npm run dev`.
2. Open the app signed out; an initial `/auth/me` request is expected to restore a prior cookie session.
3. Register a user; the app should return to sign-in because registration currently does not issue a cookie.
4. Sign in; check that the browser stores `access_token` as HttpOnly and `/accounts` loads.
5. Create a wallet; verify the UI reloads accounts after the empty `201` response.
6. Select each wallet and check its balance and statement pagination.
7. Submit a transfer and confirm the `X-Idempotency-Key` header is present.
8. Verify transfer completion/rejection arrives through SSE and triggers a balance/statement refresh.
9. Refresh the browser and confirm `/auth/me` restores the session; sign out and confirm protected data clears.

## Notes

- No demo data is substituted when requests fail; errors remain visible with retry actions.
- The frontend expects `AccountResponse.accountId`, not `id`.
- Ledger rows use `entryType`, signed `amount`, `createdAt`, `currency`, and `transactionId` from `LedgerEntryResponse`.
- If your gateway authentication filter rejects `/notifications/stream` on reconnect, inspect its SSE handling and gateway timeout configuration.
