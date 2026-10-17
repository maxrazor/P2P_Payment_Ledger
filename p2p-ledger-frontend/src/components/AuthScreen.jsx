import { useState } from 'react';
import { ArrowLeftRight, Eye, EyeOff } from 'lucide-react';

export default function AuthScreen({ onLogin, onRegister, initialError = '' }) {
  const [mode, setMode] = useState('login');
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [info, setInfo] = useState('');

  async function submit(event) {
    event.preventDefault(); setBusy(true); setError(''); setInfo('');
    try {
      if (mode === 'login') await onLogin({ email, password });
      else {
        await onRegister({ username, email, password });
        setMode('login');
        setPassword('');
        setInfo('Registration successful. Please sign in to start your session.');
      }
    } catch (requestError) {
      setError(requestError.message || 'Request failed.');
    } finally { setBusy(false); }
  }

  return <div className="auth-shell">
    <div className="auth-visual">
      <div className="brand light-brand"><div className="brand-mark"><ArrowLeftRight size={19} /></div><span>ledger</span></div>
      <div className="visual-copy"><span className="eyebrow">P2P PAYMENT LEDGER</span><h1>Money movement,<br /><em>made clear.</em></h1><p>One place for your balances, transfers and multi-currency accounts.</p></div>
      <div className="visual-orb orb-one" /><div className="visual-orb orb-two" />
      <div className="mini-card"><div><span>LEDGER</span><strong>One clear view</strong></div><div className="mini-positive">EUR · USD · RUB</div></div>
    </div>
    <div className="auth-panel"><div className="auth-box">
      <div className="mobile-brand brand"><div className="brand-mark"><ArrowLeftRight size={19} /></div><span>ledger</span></div>
      <span className="eyebrow">{mode === 'login' ? 'WELCOME BACK' : 'GET STARTED'}</span>
      <h2>{mode === 'login' ? 'Sign in to Ledger' : 'Create your account'}</h2>
      <p className="auth-subtitle">{mode === 'login' ? 'Access your payment accounts securely.' : 'Start managing your EUR, USD and RUB accounts.'}</p>
      {(initialError || info) && <div className={`form-message ${info ? 'success' : ''}`}>{info || initialError}</div>}
      <form onSubmit={submit}>
        {mode === 'register' && <label>Username<input value={username} onChange={(e) => setUsername(e.target.value)} placeholder="Alex Morgan" autoComplete="username" required /></label>}
        <label>Email<input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" autoComplete="email" required /></label>
        <label>Password<div className="password-wrap"><input type={showPassword ? 'text' : 'password'} value={password} onChange={(e) => setPassword(e.target.value)} placeholder="At least 8 characters" minLength={8} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} required /><button type="button" aria-label={showPassword ? 'Hide password' : 'Show password'} onClick={() => setShowPassword((value) => !value)}>{showPassword ? <EyeOff size={17} /> : <Eye size={17} />}</button></div></label>
        {error && <div className="form-error" role="alert">{error}</div>}
        <button className="primary-btn full" disabled={busy}>{busy ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}</button>
      </form>
      <div className="auth-switch"><span>{mode === 'login' ? 'New to Ledger?' : 'Already have an account?'}</span><button onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError(''); setInfo(''); }}>{mode === 'login' ? 'Create account' : 'Sign in'}</button></div>
    </div></div>
  </div>;
}
