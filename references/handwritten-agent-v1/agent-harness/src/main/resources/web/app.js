/* Stage 04 Mini Agent Harness Console - 前端（薄：只做 HTTP/JSON + 渲染 + 轮询） */
let currentRunId = null;

const $ = (id) => document.getElementById(id);

async function api(path, method = 'GET', body) {
    const opts = { method, headers: { 'Content-Type': 'application/json' } };
    if (body !== undefined) opts.body = JSON.stringify(body);
    const res = await fetch(path, opts);
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || '请求失败');
    return data;
}

function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"]/g, c => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;'
    })[c]);
}
function truncate(s, n) { return s && s.length > n ? s.slice(0, n) + '…' : s; }

async function runDemo() {
    $('status-line').textContent = '运行中…';
    const ov = await api('/api/demo', 'POST', {});
    currentRunId = ov.runId;
    $('run-id').textContent = currentRunId;
    $('status-line').textContent = '已创建运行';
    await refreshAll();
}

async function approve(id) {
    await api(`/api/approvals/${id}/approve`, 'POST', {});
    $('status-line').textContent = `已批准 #${id}，申请从 Checkpoint 恢复执行`;
    await refreshAll();
}
async function reject(id) {
    await api(`/api/approvals/${id}/reject`, 'POST', {});
    $('status-line').textContent = `已拒绝 #${id}`;
    await refreshAll();
}

// ---------- 渲染 ----------

async function refreshAll() {
    if (!currentRunId) { await refreshApprovals(); return; }
    try {
        const [ov, tools, trace, workers] = await Promise.all([
            api(`/api/runs/${currentRunId}/overview`),
            api(`/api/runs/${currentRunId}/tools`),
            api(`/api/runs/${currentRunId}/trace`),
            api(`/api/runs/${currentRunId}/workers`),
        ]);
        renderOverview(ov);
        renderMetrics(ov.metrics || {});
        renderTools(tools.tools || []);
        renderMemory(ov);
        renderWorkers(workers.workers || []);
        renderTrace(trace.events || []);
    } catch (e) {
        $('status-line').textContent = '加载失败: ' + e.message;
    }
    await refreshApprovals();
}

function renderOverview(o) {
    const steps = (o.plan || []).map(p => {
        const cls = p.status === 'DONE' ? 'step-done'
            : p.status === 'RUNNING' ? 'step-current' : 'step-pending';
        const mark = p.status === 'DONE' ? '[x]' : p.status === 'RUNNING' ? '[>]' : '[ ]';
        return `<div class="${cls}">${mark} ${esc(p.id)}. ${esc(p.description)}</div>`;
    }).join('<br>');
    $('overview').innerHTML = `
        <div class="kv"><span>goal</span>${esc(o.goal)}</div>
        <div class="kv"><span>status</span><b class="status-${esc((o.status||'').toLowerCase())}">${esc(o.status)}</b></div>
        <div class="kv"><span>currentStep</span>${o.currentStep}</div>
        <div class="plan">${steps || '<span class="dim">无计划</span>'}</div>
    `;
}

function renderMetrics(m) {
    $('metrics').innerHTML = `
        <div class="kv"><span>steps</span>${m.steps}</div>
        <div class="kv"><span>toolCalls</span>${m.toolCalls}</div>
        <div class="kv"><span>toolErrors</span>${m.toolErrors}</div>
        <div class="kv"><span>approvals</span>${m.approvals}</div>
        <div class="kv"><span>pass</span><b>${m.pass ? 'PASS' : 'NOT PASS'}</b></div>
        <div class="notes dim">${(m.notes || []).map(n => esc(n)).join('<br>')}</div>
    `;
}

function renderTools(tools) {
    if (!tools.length) { $('tools').innerHTML = '<span class="dim">尚无工具调用</span>'; return; }
    $('tools').innerHTML = tools.map(t => `
        <div class="tool ${t.success ? 'ok' : 'err'}">
            <span class="tool-name">${esc(t.tool)}</span>
            <span class="dim">${esc(t.workerId ? 'worker:' + t.workerId : 'orchestrator')}</span>
            <div class="tool-args">args ${esc(t.args)}</div>
            <div class="tool-result">→ ${esc(truncate(t.result, 120))} <span class="dim">(${t.elapsedMs}ms)</span></div>
        </div>`).join('\n');
}

function renderMemory(ov) {
    const mem = (ov.memory || []).map(m => `- [mem] ${esc(m.key)} → ${esc(truncate(m.value, 60))}`).join('\n');
    const kb = (ov.knowledge || []).map(k => `- [doc] ${esc(k)}`).join('\n');
    $('memory').innerHTML = `<b>Knowledge docs</b><br>${kb || '<span class="dim">（无）</span>'}` +
        `<br><br><b>Memory</b><br>${mem || '<span class="dim">（无记忆）</span>'}`.replace(/\n/g, '<br>');
}

function renderWorkers(workers) {
    if (!workers.length) { $('workers').innerHTML = '<span class="dim">未启用 Worker</span>'; return; }
    $('workers').innerHTML = workers.map(w => `
        <div class="worker">
            <b>${esc(w.name)}</b>
            <div class="dim">${(w.events || []).map(e => '· ' + esc(truncate(e, 70))).join('<br>') || '（无轨迹）'}</div>
        </div>`).join('\n');
}

const TYPE_COLOR = {
    MODEL_CALL: '#b58900', TOOL_CALL: '#268bd2', TOOL_RESULT: '#2aa198',
    STATE_CHANGED: '#859900', MEMORY_RETRIEVED: '#6c71c4', CONTEXT_BUILT: '#94a3b8',
    CHECKPOINT_SAVED: '#7b8a8f', APPROVAL_REQUIRED: '#dc322f', APPROVAL_RESOLVED: '#cb4b16',
    HANDOFF: '#d33682', EVALUATION: '#16a34a', RUN_FINISHED: '#374151'
};

function renderTrace(events) {
    if (!events.length) { $('trace').innerHTML = '<span class="dim">尚无事件</span>'; return; }
    $('trace').innerHTML = events.map(e => `
        <div class="ev">
            <span class="pill" style="color:${TYPE_COLOR[e.type] || '#555'};border-color:${TYPE_COLOR[e.type] || '#555'}">${esc(e.type)}</span>
            <span class="ev-who">${esc(e.workerId ? 'W:' + e.workerId : 'ORCH')}</span>
            <span>${esc(e.message)}</span>
        </div>`).join('\n');
}

async function refreshApprovals() {
    const data = await api('/api/approvals');
    const list = data.approvals || [];
    if (!list.length) { $('approvals').innerHTML = '<span class="dim">无审批请求</span>'; return; }
    $('approvals').innerHTML = list.map(a => {
        const pending = a.status === 'PENDING';
        return `<div class="approval ${pending ? 'pending' : ''}">
            <div><b>#${a.id}</b> [${esc(a.risk)}] ${esc(a.status)} ${esc(a.runId)}</div>
            <div class="dim">action: ${esc(a.toolCall)}</div>
            <div class="dim">reason: ${esc(a.reason)}</div>
            ${pending ? `<button class="btn-ok" onclick="window.__approve(${a.id})">批准</button>
                         <button class="btn-no" onclick="window.__reject(${a.id})">拒绝</button>` : ''}
        </div>`;
    }).join('\n');
    window.__approve = approve;
    window.__reject = reject;
}

// ---------- 绑定与轮询 ----------
$('demo').addEventListener('click', runDemo);
$('refresh').addEventListener('click', () => refreshAll().then(() => { $('status-line').textContent = '已刷新'; }));

setInterval(() => { if (currentRunId) refreshAll(); }, 1200);
setInterval(() => { refreshApprovals().catch(() => {}); }, 1500);