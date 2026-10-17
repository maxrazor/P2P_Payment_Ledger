import { useMemo, useState } from 'react';
import { ShieldCheck } from 'lucide-react';
import { transferApi } from '../api/transferApi.js';
import { formatMoney } from './TransactionList.jsx';
import Modal from './Modal.jsx';

function newIdempotencyKey() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID();
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export default function TransferModal({ accounts, defaultAccount, onClose, onSuccess }) {
  const [sourceAccountId, setSourceAccountId] = useState(defaultAccount?.accountId || accounts[0]?.accountId || '');
  const [destinationEmail, setDestinationEmail] = useState('');
  const [amount, setAmount] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const source = useMemo(() => accounts.find((item) => item.accountId === sourceAccountId), [accounts, sourceAccountId]);

  async function submit(event) {
    event.preventDefault(); setBusy(true); setError('');
    try {
      const result = await transferApi.transfer({
        sourceAccountId,
        destinationEmail: destinationEmail.trim(),
        amount: Number(amount),
        currency: source?.currency,
      }, newIdempotencyKey());
      await onSuccess(result);
    } catch (requestError) { setError(requestError.message); }
    finally { setBusy(false); }
  }

  return <Modal title="New transfer" onClose={onClose}>
    <p className="modal-copy">Transfer funds securely to another Ledger user.</p>
    {accounts.length === 0 ? <div className="form-error">Create an account before sending a transfer.</div> : <form onSubmit={submit}>
      <label>From<select value={sourceAccountId} onChange={(e) => setSourceAccountId(e.target.value)} required>{accounts.map((account) => <option key={account.accountId} value={account.accountId}>{account.currency} · {formatMoney(account.balance, account.currency)}</option>)}</select></label>
      <label>Recipient email<input type="email" value={destinationEmail} onChange={(e) => setDestinationEmail(e.target.value)} placeholder="recipient@example.com" autoComplete="off" required /></label>
      <div className="two-col"><label>Amount<div className="amount-input"><span>{source?.currency || '—'}</span><input type="number" min="0.01" step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="0.00" required /></div></label><label>Currency<input value={source?.currency || ''} readOnly /></label></div>
      {error && <div className="form-error" role="alert">{error}</div>}
      <div className="idempotency-note"><ShieldCheck size={15} />A unique idempotency key is generated for each submission.</div>
      <button className="primary-btn full" disabled={busy || !source}>{busy ? 'Submitting…' : 'Send transfer'}</button>
    </form>}
  </Modal>;
}
