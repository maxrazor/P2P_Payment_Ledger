import { useCallback, useEffect, useState } from 'react';
import { accountApi } from '../api/accountApi.js';

const PAGE_SIZE = 10;

export function useLedger(user) {
  const [accounts, setAccounts] = useState([]);
  const [selectedAccountId, setSelectedAccountId] = useState('');
  const [statementPage, setStatementPage] = useState({ content: [], page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0 });
  const [accountsLoading, setAccountsLoading] = useState(false);
  const [statementLoading, setStatementLoading] = useState(false);
  const [accountsError, setAccountsError] = useState('');
  const [statementError, setStatementError] = useState('');
  const [page, setPage] = useState(0);

  const selectedAccount = accounts.find((account) => account.accountId === selectedAccountId) || null;

  const loadAccounts = useCallback(async ({ preserveSelection = true } = {}) => {
    if (!user) return;
    setAccountsLoading(true);
    setAccountsError('');
    try {
      const response = await accountApi.getAccounts();
      if (!Array.isArray(response)) throw new Error('Unexpected response from GET /accounts: expected a list.');
      const currentSelection = preserveSelection ? selectedAccountId : '';
      const withBalances = await Promise.all(response.map(async (item) => {
        if (!item?.accountId || !item?.currency) throw new Error('An account response is missing accountId or currency.');
        const balance = await accountApi.getBalance(item.accountId);
        const numericBalance = Number(typeof balance === 'object' && balance !== null ? balance.balance : balance);
        if (!Number.isFinite(numericBalance)) throw new Error(`Invalid balance returned for ${item.currency} account.`);
        return { ...item, accountId: String(item.accountId), balance: numericBalance };
      }));
      setAccounts(withBalances);
      const nextId = withBalances.some((a) => a.accountId === currentSelection)
        ? currentSelection
        : (withBalances[0]?.accountId || '');
      setSelectedAccountId(nextId);
      if (!nextId) {
        setStatementPage({ content: [], page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0 });
        setPage(0);
      }
    } catch (error) {
      setAccountsError(error.message);
    } finally {
      setAccountsLoading(false);
    }
  }, [user, selectedAccountId]);

  const loadStatement = useCallback(async (accountId = selectedAccountId, requestedPage = page) => {
    if (!user || !accountId) {
      setStatementPage({ content: [], page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0 });
      return;
    }
    setStatementLoading(true);
    setStatementError('');
    try {
      const result = await accountApi.getStatement(accountId, { page: requestedPage, size: PAGE_SIZE });
      if (!result || !Array.isArray(result.content)) throw new Error('Unexpected statement response: expected a PageResponse with a content list.');
      setStatementPage(result);
      setPage(result.page ?? requestedPage);
    } catch (error) {
      setStatementError(error.message);
    } finally {
      setStatementLoading(false);
    }
  }, [user, selectedAccountId, page]);

  useEffect(() => {
    if (user) loadAccounts({ preserveSelection: false });
    else {
      setAccounts([]); setSelectedAccountId('');
      setStatementPage({ content: [], page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0 });
    }
  }, [user]); // Intentionally run on session changes only.

  useEffect(() => {
    setPage(0);
    if (selectedAccountId) loadStatement(selectedAccountId, 0);
    else setStatementPage({ content: [], page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0 });
  }, [selectedAccountId]);

  const refresh = useCallback(async () => {
    await loadAccounts();
    if (selectedAccountId) await loadStatement(selectedAccountId, page);
  }, [loadAccounts, loadStatement, selectedAccountId, page]);

  const changePage = useCallback((nextPage) => {
    if (nextPage < 0 || (statementPage.totalPages > 0 && nextPage >= statementPage.totalPages)) return;
    loadStatement(selectedAccountId, nextPage);
  }, [loadStatement, selectedAccountId, statementPage.totalPages]);

  return {
    accounts, selectedAccount, selectedAccountId, setSelectedAccountId,
    statementPage, accountsLoading, statementLoading, accountsError, statementError,
    loadAccounts, loadStatement, refresh, changePage, page,
  };
}
