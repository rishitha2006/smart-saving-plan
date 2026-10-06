import React, { useEffect, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import Chart from 'chart.js/auto';
import './styles/beautiful-base.css';
import './styles/dashboard.css';
import './styles/transactions.css';
import './styles/analytics.css';
import './styles/monthly-plan.css';
import './styles/planning.css';
import './styles/budget.css';
import './styles/auth.css';
import './styles/reports.css';
import './styles/settings.css';


window.Chart = Chart;

const ROUTES = new Set([
  '/', '/login', '/signup', '/onboarding', '/dashboard', '/transactions',
  '/analytics', '/monthly-plan', '/planning', '/budget', '/reports', '/settings', '/logout'
]);

function pageFromPath(path) {
  if (path === '/') return 'index';
  return path.replace(/^\/+/, '') || 'index';
}

function extractBody(html) {
  const match = html.match(/<body[^>]*>([\s\S]*?)<\/body>/i);
  return match ? match[1] : html;
}

function money(n) {
  return '₹' + Number(n || 0).toLocaleString('en-IN', {
    minimumFractionDigits: 2, maximumFractionDigits: 2
  });
}

function toggleTheme() {
  document.body.classList.toggle('dark');
  localStorage.setItem('sw-theme',
    document.body.classList.contains('dark') ? 'dark' : 'light');
}
window.toggleTheme = toggleTheme;

function updateLive() {
  const salary = Number(document.querySelector('[name=salary]')?.value || 0);
  const other = Number(document.querySelector('[name=otherIncome]')?.value || 0);
  const spending = document.getElementById('monthlySpending');
  const typedOverall = spending && spending.value !== '' ? Number(spending.value) : 0;
  const categoryNames = [
    'grocery','housing','travel','food','shopping','utilities',
    'education','healthcare','entertainment','other'
  ];
  const categoryTotal = categoryNames.reduce((sum, name) => {
    const value = Number(document.querySelector(`[name=${name}]`)?.value || 0);
    return sum + (Number.isFinite(value) && value >= 0 ? value : 0);
  }, 0);
  const effective = Math.max(0, typedOverall, categoryTotal);

  // Never rewrite the user's input while they type. The effective value is
  // displayed separately and the server applies the same rule on submit.
  if (spending) {
    spending.dataset.effective = String(effective);
    spending.setCustomValidity(
      Number.isFinite(typedOverall) && typedOverall >= 0 ? '' : 'Enter a valid spending amount.'
    );
  }

  const el = document.getElementById('liveBalance');
  if (el) el.textContent = money(salary + other - effective);

  const effectiveEl = document.getElementById('effectiveSpending');
  if (effectiveEl) effectiveEl.textContent = money(effective);

  const hint = document.getElementById('categoryWarning');
  if (hint) {
    hint.textContent = categoryTotal > typedOverall
      ? `Category total ${money(categoryTotal)} is higher than your overall estimate ${money(typedOverall)}. The higher category total will be used for planning.`
      : `Category total ${money(categoryTotal)} is within your overall estimate.`;
    hint.className = categoryTotal > typedOverall
      ? 'description warning-text' : 'description';
  }
}

function filterTransactions() {
  const q = (document.getElementById('transactionSearch')?.value || '').toLowerCase().trim();
  const cat = document.getElementById('transactionCategory')?.value || '';
  const source = document.getElementById('transactionSource')?.value || '';
  document.querySelectorAll('.transaction-row').forEach(row => {
    const text = row.dataset.search || '';
    const ok = (!q || text.includes(q)) &&
      (!cat || row.dataset.category === cat) &&
      (!source || row.dataset.source === source);
    row.style.display = ok ? 'grid' : 'none';
  });
}
window.filterTransactions = filterTransactions;

async function saveOCR() {
  const amount = Number(document.getElementById('ocrAmount')?.value);
  const description = document.getElementById('ocrDescription')?.value.trim();
  const category = document.getElementById('ocrCategory')?.value;
  const date = document.getElementById('ocrDate')?.value;
  if (!amount || amount <= 0 || !description) {
    alert('Please verify the extracted amount and description. Date is optional.');
    return;
  }
  const body = new URLSearchParams({ amount, description, category });
  if (date) body.set('date', date);
  const button = [...document.querySelectorAll('button')].find(b => b.getAttribute('onclick') === 'saveOCR()');
  if (button) { button.disabled = true; button.textContent = 'Saving…'; }
  try {
    const r = await fetch('/expense/ocr', {
      method: 'POST',
      headers: {'Content-Type': 'application/x-www-form-urlencoded'},
      body
    });
    const j = await r.json();
    const status = document.getElementById('ocrStatus');
    if (status) status.textContent = j.message;
    if (j.ok) setTimeout(() => window.navigateTo('/transactions'), 700);
  } catch {
    const status = document.getElementById('ocrStatus');
    if (status) status.textContent = 'Could not save. Please try again.';
  } finally {
    if (button) { button.disabled = false; button.textContent = 'Save OCR transaction'; }
  }
}
window.saveOCR = saveOCR;

function setupOCR() {
  const image = document.getElementById('ocrImage');
  if (!image || image.dataset.reactBound) return;
  image.dataset.reactBound = '1';
  image.addEventListener('change', async () => {
    const file = image.files[0];
    if (!file) return;
    const status = document.getElementById('ocrStatus');
    if (status) status.textContent = 'Reading screenshot…';
    try {
      if (status) status.textContent = 'Loading OCR…';
      const { default: Tesseract } = await import('tesseract.js');
      const result = await Tesseract.recognize(file, 'eng', {
        logger: m => {
          if (m.status === 'recognizing text' && status)
            status.textContent = `Reading screenshot… ${Math.round((m.progress || 0) * 100)}%`;
        }
      });
      const text = (result.data.text || '').replace(/\u00a0/g, ' ');
      if (status) status.textContent = 'OCR complete. Check the highlighted details before saving.';

      const amountPattern = /(?:₹|rs\.?|inr)?\s*([0-9][0-9,]*(?:\.\d{1,2})?)/gi;
      const candidates = [];
      for (const m of text.matchAll(amountPattern)) {
        const value = Number(m[1].replace(/,/g, ''));
        if (!(value > 0 && value < 100000000)) continue;
        const context = text.slice(Math.max(0, m.index - 65), Math.min(text.length, m.index + 35));
        let score = 0;
        if (/amount\s*(paid|payable|debited)?|total\s*(paid|amount)?|paid\s*amount|transaction amount/i.test(context)) score += 10;
        if (/debit(ed)?|sent|payment successful|you paid/i.test(context)) score += 6;
        if (/available balance|current balance|closing balance|opening balance|reference|ref\s*(no|id)|upi\s*id|account number/i.test(context)) score -= 12;
        if (/[₹]|\bINR\b|\bRs\.?/i.test(m[0])) score += 2;
        candidates.push({value, score, index: m.index});
      }
      candidates.sort((a,b) => b.score - a.score || a.index - b.index);
      const amount = document.getElementById('ocrAmount');
      if (candidates.length && candidates[0].score >= 0) {
        if (amount) amount.value = candidates[0].value.toFixed(2).replace(/\.00$/, '');
      } else if (status) {
        status.textContent = 'Could not confidently detect the amount. Enter it manually; other fields may still be extracted.';
      }

      let foundDate = '';
      const iso = text.match(/\b(20\d{2})[-/.](\d{1,2})[-/.](\d{1,2})\b/);
      const local = text.match(/\b(\d{1,2})[/-](\d{1,2})[/-](\d{2,4})\b/);
      if (iso) foundDate = `${iso[1]}-${iso[2].padStart(2,'0')}-${iso[3].padStart(2,'0')}`;
      else if (local) {
        let a = Number(local[1]), b = Number(local[2]), y = local[3];
        if (y.length === 2) y = '20' + y;
        let day = a, month = b;
        if (a <= 12 && b > 12) { month = a; day = b; }
        foundDate = `${y}-${String(month).padStart(2,'0')}-${String(day).padStart(2,'0')}`;
      }
      if (foundDate) {
        const d = new Date(foundDate + 'T00:00:00');
        if (!Number.isNaN(d.getTime()) && d.getFullYear() === Number(foundDate.slice(0,4))) {
          const date = document.getElementById('ocrDate');
          if (date) date.value = foundDate;
        }
      }

      const lines = text.split(/\r?\n/).map(x => x.trim()).filter(Boolean);
      const merchant = lines.find(x =>
        /[a-z]{3,}/i.test(x) &&
        !/upi|transaction|successful|reference|ref no|date|time|paid|debited|amount|balance|₹|rs\.?|inr|account|bank/i.test(x)
      );
      const desc = document.getElementById('ocrDescription');
      if (merchant && desc) desc.value = merchant.replace(/[^\w &.'-]/g, '').slice(0, 80);
      else if (desc && !desc.value) desc.value = 'OCR transaction';
    } catch {
      if (status) status.textContent = 'OCR could not read this image. Try a clear, cropped screenshot or enter details manually.';
    }
  });
}

function startVoiceExpense() {
  const Speech = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!Speech) {
    alert('Voice entry is not supported by this browser. Try Chrome.');
    return;
  }
  const r = new Speech();
  r.lang = 'en-IN';
  r.interimResults = false;
  r.onstart = () => {
    const el = document.getElementById('voiceText');
    if (el) el.textContent = 'Listening…';
  };
  r.onresult = async e => {
    const text = e.results[0][0].transcript;
    const output = document.getElementById('voiceText');
    if (output) output.textContent = 'Heard: ' + text;
    const m = text.match(/(?:₹|rs\.?|rupees)?\s*([0-9][0-9,]*(?:\.\d{1,2})?)/i);
    if (!m) {
      alert('Could not find an amount. Please say “spent 250 on lunch”.');
      return;
    }
    const amount = Number(m[1].replace(/,/g, ''));
    const desc = text.replace(m[0], '').replace(/spent|spend|on|rupees/gi, ' ').trim() || 'Cash expense';
    let category = 'Other';
    if (/food|lunch|dinner|breakfast|restaurant/i.test(desc)) category = 'Food';
    else if (/bus|uber|ola|travel|taxi|fuel/i.test(desc)) category = 'Transport';
    else if (/shop|clothes|amazon/i.test(desc)) category = 'Shopping';
    const body = new URLSearchParams({amount, description: desc, category});
    const res = await fetch('/expense/voice', {
      method: 'POST',
      headers: {'Content-Type': 'application/x-www-form-urlencoded'},
      body
    });
    const j = await res.json();
    if (output) output.textContent = j.message;
    if (j.ok) setTimeout(() => window.navigateTo('/transactions'), 500);
  };
  r.start();
}
window.startVoiceExpense = startVoiceExpense;

function runSimulator() {
  const salary = Number(document.getElementById('simSalary')?.value || 0);
  const spend = Number(document.getElementById('simSpend')?.value || 0);
  const save = Number(document.getElementById('simSave')?.value || 0);
  const baseIncome = Number(document.querySelector('.stats .stat strong')?.textContent.replace(/[^0-9.]/g,'') || 0);
  const baseSpend = Number(document.querySelectorAll('.stats .stat strong')[1]?.textContent.replace(/[^0-9.]/g,'') || 0);
  const income = baseIncome * (1 + salary / 100);
  const expense = baseSpend + spend;
  const balance = income - expense - save;
  const result = document.getElementById('simResult');
  if (result) result.innerHTML =
    `Scenario balance: <strong>${money(balance)}</strong> per month. ${balance >= 0
      ? 'This keeps you within income.'
      : 'This scenario creates a deficit of ' + money(-balance) + '.'}`;
}
window.runSimulator = runSimulator;

function downloadCsv(rows, name) {
  const csv = rows.map(r => r.map(v => '"' + String(v ?? '').replaceAll('"','""') + '"').join(',')).join('\n');
  const blob = new Blob([csv], {type:'text/csv;charset=utf-8;'});
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = name;
  a.click();
  URL.revokeObjectURL(a.href);
}
function downloadTransactionsCsv() {
  const rows = [['Date','Category','Description','Source','Amount']];
  document.querySelectorAll('#reportData [data-row]').forEach(el => {
    const parts = (el.dataset.row || '').split('|');
    if (parts.length >= 5) rows.push(parts);
  });
  downloadCsv(rows, 'Smart Saving Plan_Transactions.csv');
}
window.downloadTransactionsCsv = downloadTransactionsCsv;

function downloadMonthlySummaryCsv() {
  const cards = [...document.querySelectorAll('.report-summary .report-number')];
  const rows = [['Smart Saving Plan Monthly Financial Summary'],['Metric','Amount']];
  cards.forEach(c => {
    rows.push([
      c.querySelector('span')?.textContent?.trim() || '',
      c.querySelector('strong')?.textContent?.trim() || ''
    ]);
  });
  const insights = [...document.querySelectorAll('.insight-row b')].map(x => x.textContent.trim());
  if (insights.length) {
    rows.push([]);
    rows.push(['Financial insights']);
    insights.forEach(x => rows.push([x]));
  }
  downloadCsv(rows, 'Smart Saving Plan_Monthly_Summary.csv');
}
window.downloadMonthlySummaryCsv = downloadMonthlySummaryCsv;

function bindNotificationPanel() {
  const area = document.querySelector('.sw-notification-area');
  if (!area || area.dataset.reactBound) return;
  area.dataset.reactBound = '1';
  const bell = document.getElementById('swNotificationBell');
  const panel = document.getElementById('swNotificationPanel');
  const close = document.getElementById('swNotificationClose');
  if (bell && panel) bell.addEventListener('click', () => {
    panel.hidden = !panel.hidden;
    bell.setAttribute('aria-expanded', String(!panel.hidden));
  });
  if (close && panel) close.addEventListener('click', () => {
    panel.hidden = true;
    bell.setAttribute('aria-expanded', 'false');
  });
}

function bindLiveFields() {
  document.querySelectorAll('input').forEach(x => {
    if (!x.dataset.reactLiveBound) {
      x.dataset.reactLiveBound = '1';
      x.addEventListener('input', updateLive);
    }
  });
  const spendingInput = document.getElementById('monthlySpending');
  if (spendingInput && !spendingInput.dataset.reactSpendingBound) {
    spendingInput.dataset.reactSpendingBound = '1';
    // Do not mirror/format this field on every keystroke. The native number
    // input must keep exactly what the user entered until form submission.
    spendingInput.addEventListener('input', updateLive);
  }
  updateLive();
}

async function runRenderedInlineScripts(root) {
  // Thymeleaf returns the original page markup. Scripts inserted through
  // innerHTML do not execute automatically, so execute only the original
  // page's inline behavior after React mounts it. app.js is intentionally
  // skipped because React owns those handlers now.
  const scripts = [...root.querySelectorAll('script')];
  for (const script of scripts) {
    const src = script.getAttribute('src') || '';
    if (src === '/js/app.js' || src.includes('tesseract.js')) {
      script.remove();
      continue;
    }
    if (!script.src && script.textContent.trim()) {
      // The notification panel is already bound by React, avoiding duplicate
      // click handlers when navigating between pages.
      if (script.textContent.includes('swNotificationArea') || script.textContent.includes('swNotificationBell')) {
        script.remove();
        continue;
      }
      try {
        Function(script.textContent).call(window);
      } catch (err) {
        console.error('Smart Saving Plan page script failed:', err);
      }
      script.remove();
    }
  }
}

function bindInlineFeatures() {
  setupOCR();
  bindNotificationPanel();
  bindLiveFields();
}

function validateSignup() {
  const name = document.getElementById('name')?.value.trim() || '';
  const email = document.getElementById('email')?.value.trim() || '';
  const password = document.getElementById('password')?.value || '';
  if (name.length < 2) { alert('Please enter your full name.'); return false; }
  if (!email || !email.includes('@')) { alert('Please enter a valid email address.'); return false; }
  if (password.length < 6) { alert('Password must be at least 6 characters.'); return false; }
  return true;
}
window.validateSignup = validateSignup;

function App() {
  const [path, setPath] = useState(window.location.pathname);
  const [html, setHtml] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const rootRef = useRef(null);

  const navigateTo = (next) => {
    const clean = next || '/dashboard';
    window.history.pushState({}, '', clean);
    setPath(clean);
  };

  window.navigateTo = navigateTo;

  useEffect(() => {
    const onPop = () => setPath(window.location.pathname);
    window.addEventListener('popstate', onPop);
    return () => window.removeEventListener('popstate', onPop);
  }, []);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    setError('');
    const page = pageFromPath(path);
    fetch(`/api/render/${page}`, {credentials:'include', cache:'no-store', headers:{'Cache-Control':'no-cache'}})
      .then(async r => {
        if (r.status === 401) {
          const s = await fetch('/api/session', {credentials:'include'}).then(x => x.json());
          if (s.authenticated && (page === 'login' || page === 'signup')) {
            navigateTo(s.onboardingComplete ? '/dashboard' : '/onboarding');
            return '';
          }
          if (!s.authenticated && !['login','signup','index'].includes(page)) {
            navigateTo('/login');
            return '';
          }
        }
        if (!r.ok) throw new Error(await r.text());
        return r.text();
      })
      .then(markup => {
        if (!alive || !markup) return;
        setHtml(markup);
        setLoading(false);
      })
      .catch(e => {
        if (!alive) return;
        setError('We could not load this page. Please try again.');
        setLoading(false);
      });
    return () => { alive = false; };
  }, [path]);

  useEffect(() => {
    if (!html || !rootRef.current) return;
    const root = rootRef.current;
    root.innerHTML = html;

    runRenderedInlineScripts(root).finally(() => bindInlineFeatures());

    const onClick = (e) => {
      const link = e.target.closest?.('a[href]');
      if (!link || !root.contains(link)) return;
      const href = link.getAttribute('href');
      if (!href || href.startsWith('#') || href.startsWith('mailto:') || href.startsWith('tel:') || link.target === '_blank') return;
      let url;
      try { url = new URL(href, window.location.origin); } catch { return; }
      if (url.origin !== window.location.origin) return;
      // React owns all normal application navigation. Query strings are preserved.
      if (url.pathname === '/logout') {
        e.preventDefault();
        fetch('/logout', {credentials:'include'}).finally(() => navigateTo('/'));
        return;
      }
      const internal = url.pathname === '/' || url.pathname.startsWith('/') && !url.pathname.startsWith('/css/') && !url.pathname.startsWith('/js/') && !url.pathname.startsWith('/api/');
      if (internal) {
        e.preventDefault();
        navigateTo(url.pathname + url.search + url.hash);
      }
    };
    root.addEventListener('click', onClick);

    const forms = root.querySelectorAll('form');
    forms.forEach(form => {
      const onSubmit = async (e) => {
        const action = form.getAttribute('action') || window.location.pathname;

        // Account creation, login and financial-plan creation use native Spring
        // POSTs. This prevents React and the browser from competing over the
        // same submit event and guarantees a single request per click.
        if (action === '/signup' || action === '/login' || action === '/onboarding') {
          if (action === '/signup' && typeof window.validateSignup === 'function' && !window.validateSignup()) {
            e.preventDefault();
            return;
          }
          const submit = form.querySelector('button[type="submit"]');
          if (submit && !submit.dataset.submitting) {
            submit.dataset.submitting = '1';
            submit.disabled = true;
            submit.textContent = action === '/onboarding' ? 'Creating plan…' : action === '/signup' ? 'Creating account…' : 'Signing in…';
          }
          return;
        }

        e.preventDefault();
        if (typeof window.validateSignup === 'function' && action === '/signup' && !window.validateSignup()) return;
        const method = (form.getAttribute('method') || 'POST').toUpperCase();
        const body = new FormData(form);
        const submit = form.querySelector('button[type="submit"]');
        const originalText = submit?.textContent;
        if (submit) { submit.disabled = true; submit.textContent = 'Please wait…'; }
        try {
          const response = await fetch(action, { method, body, credentials:'include', redirect:'follow' });
          const finalPath = new URL(response.url || action, window.location.origin).pathname;
          const sessionResponse = await fetch('/api/session', {credentials:'include'});
          const session = await sessionResponse.json();

          if ((action === '/login' || action === '/signup')) {
            if (session.authenticated) {
              navigateTo(session.onboardingComplete ? '/dashboard' : '/onboarding');
              return;
            }
            const returned = await response.text();
            setHtml(extractBody(returned));
            return;
          }

          // Successful Spring redirects end up at the destination in response.url.
          // If validation failed, Spring returns the same form with an error; render it instead.
          if (response.redirected && finalPath !== action) {
            navigateTo(finalPath);
            return;
          }

          if (action === '/onboarding') {
            if (response.ok) {
              // A valid onboarding POST redirects to dashboard. A validation failure returns onboarding HTML.
              if (finalPath === '/dashboard' || response.redirected) navigateTo('/dashboard');
              else setHtml(extractBody(await response.text()));
            } else {
              setHtml(extractBody(await response.text()));
            }
          } else if (action === '/expense' || action === '/income' || action === '/expense/import') {
            navigateTo('/transactions');
          } else if (action === '/budget/update') {
            navigateTo('/budget');
          } else if (action === '/profile/update' || action === '/settings/profile') {
            navigateTo('/settings');
          } else if (finalPath && finalPath !== action) {
            navigateTo(finalPath);
          } else {
            // For same-page POSTs, refresh the rendered page so server-side values update.
            navigateTo(window.location.pathname + window.location.search);
          }
        } catch (err) {
          setError('Something went wrong while saving. Please try again.');
        } finally {
          if (submit) { submit.disabled = false; submit.textContent = originalText || 'Submit'; }
        }
      };
      form.addEventListener('submit', onSubmit);
    });

    return () => {
      root.removeEventListener('click', onClick);
      forms.forEach(form => form.replaceWith(form.cloneNode(true)));
    };
  }, [html]);

  useEffect(() => {
    const theme = localStorage.getItem('sw-theme');
    if (theme === 'dark') document.body.classList.add('dark');
    const pageClass = `page-${pageFromPath(path).replace(/[^a-z0-9-]/gi, '')}`;
    document.body.className = `${document.body.className.split(' ').filter(c => !c.startsWith('page-')).join(' ')} ${pageClass}`.trim();
    return () => document.body.classList.remove(pageClass);
  }, [path]);

  return React.createElement(
    React.Fragment,
    null,
    loading ? React.createElement('div', {className:'react-loading'}, 'Loading…') : null,
    error ? React.createElement('div', {className:'react-error'}, error) : null,
    React.createElement('div', {ref:rootRef})
  );
}

createRoot(document.getElementById('root')).render(React.createElement(App));
