/* 页面逻辑：ops.html */
const { createApp } = Vue;

createApp({
    data() {
        return {
            user: null,
            tab: 'overview',
            tabs: [
                { key: 'overview', label: '总览' },
                { key: 'devices', label: '设备概览' },
                { key: 'orders', label: '工单' },
                { key: 'alarms', label: '告警处理' },
                { key: 'installs', label: '安装确认' }
            ],
            activeAlarms: [],
            allAlarms: [],
            installs: [],
            orders: [],
            devices: [],
            snapshots: {},
            now: Date.now(),
            orderStatus: '',
            orderActingId: '',
            completeTarget: null,
            completeNote: '',
            completeError: '',
            completeSubmitting: false,
            actingId: '',
            trendSeries: [],
            trendTargets: [],
            trendStart: 0,
            trendEnd: 0,
            snapshotSource: null,
            pollTimer: null,
            clockTimer: null,
            trendTimer: null
        };
    },
    computed: {
        roleLabel() {
            return this.user ? roleText(this.user.role) : '';
        },
        userInitial() {
            if (!this.user) return 'OPS';
            return this.user.username.slice(0, 2).toUpperCase();
        },
        clockText() {
            const d = new Date(this.now);
            const pad = n => String(n).padStart(2, '0');
            return pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds());
        },
        dateISO() {
            const d = new Date(this.now);
            const pad = n => String(n).padStart(2, '0');
            return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
        },
        pageTitle() {
            return {
                overview: '温室运行控制台',
                devices: '设备概览',
                orders: '工单调度',
                alarms: '告警处理',
                installs: '安装确认'
            }[this.tab] || '温室运行控制台';
        },
        deviceViews() {
            const kinds = ['temperature', 'humidity', 'soil', 'light'];
            return this.devices.map(device => {
                const entries = (device.points || []).map(point => ({
                    point,
                    snapshot: this.snapshotOf(device, point)
                }));
                const state = this.deviceState(entries);
                const values = {};
                kinds.forEach(kind => {
                    const entry = entries.find(item =>
                        this.metricKind(item.point) === kind &&
                        item.snapshot && item.snapshot.value != null);
                    if (!entry) {
                        values[kind] = null;
                        return;
                    }
                    const unit = entry.snapshot.unit || entry.point.unit || '';
                    let warn = false;
                    const value = Number(entry.snapshot.value);
                    if (Number.isFinite(value)) {
                        if (entry.point.max != null && value > Number(entry.point.max)) warn = true;
                        if (entry.point.min != null && value < Number(entry.point.min)) warn = true;
                    }
                    if (String(entry.snapshot.quality || '').toUpperCase() === 'BAD') warn = true;
                    values[kind] = {
                        text: formatByUnit(entry.snapshot.value, unit),
                        unit,
                        warn
                    };
                });
                return {
                    code: device.deviceCode,
                    name: this.displayName(device),
                    status: state.status,
                    statusLabel: {
                        normal: '正常',
                        warning: '提醒',
                        offline: '离线',
                        pending: '待接入'
                    }[state.status],
                    values,
                    lastText: state.lastTs ? this.relativeTime(state.lastTs) : '—'
                };
            });
        },
        onlineCount() {
            return this.deviceViews.filter(row => row.status === 'normal' || row.status === 'warning').length;
        },
        warningCount() {
            return this.deviceViews.filter(row => row.status === 'warning').length;
        },
        pendingOrders() {
            return this.orders
                .filter(o => o.status === 'PENDING')
                .sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime());
        },
        waitingOrders() {
            return this.orders.filter(o => o.status === 'WAITING_ACCEPT');
        },
        todayClosed() {
            const d = new Date(this.now);
            const dayStart = new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
            return this.orders.filter(o => {
                if (o.status !== 'DONE') return false;
                const ts = new Date(o.completedAt || o.confirmedAt || 0).getTime();
                return ts >= dayStart;
            }).sort((a, b) =>
                new Date(b.completedAt || b.confirmedAt).getTime() -
                new Date(a.completedAt || a.confirmedAt).getTime());
        },
        latestClosed() {
            return this.todayClosed.length ? this.todayClosed[0] : null;
        },
        activeAlarmText() {
            if (!this.activeAlarms.length) return '暂无活动告警';
            const alarm = this.activeAlarms[0];
            return '测点 #' + alarm.pointId + ' · ' + (alarm.limitType === 'MAX' ? '上限' : '下限');
        },
        pendingFoot() {
            if (!this.pendingOrders.length) return '暂无待受理工单';
            return '最早 ' + this.fmtHM(this.pendingOrders[0].createdAt) + ' 提交';
        },
        closedFoot() {
            if (!this.latestClosed) return '今日暂无完工';
            return '最近完工 ' + this.fmtHM(this.latestClosed.completedAt || this.latestClosed.confirmedAt);
        },
        queueOrders() {
            const rank = { PENDING: 0, IN_PROGRESS: 1, WAITING_ACCEPT: 2, DONE: 3, CANCELLED: 4 };
            return this.orders
                .filter(o => o.status !== 'CANCELLED')
                .slice()
                .sort((a, b) => {
                    const diff = (rank[a.status] ?? 9) - (rank[b.status] ?? 9);
                    if (diff !== 0) return diff;
                    return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
                })
                .slice(0, 3);
        },
        trendChart() {
            const series = this.trendSeries;
            const x0 = 42;
            const x1 = 882;
            const y0 = 20;
            const y1 = 212;
            const all = [];
            series.forEach(s => (s.points || []).forEach(p => {
                const value = Number(p.value);
                if (p.value != null && Number.isFinite(value)) all.push(value);
            }));
            if (!all.length || !this.trendStart || !this.trendEnd) {
                return { empty: true, series: [], labels: [], threshold: null };
            }
            let min = Math.min(...all);
            let max = Math.max(...all);
            if (max - min < 1) {
                min -= 1;
                max += 1;
            }
            const pad = (max - min) * 0.15;
            min -= pad;
            max += pad;
            const yOf = v => y1 - (v - min) / (max - min) * (y1 - y0);
            const xOf = t => x0 + (t - this.trendStart) / (this.trendEnd - this.trendStart) * (x1 - x0);
            const labels = [];
            for (let i = 0; i < 5; i++) {
                labels.push({
                    y: y0 + (y1 - y0) * i / 4,
                    text: (max - (max - min) * i / 4).toFixed(1)
                });
            }
            const colors = ['#b9ee7a', '#5fd6a0'];
            const out = series.map((s, idx) => {
                const pts = (s.points || [])
                    .filter(p => p.value != null && Number.isFinite(Number(p.value)))
                    .map(p => [
                        xOf(new Date(p.timestamp).getTime()),
                        yOf(Number(p.value))
                    ]);
                if (!pts.length) return null;
                const line = pts.map(p => p[0].toFixed(1) + ',' + p[1].toFixed(1)).join(' ');
                const last = pts[pts.length - 1];
                const area = 'M' + pts.map(p => p[0].toFixed(1) + ' ' + p[1].toFixed(1)).join(' L') +
                    ' L' + last[0].toFixed(1) + ' ' + y1 +
                    ' L' + pts[0][0].toFixed(1) + ' ' + y1 + ' Z';
                return {
                    name: s.name,
                    color: colors[idx % colors.length],
                    line,
                    area,
                    last: { x: last[0], y: last[1] },
                    dashed: idx === 1
                };
            }).filter(Boolean);
            let threshold = null;
            const target = this.trendTargets[0];
            if (target && target.point.max != null) {
                const value = Number(target.point.max);
                if (value >= min && value <= max) {
                    threshold = { y: yOf(value), text: 'THRESHOLD ' + value };
                }
            }
            return { empty: out.length === 0, series: out, labels, threshold };
        }
    },
    mounted() {
        this.init();
        this.snapshotSource = openSnapshotStream(batch => this.applySnapshots(batch));
        this.clockTimer = setInterval(() => {
            this.now = Date.now();
        }, 1000);
        this.pollTimer = setInterval(() => {
            if (this.tab === 'overview') {
                this.loadSnapshots();
                this.loadAlarms();
                this.loadOrders();
            } else if (this.tab === 'alarms') {
                this.loadAlarms();
            } else if (this.tab === 'installs') {
                this.loadInstalls();
            } else if (this.tab === 'orders') {
                this.loadOrders();
            }
        }, 3000);
        this.trendTimer = setInterval(() => {
            if (this.tab === 'overview') this.loadTrend();
        }, 300000);
    },
    beforeUnmount() {
        clearInterval(this.pollTimer);
        clearInterval(this.clockTimer);
        clearInterval(this.trendTimer);
        if (this.snapshotSource) this.snapshotSource.close();
    },
    methods: {
        pad2(value) {
            return String(value).padStart(2, '0');
        },
        async init() {
            try {
                this.user = await API.get('/api/auth/me');
                await Promise.all([
                    this.loadDevices(),
                    this.loadSnapshots(),
                    this.loadAlarms(),
                    this.loadInstalls(),
                    this.loadOrders()
                ]);
                await this.loadTrend();
            } catch (e) {
                // 401 已由 API 封装跳转
            }
        },
        switchTab(key) {
            this.tab = key;
            if (key === 'overview') {
                this.orderStatus = '';
                this.loadDevices();
                this.loadSnapshots();
                this.loadAlarms();
                this.loadOrders();
                this.loadTrend();
            } else if (key === 'devices') {
                this.refreshDevices();
            } else if (key === 'alarms') {
                this.loadAlarms();
            } else if (key === 'installs') {
                this.loadInstalls();
            } else if (key === 'orders') {
                this.loadOrders();
            }
        },
        async loadDevices() {
            try {
                this.devices = await API.get('/api/devices') || [];
            } catch (e) {
                // 轮询失败不打断页面
            }
        },
        async loadSnapshots() {
            try {
                this.snapshots = await API.get('/api/snapshots') || {};
            } catch (e) {
                // 轮询失败不打断页面
            }
        },
        applySnapshots(batch) {
            if (batch && typeof batch === 'object') {
                Object.assign(this.snapshots, batch);
            }
        },
        refreshDevices() {
            this.loadDevices();
            this.loadSnapshots();
        },
        async loadTrend() {
            const targets = [];
            for (const device of this.devices) {
                const point = (device.points || []).find(p => this.metricKind(p) === 'temperature');
                if (point) targets.push({ device, point });
                if (targets.length >= 2) break;
            }
            this.trendTargets = targets;
            if (!targets.length) {
                this.trendSeries = [];
                return;
            }
            const end = Date.now();
            const start = end - 24 * 3600 * 1000;
            this.trendStart = start;
            this.trendEnd = end;
            const lists = await Promise.all(targets.map(target => {
                const url = '/api/points/' + encodeURIComponent(target.device.deviceCode) + '/' +
                    encodeURIComponent(target.point.pointCode) + '/range' +
                    '?start=' + encodeURIComponent(new Date(start).toISOString()) +
                    '&end=' + encodeURIComponent(new Date(end).toISOString());
                return API.get(url).catch(() => []);
            }));
            this.trendSeries = lists.map((list, idx) => ({
                name: this.displayName(targets[idx].device),
                points: list || []
            }));
        },
        async loadAlarms() {
            try {
                const [active, all] = await Promise.all([
                    API.get('/api/alarms/active'),
                    API.get('/api/alarms')
                ]);
                this.activeAlarms = active || [];
                this.allAlarms = all || [];
            } catch (e) {
                // 轮询失败不打断页面
            }
        },
        async confirmAlarm(alarm) {
            try {
                await API.post('/api/alarms/' + alarm.id + '/confirm', {});
                showToast('告警已确认', 'success');
                await this.loadAlarms();
            } catch (e) {
                showToast(e.message, 'error');
            }
        },
        async loadInstalls() {
            try {
                this.installs = await API.get('/api/service-applications?status=APPROVED') || [];
            } catch (e) {
                // 轮询失败不打断页面
            }
        },
        async confirmInstall(a) {
            if (!window.confirm('确认 ' + a.greenhouseName + ' 的设备已安装完成？')) return;
            this.actingId = a.id;
            try {
                await API.post('/api/service-applications/' + a.id + '/confirm', {});
                showToast('已确认安装', 'success');
                await this.loadInstalls();
            } catch (e) {
                showToast(e.message, 'error');
            } finally {
                this.actingId = '';
            }
        },
        pointTypeLabel(type) {
            return { temperature: '温度', humidity: '湿度', soil: '土壤', light: '光照' }[type] || type;
        },
        async loadOrders() {
            try {
                const url = '/api/work-orders' +
                    (this.orderStatus ? '?status=' + encodeURIComponent(this.orderStatus) : '');
                this.orders = await API.get(url) || [];
            } catch (e) {
                // 轮询失败不打断页面
            }
        },
        async acceptOrder(order) {
            if (!window.confirm('接单 ' + order.orderNo + '？')) return;
            this.orderActingId = order.id;
            try {
                await API.post('/api/work-orders/' + order.id + '/accept', {});
                showToast('已接单', 'success');
                await this.loadOrders();
            } catch (e) {
                showToast(e.message, 'error');
            } finally {
                this.orderActingId = '';
            }
        },
        openComplete(order) {
            this.completeTarget = order;
            this.completeNote = '';
            this.completeError = '';
        },
        closeComplete() {
            this.completeTarget = null;
        },
        async submitComplete() {
            if (!this.completeNote) {
                this.completeError = '请填写完工说明';
                return;
            }
            this.completeSubmitting = true;
            try {
                await API.post('/api/work-orders/' + this.completeTarget.id + '/complete',
                    { note: this.completeNote });
                showToast('已提交完工，等待种植户验收', 'success');
                this.completeTarget = null;
                await this.loadOrders();
                await this.loadTrend();
            } catch (e) {
                this.completeError = e.message;
            } finally {
                this.completeSubmitting = false;
            }
        },
        orderStatusLabel(status) {
            return {
                PENDING: '待受理',
                IN_PROGRESS: '处理中',
                WAITING_ACCEPT: '待验收',
                DONE: '已完成',
                CANCELLED: '已取消'
            }[status] || status;
        },
        orderBadgeClass(status) {
            return {
                PENDING: 'badge-warn',
                WAITING_ACCEPT: 'badge-warn',
                CANCELLED: 'badge-muted'
            }[status] || '';
        },
        queueStateClass(status) {
            return {
                PENDING: 'warn',
                IN_PROGRESS: '',
                WAITING_ACCEPT: 'ok',
                DONE: 'ok'
            }[status] || '';
        },
        displayName(device) {
            return (device.name || device.deviceCode || '')
                .replace(/\b(modbus|mqtt)\b/gi, '')
                .replace(/\s+/g, ' ')
                .replace(/([\u4e00-\u9fa5])\s+(?=[\u4e00-\u9fa5])/g, '$1')
                .trim();
        },
        snapshotOf(device, point) {
            return this.snapshots[device.deviceCode + ':' + point.pointCode] || null;
        },
        metricKind(point) {
            const text = ((point.metric || '') + ' ' + (point.pointCode || '')).toLowerCase();
            if (text.includes('soil') || text.includes('土壤')) return 'soil';
            if (text.includes('temp') || text.includes('温')) return 'temperature';
            if (text.includes('humi') || text.includes('湿')) return 'humidity';
            if (text.includes('light') || text.includes('lux') || text.includes('光照')) return 'light';
            return 'other';
        },
        deviceState(entries) {
            const stamps = entries
                .map(entry => entry.snapshot && entry.snapshot.timestamp
                    ? new Date(entry.snapshot.timestamp).getTime() : 0)
                .filter(value => value > 0);
            const lastTs = stamps.length ? Math.max(...stamps) : 0;
            const online = lastTs > 0 && this.now - lastTs < 60000;
            let abnormal = false;

            if (online) {
                entries.forEach(entry => {
                    const snapshot = entry.snapshot;
                    if (!snapshot) return;
                    if (String(snapshot.quality || '').toUpperCase() === 'BAD') {
                        abnormal = true;
                        return;
                    }
                    const value = Number(snapshot.value);
                    if (!Number.isFinite(value)) return;
                    if (entry.point.max != null && value > Number(entry.point.max)) abnormal = true;
                    if (entry.point.min != null && value < Number(entry.point.min)) abnormal = true;
                });
            }

            let status = 'normal';
            if (!entries.length || !lastTs) status = 'pending';
            else if (!online) status = 'offline';
            else if (abnormal) status = 'warning';

            return { status, online, lastTs };
        },
        relativeTime(value) {
            const ts = typeof value === 'number' ? value : new Date(value).getTime();
            if (!ts) return '尚未上报';
            const seconds = Math.max(0, Math.floor((this.now - ts) / 1000));
            if (seconds < 10) return '刚刚';
            if (seconds < 60) return seconds + ' 秒前';
            const minutes = Math.floor(seconds / 60);
            if (minutes < 60) return minutes + ' 分钟前';
            return Math.floor(minutes / 60) + ' 小时前';
        },
        fmtHM(value) {
            if (!value) return '-';
            const d = new Date(value);
            const pad = n => String(n).padStart(2, '0');
            return pad(d.getHours()) + ':' + pad(d.getMinutes());
        },
        fmtTime(value) {
            if (!value) return '-';
            const d = new Date(value);
            const pad = n => String(n).padStart(2, '0');
            return pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' +
                pad(d.getHours()) + ':' + pad(d.getMinutes());
        },
        async logout() {
            try {
                await API.post('/api/auth/logout', {});
            } catch (e) {
                // 忽略，统一回登录页
            }
            location.href = '/login.html';
        }
    }
}).mount('#app');
