/* Stage 03 Long-running Agent Debugger - 前端逻辑（薄：只做 HTTP/JSON + 渲染） */
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

async function start() {
    const goal = $('goal').value;
    const data = await api('/api/runs', 'POST', { goal });
    currentRunId = data.runId;
    $('run-id').textContent = currentRunId;
    showOutcome('已创建 run，初始 Checkpoint v0');
    await refreshAll(data);
}

async function step() {
    if (!currentRunId) return alert('请先 Start');
    const o = await api(`/api/runs/${currentRunId}/step`, 'POST', {});
    showOutcome(`[${o.status}] ${o.message}`);
    await refreshAll();
}

async function interrupt() {
    if (!currentRunId) return alert('请先 Start');
    await api(`/api/runs/${currentRunId}/interrupt`, 'POST', {});
    showOutcome('已请求在下一步前受控中断。点击 Step 观察 dispatch 是否停在原地。');
}

async function resume() {
    if (!currentRunId) return alert('请先 Start');
    const o = await api(`/api/runs/${currentRunId}/resume`, 'POST', {});
    showOutcome(`[${o.status}] ${o.message}（从 Checkpoint 继续）`);
    await refreshAll();
}

async function evaluate() {
    if (!currentRunId) return alert('请先 Start');
    const data = await api(`/api/runs/${currentRunId}/evaluate`, 'POST', {});
    showOutcome('Evaluator run:\n' + data.log.map(l => '  ' + l).join('\n'));
    await refreshEval();
}

async function autoDemo() {
    await start();
    await step(); await step();
    await interrupt();
    await step();                 // 这一列会在中断点停下
    await refreshAll();
    showOutcome('自动演示完成：第 1~2 步已执行，随后在模拟中断处停止。现在可点击 Resume 继续。');
}

// ---------- 渲染 ----------

async function refreshAll(ov) {
    const overview = ov || await api(`/api/runs/${currentRunId}/overview`);
    renderOverview(overview);
    renderPlan(overview);
    await Promise.all([refreshCheckpoints(), refreshContext()]);
}

function renderOverview(o) {
    $('overview').innerHTML = `
runId: <span class="badge">${esc(o.runId)}</span>
status: <b>${esc(o.status)}</b>   currentStep: ${o.currentStep}   compacted: ${o.compacted}
`;
}

function renderPlan(o) {
    const steps = (o.plan || []).map(s => {
        const cls = s.status === 'DONE' ? 'step-done'
            : s.status === 'RUNNING' ? 'step-current' : 'step-pending';
        const mark = s.status === 'DONE' ? '[x]' : s.status === 'RUNNING' ? '[>]' : '[ ]';
        const suffix = s.result ? '  → ' + truncate(s.result, 60) : '';
        return `<div class="${cls}">${mark} ${esc(s.id)}. ${esc(s.description)}${esc(suffix)}</div>`;
    }).join('\n');
    $('plan').innerHTML = steps || '<span class="dim">无计划</span>';
    $('plan').innerHTML = $('plan').innerHTML.replace(/\n/g, '<br>');
}

async function refreshCheckpoints() {
    const list = await api(`/api/runs/${currentRunId}/checkpoints`);
    if (!list.length) { $('checkpoints').innerHTML = '<span class="dim">尚无 Checkpoint</span>'; return; }
    $('checkpoints').innerHTML = list.map(cp =>
        `<div>v<span class="cp-v">${cp.version}</span> @${esc(cp.savedAt)}  nextStep=${cp.currentStep} ${cp.compacted ? '·COMPACTED' : ''}</div>`
    ).join('\n').replace(/\n/g, '<br>');
}

async function refreshContext() {
    const ctx = await api(`/api/runs/${currentRunId}/context`);
    const rag = ctx.ragDocs.length ? ctx.ragDocs.map(d => `- [${esc(d.title)}] ${esc(truncate(d.content, 80))}`).join('\n') : '（无 RAG 命中）';
    const mems = ctx.memories.length ? ctx.memories.map(m => `- [${esc(m.type)}] ${esc(m.content)}`).join('\n') : '（无记忆命中）';
    const comp = ctx.compactionSummaries.length ? ctx.compactionSummaries.map(c => `- ${esc(c)}`).join('\n') : '（未触发压缩）';
    const snap = ctx.snapshots.length ? ctx.snapshots.map(s =>
        `step#${s.stepIndex} @${esc(s.createdAt)}:\n${esc(firstLines(s.context, 4))}`).join('\n\n') : '（无快照）';
    $('context').innerHTML = `
<b>RAG retrieved docs</b>\n${rag}\n\n<b>Retrieved memories</b>\n${mems}
\n\n<b>Compacted summaries</b>\n${comp}\n\n<b>Selected context (snapshots)</b>\n${snap}
`.replace(/\n/g, '<br>');
}

async function refreshEval() {
    const list = await api(`/api/runs/${currentRunId}/evaluations`);
    if (!list.length) { $('evaluation').innerHTML = '<span class="dim">Evaluate 后查看</span>'; return; }
    $('evaluation').innerHTML = list.map(e =>
        `<div>iter#${e.iteration} · validator=${e.validatorPass ? '<b>PASS</b>' : 'REJECT'}` +
        `${e.validatorErrors.length ? ' (' + esc(e.validatorErrors.join('; ')) + ')' : ''}` +
        ` · evaluator=${e.evaluatorPass ? '<b>PASS</b>(score ' + e.evaluatorScore + ')' : 'REJECT'}` +
        `${e.evaluatorIssues.length ? ' (' + esc(e.evaluatorIssues.join('; ')) + ')' : ''}</div>`
    ).join('\n').replace(/\n/g, '<br>');
}

function showOutcome(msg) { $('outcome').textContent = msg; }

function truncate(s, n) { return s && s.length > n ? s.slice(0, n) + '…' : s; }
function firstLines(s, n) { return s ? s.split('\n').slice(0, n).join('\n') : ''; }

// ---------- 绑定 ----------
$('start').addEventListener('click', start);
$('step').addEventListener('click', step);
$('interrupt').addEventListener('click', interrupt);
$('resume').addEventListener('click', resume);
$('evaluate').addEventListener('click', evaluate);
$('auto-demo').addEventListener('click', autoDemo);