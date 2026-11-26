// Token inspector: small progressive enhancements. Every page works without this file.

// ---- Times: show ISO instants in the visitor's local time
const dateTime = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
const shortDateTime = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
for (const el of document.querySelectorAll('time[data-local-time]')) {
    const date = new Date(el.dateTime);
    if (isNaN(date)) continue;
    el.textContent = (el.closest('.meta') ? shortDateTime : dateTime).format(date);
    el.title = el.dateTime;
}

// ---- Expiry: countdown, bar and Valid / Expiring / Expired badge
const meta = document.querySelector('.meta[data-expires]');
if (meta) {
    const issued = Date.parse(meta.dataset.issued);
    const expires = Date.parse(meta.dataset.expires);
    const label = meta.querySelector('[data-countdown]');
    const bar = meta.querySelector('[data-meter]');
    const badge = document.querySelector('[data-validity]');
    const tick = () => {
        const left = expires - Date.now();
        const ratio = Math.max(0, Math.min(1, left / (expires - issued || 1)));
        const minutes = Math.ceil(left / 60000);
        label.textContent = left <= 0 ? 'Expired' : minutes <= 1 ? 'in less than a minute' : `in ${minutes} min`;
        if (bar) bar.style.width = `${ratio * 100}%`;
        meta.classList.toggle('is-low', ratio < 0.2);
        meta.classList.toggle('is-expired', left <= 0);
        if (badge) {
            badge.textContent = left <= 0 ? 'Expired' : ratio < 0.2 ? 'Expiring soon' : 'Valid';
            badge.className = 'badge ' + (left <= 0 ? 'off' : ratio < 0.2 ? 'warn' : 'ok');
        }
    };
    if (!isNaN(expires)) {
        tick();
        setInterval(tick, 15000);
    }
}

// ---- Tabs (arrow keys move between them)
const tabs = [...document.querySelectorAll('[role="tab"]')];
const selectTab = (tab) => {
    for (const t of tabs) {
        const on = t === tab;
        t.setAttribute('aria-selected', String(on));
        t.tabIndex = on ? 0 : -1;
        document.getElementById(t.getAttribute('aria-controls')).hidden = !on;
    }
};
tabs.forEach((tab, i) => {
    tab.addEventListener('click', () => selectTab(tab));
    tab.addEventListener('keydown', (event) => {
        if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft') return;
        const next = tabs[(i + (event.key === 'ArrowRight' ? 1 : tabs.length - 1)) % tabs.length];
        selectTab(next);
        next.focus();
    });
});

// ---- Encoded JWT: color header . payload . signature
for (const pre of document.querySelectorAll('[data-jwt]')) {
    const parts = pre.textContent.trim().split('.');
    if (parts.length !== 3) continue;
    pre.replaceChildren(...parts.flatMap((part, i) => {
        const span = document.createElement('span');
        span.className = ['jwt-header', 'jwt-payload', 'jwt-signature'][i];
        span.textContent = part;
        return i < 2 ? [span, '.'] : [span];
    }));
}

// ---- Copy / reveal buttons
for (const button of document.querySelectorAll('[data-copy]')) {
    button.addEventListener('click', async () => {
        await navigator.clipboard.writeText(document.querySelector(button.dataset.copy).textContent.trim());
        const label = button.textContent;
        button.textContent = 'Copied';
        setTimeout(() => (button.textContent = label), 1500);
    });
}
for (const button of document.querySelectorAll('[data-reveal]')) {
    button.addEventListener('click', () => {
        const target = document.querySelector(button.dataset.reveal);
        const masked = target.classList.toggle('is-masked');
        button.textContent = masked ? 'Show' : 'Hide';
    });
}

// ---- Request console: GET /api/me with the access token
const send = document.querySelector('[data-send]');
if (send) {
    const output = document.querySelector('[data-response]');
    const statusBar = document.querySelector('[data-status]');
    const highlight = (json) => json
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/("(?:\\.|[^"\\])*")(\s*:)?|\b(-?\d+(?:\.\d+)?|true|false|null)\b/g, (match, string, colon, literal) => {
            if (string) return colon ? `<span class="j-k">${string}</span>${colon}` : `<span class="j-s">${string}</span>`;
            return `<span class="j-n">${literal}</span>`;
        });

    send.addEventListener('click', async () => {
        send.disabled = true;
        output.textContent = 'Sending…';
        const started = performance.now();
        try {
            const response = await fetch('/api/me', { headers: { Authorization: `Bearer ${send.dataset.token}` } });
            const ms = Math.round(performance.now() - started);
            const text = await response.text();
            let body;
            try {
                body = highlight(JSON.stringify(JSON.parse(text), null, 2));
            } catch {
                body = (text || response.headers.get('WWW-Authenticate') || '(empty body)')
                    .replace(/&/g, '&amp;').replace(/</g, '&lt;');
            }
            output.innerHTML = body;
            output.classList.add('has-body');
            const type = (response.headers.get('Content-Type') || '').split(';')[0];
            statusBar.innerHTML = '';
            const code = document.createElement('b');
            code.className = response.ok ? 'ok' : 'bad';
            code.textContent = `${response.status} ${response.ok ? 'OK' : response.statusText || 'Error'}`;
            statusBar.append(code);
            for (const item of [`${ms} ms`, type]) {
                if (!item) continue;
                const span = document.createElement('span');
                span.textContent = item;
                statusBar.append(span);
            }
            statusBar.hidden = false;
        } catch (error) {
            output.textContent = `Request failed: ${error.message}`;
        } finally {
            send.disabled = false;
        }
    });
}
