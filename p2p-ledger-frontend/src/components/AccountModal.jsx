import { useState } from 'react';
import { accountApi } from '../api/accountApi.js';
import Modal from './Modal.jsx';

const CURRENCIES = ['EUR', 'USD', 'RUB'];

export default function AccountModal({ email, onClose, onCreated }) {
  const [currency, setCurrency] = useState('EUR');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function submit(event) {
    event.preventDefault(); setBusy(true); setError('');
    try {
      await accountApi.createAccount(currency, email);
      await onCreated(currency);
    } catch (requestError) { setError(requestError.message); }
    finally { setBusy(false); }
  }
  return <Modal title="Create account" onClose={onClose}>
    <p className="modal-copy">Add another wallet in a different currency.</p>
    <form onSubmit={submit}><label>Currency<div className="currency-options">{CURRENCIES.map((item) => <button type="button" key={item} className={`currency-option ${currency === item ? 'selected' : ''}`} onClick={() => setCurrency(item)}><span>{item === 'EUR' ? '€' : item === 'USD' ? '$' : '₽'}</span>{item}</button>)}</div></label>
      {error && <div className="form-error" role="alert">{error}</div>}
      <button className="primary-btn full" disabled={busy}>{busy ? 'Creating…' : `Create ${currency} account`}</button>
    </form>
  </Modal>;
}
