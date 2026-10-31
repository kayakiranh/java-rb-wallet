'use strict';
const $ = id => document.getElementById(id);
const money = value => new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' }).format(value);
let token = '', registration = false, currentWallet = null, page = 1, bill = null, busy = false;
let pendingRequest = null, hasNext = false;
const titles = { TopUp: 'Para yükleme', Transfer: 'Cüzdan transferi', BillPayment: 'Fatura ödemesi' };
const statuses = { Completed: 'Tamamlandı', Pending: 'Bekliyor', Rejected: 'Reddedildi' };

function notify(message, error = false) {
  $('notice').hidden = false;
  $('notice').textContent = message;
  $('notice').className = error ? 'error' : '';
}
function storageKey() { return 'rb-wallet:last-request:' + currentWallet.number; }
function remember(request) {
  // Save only the request, never the JWT. Preserve its key across reloads after a lost response.
  if (request) sessionStorage.setItem(storageKey(), JSON.stringify(request));
  else sessionStorage.removeItem(storageKey());
  pendingRequest = request;
  $('retry-banner').hidden = !request;
}
function logout() {
  token = ''; currentWallet = null; pendingRequest = null; bill = null; page = 1;
  $('dashboard').hidden = true; $('logout').hidden = true; $('auth').hidden = false;
  $('password').value = ''; $('page-title').textContent = 'Hoş geldin.';
  $('transactions').replaceChildren(); $('bill-result').hidden = true;
}
async function api(path, method = 'GET', body) {
  let response;
  try {
    response = await fetch('/api' + path, { method, headers: {
      'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {})
    }, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(20000) });
  } catch { throw Object.assign(new Error('Bağlantı kurulamadı. Para işlemi yaptıysan aynı isteğin sonucunu kontrol et.'), { status: 0 }); }
  const data = await response.json().catch(() => null);
  if (!response.ok) {
    if (response.status === 401 && token) { logout(); throw new Error('Oturumun sona erdi. Tekrar giriş yap.'); }
    const validation = data?.errors ? Object.values(data.errors).flat().join(' ') : null;
    throw Object.assign(new Error(validation || data?.title || (response.status === 429 ? 'Çok fazla deneme yaptın. Bir dakika sonra tekrar dene.' : 'İşlem yapılamadı.')), { status: response.status });
  }
  return data;
}
async function perform(action) {
  if (busy) return;
  busy = true;
  const buttons = Array.from(document.querySelectorAll('button'));
  const disabled = buttons.map(b => b.disabled);
  buttons.forEach(b => b.disabled = true);
  try { await action(); } catch (e) { notify(e.message, true); }
  finally { buttons.forEach((b, i) => b.disabled = disabled[i]); busy = false;
    if (token) { $('previous').disabled = page <= 1; $('next').disabled = !hasNext; } }
}
function setAuth(register) {
  registration = register;
  $('name-label').hidden = !register; $('name').required = register;
  $('password-help').hidden = !register;
  $('password').minLength = register ? 12 : 1;
  $('password').autocomplete = register ? 'new-password' : 'current-password';
  $('auth-title').textContent = register ? 'Yeni cüzdanını oluştur' : 'Cüzdanına giriş yap';
  $('auth-submit').textContent = register ? 'Hesap oluştur →' : 'Giriş yap →';
  $('login-tab').classList.toggle('selected', !register);
  $('register-tab').classList.toggle('selected', register);
}
function renderRows(rows) {
  $('transactions').replaceChildren();
  for (const tx of rows) {
    const row = document.createElement('tr');
    const info = document.createElement('td');
    info.textContent = titles[tx.kind] || tx.kind;
    const detail = document.createElement('small');
    detail.textContent = tx.direction === 'Incoming' && tx.kind === 'Transfer' ? 'Gelen transfer' : tx.target;
    info.append(detail);
    if (tx.failureReason) { const reason = document.createElement('small'); reason.textContent = tx.failureReason; info.append(reason); }
    info.title = 'İşlem referansı: ' + tx.id;
    const date = document.createElement('td');
    date.textContent = new Date(tx.createdAt).toLocaleString('tr-TR', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
    const state = document.createElement('td'), badge = document.createElement('span');
    badge.className = 'badge ' + tx.status.toLowerCase(); badge.textContent = statuses[tx.status] || tx.status; state.append(badge);
    const amount = document.createElement('td');
    amount.className = 'right amount' + (tx.direction === 'Incoming' ? ' incoming' : '');
    amount.textContent = (tx.direction === 'Incoming' ? '+' : '−') + money(tx.amount);
    row.append(info, date, state, amount); $('transactions').append(row);
  }
  $('empty-history').hidden = rows.length !== 0;
  $('page-number').textContent = page + '. sayfa';
  hasNext = rows.length === 50;
}
async function refresh() {
  const [wallet, rows] = await Promise.all([api('/wallet'), api('/transactions?page=' + page)]);
  currentWallet = wallet;
  $('available').textContent = money(wallet.available); $('balance').textContent = money(wallet.balance);
  $('reserved').textContent = money(wallet.reserved); $('wallet-number').textContent = wallet.number;
  renderRows(rows);
}
async function showDashboard() {
  page = 1;
  await refresh();
  $('auth').hidden = true; $('dashboard').hidden = false; $('logout').hidden = false;
  $('page-title').textContent = 'Cüzdanına genel bakış';
  $('password').value = '';
  const saved = sessionStorage.getItem(storageKey());
  pendingRequest = saved ? JSON.parse(saved) : null;
  $('retry-banner').hidden = !pendingRequest;
}
async function sendSaved() {
  if (!pendingRequest) return;
  const saved = pendingRequest;
  let result;
  try { result = await api(saved.path, 'POST', saved.body); }
  catch (e) {
    if ([400, 404].includes(e.status) && currentWallet) remember(null);
    // 409 may mean a parallel replay completed. Query the original key before retrying.
    if (e.status === 409 && currentWallet) {
      const existing = await api('/transactions/by-request/' + saved.body.requestKey).catch(() => null);
      if (existing) { result = existing; }
      else {
        // An already-paid bill cannot be retried, but no financial record was created for this key.
        if (saved.path === '/bills/payments' && e.message === 'Fatura zaten ödenmiş.') remember(null);
        throw e;
      }
    } else throw e;
  }
  remember(null);
  if (result.status === 'Rejected') notify('İşlem reddedildi: ' + (result.failureReason || 'Servis onaylamadı.'), true);
  else if (result.status === 'Pending') notify('İşlemin alındı ve bekliyor. Sonuç otomatik kontrol edilecek. Referans: ' + result.id);
  else notify('İşlemin tamamlandı. Referans: ' + result.id);
  page = 1; await refresh();
}
async function submitMoney(path, body) {
  if (pendingRequest) { notify('Önce son isteğin sonucunu kontrol et. Aynı işlem ikinci kez oluşturulmayacak.', true); return; }
  remember({ path, body: { ...body, requestKey: crypto.randomUUID() } });
  await sendSaved();
}
$('login-tab').onclick = () => setAuth(false);
$('register-tab').onclick = () => setAuth(true);
$('logout').onclick = () => { logout(); notify('Güvenle çıkış yaptın.'); };
$('auth-form').onsubmit = event => { event.preventDefault(); perform(async () => {
  const body = { email: $('email').value.trim(), password: $('password').value };
  if (registration) body.name = $('name').value.trim();
  const result = await api(registration ? '/auth/register' : '/auth/login', 'POST', body);
  token = result.accessToken; await showDashboard(); notify('Cüzdanın hazır.');
}); };
for (const tab of document.querySelectorAll('[data-panel]')) tab.onclick = () => {
  document.querySelectorAll('[data-panel]').forEach(b => b.classList.toggle('selected', b === tab));
  document.querySelectorAll('[data-form]').forEach(form => form.hidden = form.dataset.form !== tab.dataset.panel);
};
$('topup-form').onsubmit = event => { event.preventDefault(); perform(() => submitMoney('/wallet/top-ups', {
  bankAccount: $('bank-account').value, amount: Number($('topup-amount').value)
})); };
$('transfer-form').onsubmit = event => { event.preventDefault(); perform(() => submitMoney('/transfers', {
  recipientWalletNumber: $('recipient').value.trim(), amount: Number($('transfer-amount').value)
})); };
$('bill-query-form').onsubmit = event => { event.preventDefault(); perform(async () => {
  bill = null; $('bill-result').hidden = true;
  bill = await api('/bills/' + encodeURIComponent($('subscriber').value.trim()));
  $('bill-description').textContent = 'Abone: ' + bill.subscriberNumber + (bill.paid ? ' · Ödendi' : ' · Ödenmemiş');
  $('bill-amount').textContent = money(bill.amount); $('bill-result').hidden = false;
  $('pay-bill').hidden = bill.paid;
}); };
$('subscriber').oninput = () => { bill = null; $('bill-result').hidden = true; };
$('pay-bill').onclick = () => perform(async () => {
  if (!bill || bill.paid) return;
  await submitMoney('/bills/payments', { subscriberNumber: bill.subscriberNumber });
  $('bill-result').hidden = true; bill = null;
});
$('retry').onclick = () => perform(sendSaved);
$('refresh').onclick = () => perform(refresh);
$('previous').onclick = () => perform(async () => { if (page > 1) page--; await refresh(); });
$('next').onclick = () => perform(async () => { page++; await refresh(); });
$('copy-number').onclick = () => perform(async () => {
  try { await navigator.clipboard.writeText(currentWallet.number); notify('Cüzdan numaran kopyalandı.'); }
  catch { notify('Kopyalama izni verilmedi. Cüzdan numaranı seçip kopyalayabilirsin.', true); }
});
