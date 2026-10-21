import { useCallback, useMemo, useState } from 'react';
import { ArrowLeftRight, CreditCard, CircleDollarSign, ChevronDown, LogOut, Plus, RefreshCw, Send, ShieldCheck, Wallet, X, Euro } from 'lucide-react';
import { useAuth } from './hooks/useAuth.js';
import { useLedger } from './hooks/useLedger.js';
import { useNotifications } from './hooks/useNotifications.js';
import AuthScreen from './components/AuthScreen.jsx';
import AccountModal from './components/AccountModal.jsx';
import TransferModal from './components/TransferModal.jsx';
import TransactionList, { formatMoney } from './components/TransactionList.jsx';
import Notice from './components/Notice.jsx';

const CURRENCIES = ['USD', 'EUR', 'RUB'];

export default function App() {
  const { user, loading: authLoading, sessionError, login, register, logout } = useAuth();
  const ledger = useLedger(user);
  const [active, setActive] = useState('overview');
  const [notice, setNotice] = useState(null);
  const [accountModalOpen, setAccountModalOpen] = useState(false);
  const [transferModalOpen, setTransferModalOpen] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);

  const onNotification = useCallback((notification) => {
    const completed = notification?.type === 'TRANSFER_COMPLETED';
    const rejected = notification?.type === 'TRANSFER_REJECTED';
    if (!completed && !rejected) return;
    setNotice({ type: completed ? 'success' : 'info', text: notification.message || (completed ? 'Transfer completed.' : 'Transfer rejected.') });
    void ledger.refresh();
  }, [ledger.refresh]);

  const onSseError = useCallback((error) => {
    if (error?.closed) {
      console.warn('SSE connection closed; automatic reconnection stopped.');
    } else {
      console.debug('SSE connection interrupted; reconnecting may occur.');
    }
  }, []);
  useNotifications(user, { onNotification, onError: onSseError });

  const selectedAccount = ledger.selectedAccount;
  const totals = useMemo(() => ledger.accounts.reduce((acc, account) => {
    acc[account.currency] = (acc[account.currency] || 0) + account.balance;
    return acc;
  }, {}), [ledger.accounts]);

  async function handleLogout() {
    setLoggingOut(true);
    try {
      await logout();
      setNotice(null);
      setActive('overview');
    } catch (error) {
      setNotice({ type: 'error', text: `Logout request failed: ${error.message}` });
    } finally { setLoggingOut(false); }
  }

  async function handleCreateAccount() {
    await ledger.loadAccounts({ preserveSelection: true });
    setAccountModalOpen(false);
    setNotice({ type: 'success', text: 'Account created successfully.' });
  }

  async function handleTransferSuccess(result) {
    setTransferModalOpen(false);
    setNotice({ type: 'success', text: result?.message || 'Transfer submitted. The final result will arrive as a notification.' });
    await ledger.refresh();
  }

  if (authLoading) return <div className="loading-screen"><div className="loading-mark"><ArrowLeftRight size={20} /></div><span>Restoring your session…</span></div>;
  if (!user) return <AuthScreen onLogin={login} onRegister={register} initialError={sessionError} />;

  return <div className="app-shell">
    <aside className="sidebar">
      <div className="brand"><div className="brand-mark"><ArrowLeftRight size={19} /></div><span>ledger</span></div>
      <nav className="nav">
        <NavItem active={active === 'overview'} icon={<Wallet size={18} />} label="Overview" onClick={() => setActive('overview')} />
        <NavItem active={active === 'statement'} icon={<CreditCard size={18} />} label="Statement" onClick={() => setActive('statement')} />
      </nav>
      <div className="sidebar-bottom"><div className="security-card"><ShieldCheck size={17} /><div><strong>Secure ledger</strong><span>Protected session</span></div></div>
        <button className="logout-btn" onClick={handleLogout} disabled={loggingOut}><LogOut size={17} />{loggingOut ? 'Signing out…' : 'Sign out'}</button>
      </div>
    </aside>

    <main className="main">
      <header className="topbar"><div><span className="eyebrow">PERSONAL FINANCE</span><h1>{active === 'overview' ? 'Your ledger' : 'Statement'}</h1></div>
        <div className="top-actions"><button className="icon-btn" title="Refresh accounts and statement" onClick={() => void ledger.refresh()} disabled={ledger.accountsLoading}><RefreshCw size={17} className={ledger.accountsLoading ? 'spin' : ''} /></button><div className="avatar" title={user.email}>{(user.username || user.email || 'ME').slice(0, 2).toUpperCase()}</div></div>
      </header>
      <Notice notice={notice} onClose={() => setNotice(null)} />
      {ledger.accountsError && <ErrorPanel message={ledger.accountsError} onRetry={() => void ledger.loadAccounts()} />}

      {active === 'overview' ? <>
        <section className="hero-grid">
          <div className="balance-card"><div className="balance-top"><span>Selected account balance</span><span className="balance-currency">{selectedAccount?.currency || 'NO ACCOUNT'}</span></div>
            <div className="balance-main"><strong>{selectedAccount ? formatMoney(selectedAccount.balance, selectedAccount.currency) : '—'}</strong><span>{selectedAccount ? `${selectedAccount.currency} wallet` : 'Create or select an account'}</span></div>
            <div className="balance-footer"><span>{selectedAccount ? `Account · ${selectedAccount.accountId.slice(-4)}` : 'No account selected'}</span><button onClick={() => setTransferModalOpen(true)} disabled={!ledger.accounts.length}><Send size={15} /> Send money</button></div>
          </div>
          <div className="quick-card"><span className="eyebrow">QUICK ACTION</span><h2>Move money clearly.</h2><p>Send funds to another Ledger user by email. Transfers are processed asynchronously.</p><button className="primary-btn" onClick={() => setTransferModalOpen(true)} disabled={!ledger.accounts.length}><Send size={16} /> New transfer</button></div>
        </section>

        <section className="section"><div className="section-heading"><div><span className="eyebrow">YOUR ACCOUNTS</span><h2>Accounts</h2></div><button className="secondary-btn" onClick={() => setAccountModalOpen(true)}><Plus size={16} /> Add account</button></div>
          {ledger.accountsLoading && !ledger.accounts.length ? <div className="empty-card">Loading accounts…</div> : ledger.accounts.length ? <div className="account-grid">
            {ledger.accounts.map((account) => <button key={account.accountId} className={`account-card ${account.accountId === ledger.selectedAccountId ? 'selected' : ''}`} onClick={() => ledger.setSelectedAccountId(account.accountId)}>
              <div className="account-icon">{account.currency === 'EUR' ? <Euro size={19} /> : <CircleDollarSign size={19} />}</div><div className="account-meta"><span>{account.currency} account</span><strong>{formatMoney(account.balance, account.currency)}</strong><small>Account · {account.accountId.slice(-4)}</small></div><ChevronDown size={16} className="account-chevron" /></button>)}
          </div> : <div className="empty-card"><Wallet size={22} /><span>No accounts yet.</span><button className="secondary-btn" onClick={() => setAccountModalOpen(true)}>Create one</button></div>}
          <div className="currency-strip">{CURRENCIES.map((currency) => <div key={currency} className="currency-chip"><span>{currency} total</span><strong>{formatMoney(totals[currency] || 0, currency)}</strong></div>)}</div>
        </section>

        <section className="section"><div className="section-heading"><div><span className="eyebrow">LATEST ACTIVITY</span><h2>Recent transactions</h2></div><span className="muted">{selectedAccount?.currency || ''}</span></div>
          {ledger.statementError && <ErrorPanel message={ledger.statementError} onRetry={() => void ledger.loadStatement()} />}
          <TransactionList transactions={ledger.statementPage.content.slice(0, 5)} loading={ledger.statementLoading} />
        </section>
      </> : <section className="section statement-page">
        <div className="statement-header"><div><span className="eyebrow">ACCOUNT STATEMENT</span><h2>{selectedAccount ? `${selectedAccount.currency} activity` : 'Select an account'}</h2><p>{selectedAccount ? `Account · ${selectedAccount.accountId.slice(-4)}` : 'Choose an account from Overview.'}</p></div>
          <button className="secondary-btn" onClick={() => void ledger.loadStatement()} disabled={!selectedAccount || ledger.statementLoading}><RefreshCw size={16} className={ledger.statementLoading ? 'spin' : ''} /> Refresh</button></div>
        <div className="statement-account-select"><label>Account<select value={ledger.selectedAccountId} onChange={(event) => ledger.setSelectedAccountId(event.target.value)}><option value="">Select an account</option>{ledger.accounts.map((account) => <option key={account.accountId} value={account.accountId}>{account.currency} · {formatMoney(account.balance, account.currency)}</option>)}</select></label></div>
        {ledger.statementError && <ErrorPanel message={ledger.statementError} onRetry={() => void ledger.loadStatement()} />}
        <TransactionList transactions={ledger.statementPage.content} large loading={ledger.statementLoading} />
        <div className="pagination"><span>Page {ledger.statementPage.totalPages ? ledger.statementPage.page + 1 : 0} of {ledger.statementPage.totalPages} · {ledger.statementPage.totalElements} entries</span><div><button className="secondary-btn" disabled={ledger.statementLoading || ledger.page <= 0} onClick={() => ledger.changePage(ledger.page - 1)}>Previous</button><button className="secondary-btn" disabled={ledger.statementLoading || ledger.page + 1 >= ledger.statementPage.totalPages} onClick={() => ledger.changePage(ledger.page + 1)}>Next</button></div></div>
      </section>}
    </main>

    {accountModalOpen && <AccountModal email={user.email} onClose={() => setAccountModalOpen(false)} onCreated={handleCreateAccount} />}
    {transferModalOpen && <TransferModal accounts={ledger.accounts} defaultAccount={selectedAccount} onClose={() => setTransferModalOpen(false)} onSuccess={handleTransferSuccess} />}
  </div>;
}

function NavItem({ active, icon, label, onClick }) {
  return <button className={`nav-item ${active ? 'active' : ''}`} onClick={onClick}>{icon}<span>{label}</span></button>;
}

function ErrorPanel({ message, onRetry }) {
  return <div className="error-panel"><span>{message}</span>{onRetry && <button className="secondary-btn" onClick={onRetry}>Retry</button>}</div>;
}
