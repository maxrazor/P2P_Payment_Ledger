import { ArrowDownLeft, ArrowUpRight, CreditCard } from 'lucide-react';

export function formatMoney(value, currency = 'USD') {
  try {
    return new Intl.NumberFormat('en', { style: 'currency', currency, maximumFractionDigits: 2 }).format(Number(value ?? 0));
  } catch {
    return `${Number(value ?? 0).toFixed(2)} ${currency}`;
  }
}

export function formatDate(value) {
  if (!value) return 'Date unavailable';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'Date unavailable';
  return new Intl.DateTimeFormat('en', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }).format(date);
}

export default function TransactionList({ transactions = [], large = false, loading = false }) {
  if (loading && transactions.length === 0) return <div className="empty-card">Loading transactions…</div>;
  if (!transactions.length) return <div className="empty-card"><CreditCard size={21} /><span>No transactions on this page.</span></div>;

  return <div className={`transaction-list ${large ? 'large' : ''}`}>
    {transactions.map((tx, index) => {
      const amount = Number(tx.amount ?? 0);
      const incoming = amount >= 0;
      const currency = tx.currency || 'USD';
      return <div className="transaction-row" key={tx.id ?? `${tx.transactionId}-${tx.createdAt}-${index}`}>
        <div className={`transaction-icon ${incoming ? 'in' : 'out'}`}>{incoming ? <ArrowDownLeft size={17} /> : <ArrowUpRight size={17} />}</div>
        <div className="transaction-info">
          <strong>{tx.description || (incoming ? 'Incoming transfer' : 'Outgoing transfer')}</strong>
          <span style={{ fontSize: '13px' }}>{tx.entryType || 'LEDGER ENTRY'} · {formatDate(tx.createdAt)}</span>
        </div>
        <div className={`transaction-amount ${incoming ? 'positive' : 'negative'}`}>{incoming ? '+' : ''}{formatMoney(amount, currency)}</div>
      </div>;
    })}
  </div>;
}
