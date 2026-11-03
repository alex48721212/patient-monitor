'use strict';

// 카드마다 보관하는 최근 측정값 개수 (2초 간격이면 약 2분)
const HISTORY_SIZE = 60;
// 이 시간 동안 측정값이 안 오면 카드를 흐리게 표시
const STALE_MS = 10_000;
// 서버 AlarmEvaluator.CRITICAL_MARGIN 과 같은 값
const CRITICAL_MARGIN = 0.10;

const state = {
    thresholds: null,
    patients: new Map(),   // id -> patient
    history: new Map(),    // id -> VitalSign[] (오래된 것 → 최신)
    cards: new Map(),      // id -> card element
    alarms: [],            // 미확인 알람 (최신 먼저)
    ws: null,
    reconnectDelay: 1000,
    loggingOut: false,
};

// 타일 ↔ 측정값/정상범위 매핑
const TILES = {
    heartRate:       { range: 'heartRate',       read: v => v.heartRate,       text: v => v.heartRate },
    spo2:            { range: 'spo2',            read: v => v.spo2,            text: v => v.spo2 },
    bp:              { range: 'systolicBp',      read: v => v.systolicBp,      text: v => `${v.systolicBp}/${v.diastolicBp}` },
    respiratoryRate: { range: 'respiratoryRate', read: v => v.respiratoryRate, text: v => v.respiratoryRate },
    temperature:     { range: 'temperature',     read: v => v.temperature,     text: v => v.temperature.toFixed(1) },
};

const $ = sel => document.querySelector(sel);

// ---------------------------------------------------------------- API

function csrfToken() {
    const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
    return match ? decodeURIComponent(match[1]) : '';
}

async function api(path, options = {}) {
    const res = await fetch(path, {
        headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrfToken() },
        ...options,
    });
    if (res.status === 401) {
        location.href = '/login.html';
        throw new Error('로그인이 필요합니다');
    }
    if (!res.ok) {
        let detail = res.statusText;
        try { detail = (await res.json()).detail || detail; } catch { /* 본문 없음 */ }
        throw new Error(detail);
    }
    return res.status === 204 ? null : res.json();
}

async function loadPatients() {
    const patients = await api('/api/patients?status=ADMITTED');
    const ids = new Set(patients.map(p => p.id));

    for (const id of [...state.patients.keys()]) {
        if (!ids.has(id)) removePatient(id);
    }
    await Promise.all(patients.map(async p => {
        const isNew = !state.patients.has(p.id);
        state.patients.set(p.id, p);
        if (isNew) {
            const recent = await api(`/api/patients/${p.id}/vitals?limit=${HISTORY_SIZE}`);
            state.history.set(p.id, recent.reverse());
        }
    }));

    // 병실 번호 순으로 정렬해서 배치
    const grid = $('#patient-grid');
    [...state.patients.values()]
        .sort((a, b) => a.roomNumber.localeCompare(b.roomNumber))
        .forEach(p => {
            const card = state.cards.get(p.id) || createCard(p);
            fillCardHeader(card, p);
            grid.appendChild(card);
            renderVitals(p.id);
        });
    updateSummary();
}

async function loadAlarms() {
    state.alarms = await api('/api/alarms?acknowledged=false&limit=200');
    renderAlarms();
}

async function loadAll() {
    if (!state.thresholds) {
        state.thresholds = await api('/api/thresholds');
    }
    await loadPatients();
    await loadAlarms();
}

// ---------------------------------------------------------------- WebSocket

function connect() {
    const protocol = location.protocol === 'https:' ? 'wss' : 'ws';
    const ws = new WebSocket(`${protocol}://${location.host}/ws/monitor`);
    state.ws = ws;

    ws.onopen = async () => {
        setConnection(true);
        state.reconnectDelay = 1000;
        // 끊겨 있던 동안 놓친 데이터를 다시 받아온다
        try { await loadAll(); } catch (e) { console.error(e); }
    };
    ws.onmessage = e => handleMessage(JSON.parse(e.data));
    ws.onclose = async () => {
        // 로그아웃 중이면 서버 리다이렉트(?logout)를 그대로 따른다
        if (state.loggingOut) return;
        setConnection(false);
        // 세션이 끝나 끊긴 경우라면 api 가 로그인 페이지로 보낸다
        try { await api('/api/me'); } catch (e) { console.debug(e); }
        setTimeout(connect, state.reconnectDelay);
        state.reconnectDelay = Math.min(state.reconnectDelay * 2, 15000);
    };
    ws.onerror = () => ws.close();
}

function handleMessage({ type, data }) {
    switch (type) {
        case 'vital':
            onVital(data);
            break;
        case 'alarm':
            onAlarm(data);
            break;
        case 'patients-changed':
            loadPatients().catch(console.error);
            break;
        case 'alarms-changed':
            loadAlarms().catch(console.error);
            break;
    }
}

function onVital(vital) {
    if (!state.patients.has(vital.patientId)) {
        loadPatients().catch(console.error);
        return;
    }
    const list = state.history.get(vital.patientId) || [];
    list.push(vital);
    if (list.length > HISTORY_SIZE) list.shift();
    state.history.set(vital.patientId, list);
    renderVitals(vital.patientId);
}

function onAlarm(alarm) {
    state.alarms.unshift(alarm);
    renderAlarms(alarm.id);
    if (alarm.severity === 'CRITICAL') {
        toast(`${alarm.patientName} (${alarm.roomNumber}) — ${alarm.message}`);
    }
}

function setConnection(on) {
    const el = $('#conn');
    el.textContent = on ? '실시간 연결됨' : '재연결 중…';
    el.className = `conn ${on ? 'conn-on' : 'conn-off'}`;
}

// ---------------------------------------------------------------- 환자 카드

function createCard(patient) {
    const card = $('#patient-card-template').content.firstElementChild.cloneNode(true);
    card.dataset.id = patient.id;
    card.querySelector('.discharge').addEventListener('click', () => discharge(patient.id));
    card.querySelector('.ack-all').addEventListener('click', () => ackPatient(patient.id));
    state.cards.set(patient.id, card);
    return card;
}

function fillCardHeader(card, p) {
    card.querySelector('.name').textContent = p.name;
    card.querySelector('.room').textContent = p.roomNumber;
    card.querySelector('.demo').textContent = `${age(p.birthDate)}세 ${p.gender === 'M' ? '남' : '여'}`;
    card.querySelector('.diagnosis').textContent = p.diagnosis || '진단명 없음';
}

function removePatient(id) {
    state.cards.get(id)?.remove();
    state.cards.delete(id);
    state.patients.delete(id);
    state.history.delete(id);
}

function renderVitals(patientId) {
    const card = state.cards.get(patientId);
    const list = state.history.get(patientId) || [];
    if (!card || !state.thresholds) return;

    const latest = list[list.length - 1];
    for (const [key, def] of Object.entries(TILES)) {
        const tile = card.querySelector(`[data-vital="${key}"]`);
        const range = state.thresholds[def.range];
        let level = '';
        if (latest) {
            tile.querySelector('.value').textContent = def.text(latest);
            level = severity(def.read(latest), range);
            // 혈압은 수축기/이완기 중 더 나쁜 쪽으로 표시
            if (key === 'bp') {
                level = worse(level, severity(latest.diastolicBp, state.thresholds.diastolicBp));
            }
        }
        tile.classList.toggle('warning', level === 'warning');
        tile.classList.toggle('critical', level === 'critical');
        drawSparkline(tile.querySelector('.spark'), list.map(def.read), range,
            getComputedStyle(tile).color);
    }
    card.dataset.measuredAt = latest ? latest.measuredAt : '';
    updateFreshness(card);
}

function updateFreshness(card) {
    const at = card.dataset.measuredAt;
    const el = card.querySelector('.updated');
    if (!at) {
        card.classList.add('stale');
        el.textContent = '측정 대기';
        return;
    }
    const ago = Date.now() - new Date(at).getTime();
    const stale = ago > STALE_MS;
    card.classList.toggle('stale', stale);
    el.textContent = stale
        ? `신호 없음 · 마지막 ${formatTime(at)}`
        : `${formatTime(at)} 측정`;
}

function severity(value, range) {
    if (value < range.min) return value < range.min * (1 - CRITICAL_MARGIN) ? 'critical' : 'warning';
    if (value > range.max) return value > range.max * (1 + CRITICAL_MARGIN) ? 'critical' : 'warning';
    return '';
}

function worse(a, b) {
    const rank = { '': 0, warning: 1, critical: 2 };
    return rank[a] >= rank[b] ? a : b;
}

/** 최근 값 추이를 선으로 그리고, 정상 범위는 옅은 띠로 표시 */
function drawSparkline(canvas, values, range, color) {
    const dpr = window.devicePixelRatio || 1;
    const w = canvas.clientWidth;
    const h = canvas.clientHeight;
    if (!w || !h) return;
    if (canvas.width !== w * dpr || canvas.height !== h * dpr) {
        canvas.width = w * dpr;
        canvas.height = h * dpr;
    }
    const ctx = canvas.getContext('2d');
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, w, h);
    if (values.length === 0) return;

    const pad = (range.max - range.min) * 0.35;
    const lo = Math.min(range.min - pad, ...values);
    const hi = Math.max(range.max + pad, ...values);
    const y = v => h - 2 - ((v - lo) / (hi - lo || 1)) * (h - 4);
    const step = w / (HISTORY_SIZE - 1);
    const x0 = w - (values.length - 1) * step;

    ctx.fillStyle = 'rgba(255,255,255,0.05)';
    ctx.fillRect(0, y(range.max), w, y(range.min) - y(range.max));

    ctx.strokeStyle = color;
    ctx.lineWidth = 1.5;
    ctx.lineJoin = 'round';
    ctx.beginPath();
    values.forEach((v, i) => {
        const px = x0 + i * step;
        i === 0 ? ctx.moveTo(px, y(v)) : ctx.lineTo(px, y(v));
    });
    ctx.stroke();

    const last = values[values.length - 1];
    ctx.fillStyle = color;
    ctx.beginPath();
    ctx.arc(x0 + (values.length - 1) * step, y(last), 2.5, 0, Math.PI * 2);
    ctx.fill();
}

// ---------------------------------------------------------------- 알람 패널

function renderAlarms(newAlarmId) {
    const criticalOnly = $('#critical-only').checked;
    const visible = state.alarms.filter(a => !criticalOnly || a.severity === 'CRITICAL');
    const list = $('#alarm-list');

    list.replaceChildren(...visible.map(a => {
        const li = document.createElement('li');
        li.className = `alarm ${a.severity === 'CRITICAL' ? 'critical' : ''} ${a.id === newAlarmId ? 'new' : ''}`;

        const bar = document.createElement('span');
        bar.className = 'bar';

        const body = document.createElement('div');
        const who = document.createElement('div');
        who.className = 'who';
        who.textContent = a.patientName;
        const room = document.createElement('span');
        room.textContent = a.roomNumber;
        who.appendChild(room);
        const msg = document.createElement('div');
        msg.className = 'msg';
        msg.textContent = a.message;
        const time = document.createElement('div');
        time.className = 'time';
        time.textContent = `${a.severity === 'CRITICAL' ? '위급' : '주의'} · ${formatTime(a.createdAt)}`;
        body.append(who, msg, time);

        const btn = document.createElement('button');
        btn.className = 'btn btn-small';
        btn.textContent = '확인';
        btn.addEventListener('click', () => ackAlarm(a.id));

        li.append(bar, body, btn);
        return li;
    }));
    $('#empty-alarms').hidden = visible.length > 0;

    updateCardAlarmState();
    updateSummary();
}

/** 환자 카드 테두리/배지에 미확인 알람 상태 반영 */
function updateCardAlarmState() {
    for (const [id, card] of state.cards) {
        const mine = state.alarms.filter(a => a.patientId === id);
        const critical = mine.some(a => a.severity === 'CRITICAL');
        card.classList.toggle('has-critical', critical);
        card.classList.toggle('has-warning', !critical && mine.length > 0);

        const badge = card.querySelector('.badge');
        badge.hidden = mine.length === 0;
        badge.textContent = `알람 ${mine.length}`;
        badge.classList.toggle('critical', critical);
        card.querySelector('.ack-all').hidden = mine.length === 0;
    }
}

function updateSummary() {
    $('#admitted-count').textContent = state.patients.size;
    $('#alarm-count').textContent = state.alarms.length;
    $('#critical-count').textContent = state.alarms.filter(a => a.severity === 'CRITICAL').length;
    $('#empty-patients').hidden = state.patients.size > 0;
}

function toast(text) {
    const el = document.createElement('div');
    el.className = 'toast';
    el.textContent = text;
    $('#toasts').appendChild(el);
    setTimeout(() => el.remove(), 5000);
    // 동시에 너무 많이 쌓이지 않게
    const all = $('#toasts').children;
    while (all.length > 3) all[0].remove();
}

// ---------------------------------------------------------------- 사용자 동작

async function ackAlarm(id) {
    try {
        await api(`/api/alarms/${id}/ack`, { method: 'POST' });
    } catch (e) {
        toast(`알람 확인 실패: ${e.message}`);
    }
}

async function ackPatient(patientId) {
    try {
        await api(`/api/alarms/patients/${patientId}/ack`, { method: 'POST' });
    } catch (e) {
        toast(`알람 확인 실패: ${e.message}`);
    }
}

async function discharge(id) {
    const p = state.patients.get(id);
    if (!confirm(`${p.name} 환자를 퇴원 처리할까요? 모니터링이 중단됩니다.`)) return;
    try {
        await api(`/api/patients/${id}/discharge`, { method: 'POST' });
    } catch (e) {
        toast(`퇴원 처리 실패: ${e.message}`);
    }
}

function setupRegisterDialog() {
    const dialog = $('#register-dialog');
    const form = $('#register-form');
    const error = $('#register-error');

    $('#open-register').addEventListener('click', () => {
        form.reset();
        error.hidden = true;
        dialog.showModal();
    });
    $('#cancel-register').addEventListener('click', () => dialog.close());

    form.addEventListener('submit', async e => {
        e.preventDefault();
        const body = Object.fromEntries(new FormData(form));
        try {
            await api('/api/patients', { method: 'POST', body: JSON.stringify(body) });
            dialog.close();
        } catch (err) {
            error.textContent = err.message;
            error.hidden = false;
        }
    });
}

// ---------------------------------------------------------------- 가입 승인 (관리자)

async function loadPending() {
    const users = await api('/api/admin/users/pending');
    $('#pending-count').textContent = users.length;
    $('#pending-empty').hidden = users.length > 0;
    $('#pending-list').replaceChildren(...users.map(u => {
        const li = document.createElement('li');

        const info = document.createElement('div');
        info.textContent = `${u.name} (${u.username})`;
        const sub = document.createElement('div');
        sub.className = 'sub';
        sub.textContent = u.email;
        info.appendChild(sub);

        const actions = document.createElement('div');
        actions.className = 'actions';
        const approve = document.createElement('button');
        approve.className = 'btn btn-small btn-primary';
        approve.textContent = '승인';
        approve.addEventListener('click', () => decidePending(`/api/admin/users/${u.id}/approve`, 'POST'));
        const reject = document.createElement('button');
        reject.className = 'btn btn-small';
        reject.textContent = '거절';
        reject.addEventListener('click', () => {
            if (confirm(`${u.name} 님의 가입 신청을 거절할까요? 신청 정보가 삭제됩니다.`)) {
                decidePending(`/api/admin/users/${u.id}`, 'DELETE');
            }
        });
        actions.append(approve, reject);

        li.append(info, actions);
        return li;
    }));
}

async function decidePending(path, method) {
    const error = $('#pending-error');
    error.hidden = true;
    try {
        await api(path, { method });
        await loadPending();
    } catch (e) {
        error.textContent = e.message;
        error.hidden = false;
    }
}

function setupPendingDialog() {
    const dialog = $('#pending-dialog');
    $('#open-pending').hidden = false;
    $('#open-pending').addEventListener('click', async () => {
        $('#pending-error').hidden = true;
        dialog.showModal();
        try {
            await loadPending();
        } catch (e) {
            $('#pending-error').textContent = e.message;
            $('#pending-error').hidden = false;
        }
    });
    $('#close-pending').addEventListener('click', () => dialog.close());
    loadPending().catch(console.error);
}

// ---------------------------------------------------------------- 유틸

function age(birthDate) {
    const b = new Date(birthDate);
    const now = new Date();
    let a = now.getFullYear() - b.getFullYear();
    if (now < new Date(now.getFullYear(), b.getMonth(), b.getDate())) a--;
    return a;
}

function formatTime(iso) {
    return new Date(iso).toLocaleTimeString('ko-KR', { hour12: false });
}

function tick() {
    $('#clock').textContent = new Date().toLocaleString('ko-KR', { hour12: false });
    state.cards.forEach(updateFreshness);
}

// ---------------------------------------------------------------- 시작

$('#logout-form').addEventListener('submit', e => {
    state.loggingOut = true;
    e.target.elements._csrf.value = csrfToken();
});
api('/api/me').then(me => {
    $('#current-user').textContent = me.username;
    if (me.roles.includes('ROLE_ADMIN')) setupPendingDialog();
}).catch(console.error);
$('#critical-only').addEventListener('change', () => renderAlarms());
window.addEventListener('resize', () => state.cards.forEach((_, id) => renderVitals(id)));
setupRegisterDialog();
tick();
setInterval(tick, 1000);
connect();
