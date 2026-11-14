// 统一请求封装：带 Session Cookie，401 跳登录，错误提取后端 message
const API = {
    async request(url, options = {}) {
        const opts = { credentials: 'same-origin', ...options };
        if (opts.body !== undefined && typeof opts.body !== 'string') {
            opts.headers = { 'Content-Type': 'application/json', ...(opts.headers || {}) };
            opts.body = JSON.stringify(opts.body);
        }

        const resp = await fetch(url, opts);

        if (resp.status === 401) {
            if (!location.pathname.endsWith('login.html')) {
                location.href = '/login.html';
            }
            throw new Error('未登录');
        }

        if (!resp.ok) {
            let message = '请求失败（' + resp.status + '）';
            const text = await resp.text();
            if (text) {
                try {
                    const data = JSON.parse(text);
                    message = data.error || data.message || message;
                } catch (e) {
                    message = text;
                }
            }
            throw new Error(message);
        }

        if (resp.status === 204) {
            return null;
        }
        const text = await resp.text();
        return text ? JSON.parse(text) : null;
    },

    get(url) {
        return this.request(url);
    },

    post(url, body) {
        return this.request(url, { method: 'POST', body });
    },

    put(url, body) {
        return this.request(url, { method: 'PUT', body });
    },

    del(url) {
        return this.request(url, { method: 'DELETE' });
    }
};

// 订阅服务端快照推送；不支持 SSE 时返回 null，页面保留轮询兜底
function openSnapshotStream(onSnapshot) {
    if (!window.EventSource) return null;
    const source = new EventSource('/api/snapshots/stream');
    source.onmessage = event => {
        try {
            onSnapshot(JSON.parse(event.data));
        } catch (e) {
            // 忽略单条格式错误，不打断连接
        }
    };
    return source;
}

// 按单位格式化显示值：温度保留 1 位小数，百分比保留 2 位小数，其余最多 2 位
function formatByUnit(value, unit) {
    if (value === null || value === undefined || value === '') return '-';
    const number = Number(value);
    if (Number.isNaN(number)) return String(value);
    if (unit && (unit.includes('℃') || unit.includes('°C'))) return number.toFixed(1);
    if (unit && unit.includes('%')) return number.toFixed(2);
    return Math.round(number * 100) / 100;
}

// 角色首页：登录后按角色进入对应工作台
const PORTAL_HOME = { ADMIN: '/admin.html', OPS: '/ops.html', FARMER: '/farmer.html' };

function homeForRole(role) {
    return PORTAL_HOME[role] || '/login.html';
}

function roleText(role) {
    return { ADMIN: '管理员', OPS: '运维', FARMER: '种植户' }[role] || role || '';
}

// 页面右上角提示
function showToast(message, type = 'info') {
    let root = document.getElementById('toast-root');
    if (!root) {
        root = document.createElement('div');
        root.id = 'toast-root';
        root.className = 'toast-root';
        document.body.appendChild(root);
    }
    const item = document.createElement('div');
    item.className = 'toast ' + type;
    item.textContent = message;
    root.appendChild(item);
    setTimeout(() => item.remove(), 2800);
}
