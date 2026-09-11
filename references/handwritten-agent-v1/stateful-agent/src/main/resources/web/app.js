(function () {
    'use strict';

    const BASE = '/api';
    let currentConversationId = null;
    let pollInterval = null;

    const $ = id => document.getElementById(id);
    const messagesEl = $('messages');
    const stateEl = $('state');
    const planEl = $('plan');
    const memoryEl = $('memory');
    const convIdBadge = $('conversation-id');
    const convList = $('conv-list');
    const input = $('message-input');
    const sendBtn = $('send-button');
    const newConvBtn = $('new-conversation');

    // ---- API ----
    async function api(method, path, body) {
        const opts = { method, headers: { 'Content-Type': 'application/json' } };
        if (body) opts.body = JSON.stringify(body);
        const res = await fetch(BASE + path, opts);
        if (!res.ok) throw new Error(await res.text());
        return res.json();
    }

    // ---- 新建会话 ----
    newConvBtn.addEventListener('click', async () => {
        const data = await api('POST', '/conversations', { title: '新会话' });
        currentConversationId = data.id;
        convIdBadge.textContent = currentConversationId;
        loadConversations();
        clearChat();
        clearRight();
        input.focus();
    });

    // ---- 加载会话列表 ----
    async function loadConversations() {
        const list = await api('GET', '/conversations');
        convList.innerHTML = '';
        list.forEach(c => {
            const div = document.createElement('div');
            div.textContent = c.id + ' — ' + c.title;
            if (c.id === currentConversationId) div.classList.add('active');
            div.addEventListener('click', () => switchConversation(c.id));
            convList.appendChild(div);
        });
    }

    async function switchConversation(id) {
        currentConversationId = id;
        convIdBadge.textContent = id;
        loadConversations();
        loadState(id);
        input.focus();
    }

    // ---- 加载状态（轮询） ----
    async function loadState(conversationId) {
        if (!conversationId) return;
        try {
            const data = await api('GET', '/conversations/' + conversationId + '/state');
            if (data.exists === false) return;
            renderMessages(data.messages || []);
            renderState(data);
            renderPlan(data);
        } catch (e) { /* ignore */ }
        try {
            const mems = await api('GET', '/conversations/' + conversationId + '/memories');
            renderMemory(mems);
        } catch (e) { /* ignore */ }
    }

    // ---- 发送消息 ----
    async function sendMessage() {
        const msg = input.value.trim();
        if (!msg || !currentConversationId) return;
        input.value = '';
        sendBtn.disabled = true;

        try {
            const data = await api('POST', '/chat', { conversationId: currentConversationId, message: msg });
            renderMessages(data.messages || []);
            renderState(data);
            renderPlan(data);
            renderMemoryFromResult(data);
            if (data.memorySaved && data.memoryContent) {
                appendMemorySave(data.memoryContent);
            }
        } catch (e) {
            console.error('chat error', e);
            const errMsg = document.createElement('div');
            errMsg.className = 'msg assistant';
            errMsg.textContent = '[错误] ' + e.message;
            messagesEl.appendChild(errMsg);
            messagesEl.scrollTop = messagesEl.scrollHeight;
        } finally {
            sendBtn.disabled = false;
            input.focus();
        }
    }

    input.addEventListener('keydown', e => { if (e.key === 'Enter') sendMessage(); });
    sendBtn.addEventListener('click', sendMessage);

    // ---- 渲染 ----
    function renderMessages(msgs) {
        messagesEl.innerHTML = '';
        msgs.forEach(m => {
            const div = document.createElement('div');
            div.className = 'msg ' + (m.role === 'user' ? 'user' : 'assistant');
            const meta = document.createElement('span');
            meta.className = 'meta';
            meta.textContent = m.role === 'user' ? '你' : 'Agent';
            div.appendChild(meta);
            div.appendChild(document.createTextNode(m.content));
            messagesEl.appendChild(div);
        });
        messagesEl.scrollTop = messagesEl.scrollHeight;
    }

    function renderState(data) {
        if (!data.runId) { stateEl.innerHTML = '<span class="dim">运行 Agent 后查看状态</span>'; return; }
        stateEl.innerHTML = `
            <div class="kv">run:   <b>${data.runId}</b></div>
            <div class="kv">goal:  <b>${escapeHtml(data.goal || '—')}</b></div>
            <div class="kv">status:<b>${data.status || '—'}</b></div>
            <div class="kv">step:  <b>${data.currentStep != null ? data.currentStep : '—'}</b></div>
        `;
    }

    function renderPlan(data) {
        if (!data.steps || data.steps.length === 0) {
            planEl.innerHTML = '<span class="dim">运行 Agent 后查看计划</span>';
            return;
        }
        let html = '<div class="kv">目标: <b>' + escapeHtml(data.planGoal || '—') + '</b></div>';
        data.steps.forEach(s => {
            const cls = s.status === 'DONE' ? 'done' : s.status === 'RUNNING' ? 'running' : s.status === 'FAILED' ? 'failed' : 'pending';
            let extra = '';
            if (s.failureReason) extra = ' <span class="err">(' + escapeHtml(s.failureReason) + ')</span>';
            html += '<span class="step ' + cls + '">[' + s.id + '] ' + escapeHtml(s.description) + ' (' + s.status + ')' + extra + '</span>';
        });
        planEl.innerHTML = html;
    }

    function renderMemory(mems) {
        if (!mems || mems.length === 0) {
            memoryEl.innerHTML = '<span class="dim">当前没有保存的长期记忆</span>';
            return;
        }
        let html = '';
        mems.forEach(m => {
            html += '<span class="memory-item"><span class="type">[' + escapeHtml(m.type) + ']</span> ' + escapeHtml(m.content) + '（重要度 ' + (m.importance || '-') + '）</span>';
        });
        memoryEl.innerHTML = html;
    }

    function renderMemoryFromResult(data) {
        const mems = data.retrievedMemories || [];
        if (mems.length === 0) {
            memoryEl.innerHTML = '<span class="dim">本轮未检索到相关记忆</span>';
            return;
        }
        let html = '';
        mems.forEach(m => {
            html += '<span class="memory-item"><span class="type">[' + escapeHtml(m.type) + ']</span> ' + escapeHtml(m.content) + '</span>';
        });
        memoryEl.innerHTML = html;
    }

    function appendMemorySave(content) {
        const existing = memoryEl.innerHTML;
        if (existing.startsWith('<span class="dim"')) {
            memoryEl.innerHTML = '<span class="mem-saved">💾 已保存: ' + escapeHtml(content) + '</span>';
        } else {
            memoryEl.innerHTML = '<span class="mem-saved">💾 已保存: ' + escapeHtml(content) + '</span><hr class="sep">' + existing;
        }
    }

    function clearChat() {
        messagesEl.innerHTML = '<span class="dim">请输入消息开始对话</span>';
    }

    function clearRight() {
        stateEl.innerHTML = '<span class="dim">运行 Agent 后查看状态</span>';
        planEl.innerHTML = '<span class="dim">运行 Agent 后查看计划</span>';
        memoryEl.innerHTML = '<span class="dim">运行 Agent 后查看本轮检索到的长期记忆</span>';
    }

    function escapeHtml(s) {
        if (!s) return '';
        const div = document.createElement('div');
        div.textContent = s;
        return div.innerHTML;
    }

    // ---- 启动：自动加载 ----
    loadConversations();

    // ---- 轮询（每 3 秒刷新右侧） ----
    setInterval(() => { if (currentConversationId) loadState(currentConversationId); }, 3000);
})();