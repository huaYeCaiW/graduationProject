/* 页面逻辑：farmer.html */
const { createApp } = Vue;

createApp({
    data() {
        return {
            user: null,
            tab: 'devices',
            tabs: [
                { key: 'devices', label: '今日田情' },
                { key: 'orders', label: '我的工单' },
                { key: 'apply', label: '申请服务' },
                { key: 'mine', label: '我的申请' }
            ],
            submitting: false,
            applications: [],
            devices: [],
            orders: [],
            orderForm: { greenhouseName: '', applicantName: '', phone: '', description: '' },
            orderError: '',
            submittingOrder: false,
            actingOrderId: '',
            snapshots: {},
            now: Date.now(),
            demandTypes: [
                { type: 'temperature', label: '温度', count: 1 },
                { type: 'humidity', label: '空气湿度', count: 1 },
                { type: 'soil', label: '土壤湿度', count: 0 },
                { type: 'light', label: '光照', count: 0 }
            ],
            applyForm: { greenhouseName: '', applicantName: '', phone: '' },
            applyError: '',
            snapshotSource: null,
            snapshotTimer: null,
            pollTimer: null,
            clockTimer: null
        };
    },
    computed: {
        roleLabel() {
            return this.user ? roleText(this.user.role) : '';
        },
        userInitial() {
            return this.user ? this.user.username.charAt(0).toUpperCase() : '';
        },
        dateLabel() {
            const d = new Date(this.now);
            const week = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'][d.getDay()];
            return d.getFullYear() + ' 年 ' + (d.getMonth() + 1) + ' 月 ' + d.getDate() + ' 日 · ' + week;
        },
        clockText() {
            const d = new Date(this.now);
            const pad = n => String(n).padStart(2, '0');
            return pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds());
        },
        heroTitle() {
            return {
                devices: '今日田情',
                orders: '我的工单',
                apply: '申请服务',
                mine: '我的申请'
            }[this.tab] || '今日田情';
        },
        heroSub() {
            if (this.tab === 'orders') return '提交报修后由运维受理，完工后请及时验收。';
            if (this.tab === 'apply') return '一棚一单，填写需要监测的项目，提交后由合作社审批。';
            if (this.tab === 'mine') return '申请审批与安装进度，每 3 秒自动刷新。';
            const views = this.greenhouseViews;
            if (!views.length) return '设备尚未接入，提交申请后由合作社安排安装。';
            const warn = views.find(v => v.status === 'warning');
            if (warn) return '共 ' + views.length + ' 个大棚持续监测中，' + warn.name + warn.reminder + '。';
            const off = views.find(v => v.status === 'offline');
            if (off) return '共 ' + views.length + ' 个大棚持续监测中，' + off.name + ' 设备离线，请联系运维。';
            return '共 ' + views.length + ' 个大棚持续监测中，各项指标平稳。';
        },
        stats() {
            const views = this.greenhouseViews;
            const normal = views.filter(v => v.status === 'normal').length;
            const warning = views.filter(v => v.status === 'warning').length;
            const active = this.orders.filter(o =>
                o.status === 'PENDING' || o.status === 'IN_PROGRESS' || o.status === 'WAITING_ACCEPT').length;
            return [
                { value: this.pad2(this.devices.length), label: '在管大棚' },
                { value: this.pad2(normal), label: '运行正常' },
                { value: this.pad2(warning), label: '待处理提醒' },
                { value: this.pad2(active), label: '进行中工单' }
            ];
        },
        sideOrders() {
            const rank = { PENDING: 0, IN_PROGRESS: 1, WAITING_ACCEPT: 2, DONE: 3, CANCELLED: 4 };
            return this.orders.slice().sort((a, b) => {
                const diff = (rank[a.status] ?? 9) - (rank[b.status] ?? 9);
                if (diff !== 0) return diff;
                return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
            }).slice(0, 4);
        },
        quote() {
            const warn = this.greenhouseViews.find(v => v.status === 'warning');
            if (warn && warn.reminder) return warn.name + warn.reminder + '，建议及时处理。';
            return '各棚指标平稳，保持日常通风与夜间保温即可。';
        },
        almanac() {
            const terms = [
                [1, 5, '小寒'], [1, 20, '大寒'], [2, 4, '立春'], [2, 19, '雨水'],
                [3, 5, '惊蛰'], [3, 20, '春分'], [4, 5, '清明'], [4, 20, '谷雨'],
                [5, 5, '立夏'], [5, 21, '小满'], [6, 5, '芒种'], [6, 21, '夏至'],
                [7, 7, '小暑'], [7, 23, '大暑'], [8, 7, '立秋'], [8, 23, '处暑'],
                [9, 7, '白露'], [9, 23, '秋分'], [10, 8, '寒露'], [10, 23, '霜降'],
                [11, 7, '立冬'], [11, 22, '小雪'], [12, 7, '大雪'], [12, 21, '冬至']
            ];
            const now = new Date(this.now);
            const year = now.getFullYear();
            const today = new Date(year, now.getMonth(), now.getDate()).getTime();
            const list = terms.map(([m, d, name]) => ({
                ts: new Date(year, m - 1, d).getTime(),
                name
            }));
            let current = null;
            let next = null;
            list.forEach(item => {
                if (item.ts <= today) current = item;
                else if (!next) next = item;
            });
            if (!current) current = { name: '冬至' };
            if (!next) next = { ts: new Date(year + 1, 0, 5).getTime(), name: '小寒' };
            const days = Math.round((next.ts - today) / 86400000);
            const month = now.getMonth() + 1;
            let advice = '夜间保温';
            if (month >= 3 && month <= 5) advice = '通风控湿';
            else if (month >= 6 && month <= 8) advice = '遮阳降温';
            else if (month >= 9 && month <= 10) advice = '昼夜防温差';
            return [
                { label: '节气', value: current.name },
                { label: '距' + next.name, value: days === 0 ? '今日' : days + ' 天' },
                { label: '农事建议', value: advice }
            ];
        },
        greenhouseViews() {
            return this.devices.map(device => {
                const entries = (device.points || []).map(point => ({
                    point,
                    snapshot: this.snapshotOf(device, point)
                }));
                const state = this.deviceState(entries);

                let reminder = '';
                if (state.status === 'offline') {
                    reminder = '设备离线，最后一次上报在 ' + this.relativeTime(state.lastTs);
                } else if (state.status === 'pending') {
                    reminder = entries.length ? '等待设备上报数据' : '设备已录入，等待数据接入';
                } else if (state.status === 'warning') {
                    if (state.anomalies.length) {
                        reminder = state.anomalies[0];
                        if (state.anomalies.length > 1) {
                            reminder += '，另有 ' + (state.anomalies.length - 1) + ' 项指标异常';
                        }
                    } else {
                        reminder = '数据异常，请联系运维';
                    }
                }

                const seen = new Set();
                const metrics = [];
                entries
                    .filter(entry => entry.snapshot && entry.snapshot.value != null)
                    .sort((a, b) => this.metricPriority(a.point) - this.metricPriority(b.point))
                    .forEach(entry => {
                        const kind = this.metricKind(entry.point);
                        if (seen.has(kind)) return;
                        seen.add(kind);
                        const raw = this.snapshotText(entry.point, entry.snapshot);
                        const parts = String(raw).split(' ');
                        metrics.push({
                            label: entry.point.metric || this.metricLabel(kind),
                            value: raw,
                            num: parts[0],
                            unit: parts.slice(1).join(' ')
                        });
                    });

                const shown = metrics.slice(0, 3);

                const labels = {
                    normal: { text: '正常' },
                    warning: { text: '有提醒' },
                    offline: { text: '离线' },
                    pending: { text: '等待接入' }
                };

                return {
                    code: device.deviceCode,
                    name: this.displayName(device),
                    status: state.status,
                    statusLabel: labels[state.status].text,
                    mainNum: shown.length ? shown[0].num : null,
                    mainUnit: shown.length ? shown[0].unit : '',
                    metaText: shown.slice(1).map(m => m.label + ' ' + m.value).join('　·　'),
                    reminder,
                    lastText: state.lastTs ? '数据 ' + this.relativeTime(state.lastTs) + '更新' : '尚未收到数据'
                };
            });
        },
        overallSummary() {
            const views = this.greenhouseViews;
            const normal = views.filter(view => view.status === 'normal').length;
            const warning = views.filter(view => view.status === 'warning').length;
            const offline = views.filter(view => view.status === 'offline').length;
            const pending = views.filter(view => view.status === 'pending').length;
            const parts = [];
            if (normal) parts.push(normal + ' 个运行正常');
            if (warning) parts.push(warning + ' 个有提醒');
            if (offline) parts.push(offline + ' 个离线');
            if (pending) parts.push(pending + ' 个等待接入');
            return {
                text: '合作社共 ' + views.length + ' 个大棚，' + (parts.join('，') || '暂无数据'),
                level: warning || offline ? 'warn' : (pending ? 'muted' : '')
            };
        }
    },
    mounted() {
        this.init();
        this.snapshotSource = openSnapshotStream(batch => this.applySnapshots(batch));
        this.snapshotTimer = setInterval(this.loadSnapshots, 3000);
        this.pollTimer = setInterval(() => {
            this.loadMine();
            this.loadDevices();
            if (this.tab === 'orders') this.loadOrders();
        }, 3000);
        this.clockTimer = setInterval(() => {
            this.now = Date.now();
        }, 1000);
    },
    beforeUnmount() {
        clearInterval(this.snapshotTimer);
        clearInterval(this.pollTimer);
        clearInterval(this.clockTimer);
        if (this.snapshotSource) this.snapshotSource.close();
    },
    methods: {
        pad2(value) {
            return String(value).padStart(2, '0');
        },
        async init() {
            try {
                this.user = await API.get('/api/auth/me');
                this.applyForm.applicantName = this.user.username;
                this.orderForm.applicantName = this.user.username;
                await Promise.all([this.loadMine(), this.loadDevices(), this.loadSnapshots(), this.loadOrders()]);
            } catch (e) {
                // 401 已由 API 封装跳转
            }
        },
        switchTab(key) {
            this.tab = key;
            if (key === 'mine') this.loadMine();
            else if (key === 'orders') this.loadOrders();
            else if (key === 'devices') {
                this.loadDevices();
                this.loadSnapshots();
            }
        },
        async loadMine() {
            try {
                this.applications = await API.get('/api/service-applications/mine') || [];
            } catch (e) {
                // 轮询失败不打断页面
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
        async submitApply() {
            const f = this.applyForm;
            if (!f.greenhouseName) {
                this.applyError = '大棚名称不能为空';
                return;
            }
            if (!f.applicantName) {
                this.applyError = '申请人不能为空';
                return;
            }
            if (!f.phone || f.phone.length < 6) {
                this.applyError = '联系电话格式错误';
                return;
            }
            const points = [];
            for (const d of this.demandTypes) {
                const count = Number(d.count) || 0;
                if (count < 0 || count > 20 || !Number.isInteger(count)) {
                    this.applyError = d.label + ' 数量必须在 0~20 之间';
                    return;
                }
                if (count > 0) points.push({ type: d.type, count });
            }
            if (!points.length) {
                this.applyError = '至少选择一项监测项目';
                return;
            }

            this.submitting = true;
            try {
                await API.post('/api/service-applications', {
                    greenhouseName: f.greenhouseName,
                    applicantName: f.applicantName,
                    points,
                    phone: f.phone
                });
                showToast('申请已提交，等待审批', 'success');
                this.applyForm.greenhouseName = '';
                this.demandTypes.forEach(d => { d.count = d.type === 'temperature' || d.type === 'humidity' ? 1 : 0; });
                this.applyError = '';
                this.tab = 'mine';
                await this.loadMine();
            } catch (e) {
                this.applyError = e.message;
            } finally {
                this.submitting = false;
            }
        },
        pointTypeLabel(type) {
            return { temperature: '温度', humidity: '空气湿度', soil: '土壤湿度', light: '光照' }[type] || type;
        },
        appStatusLabel(status) {
            return { PENDING: '待审批', APPROVED: '已通过', REJECTED: '已驳回', INSTALLED: '已开通' }[status] || status;
        },
        appBadgeClass(status) {
            return { PENDING: 'badge-warn', REJECTED: 'badge-danger' }[status] || '';
        },
        async loadOrders() {
            try {
                this.orders = await API.get('/api/work-orders/mine') || [];
            } catch (e) {
                // 轮询失败不打断页面
            }
        },
        async submitOrder() {
            const f = this.orderForm;
            if (!f.greenhouseName) {
                this.orderError = '大棚名称不能为空';
                return;
            }
            if (!f.applicantName) {
                this.orderError = '联系人不能为空';
                return;
            }
            if (!f.phone || f.phone.length < 6) {
                this.orderError = '联系电话格式错误';
                return;
            }
            if (!f.description) {
                this.orderError = '问题描述不能为空';
                return;
            }
            this.submittingOrder = true;
            try {
                await API.post('/api/work-orders', {
                    greenhouseName: f.greenhouseName,
                    applicantName: f.applicantName,
                    phone: f.phone,
                    description: f.description
                });
                showToast('报修已提交', 'success');
                this.orderForm.greenhouseName = '';
                this.orderForm.description = '';
                this.orderError = '';
                await this.loadOrders();
            } catch (e) {
                this.orderError = e.message;
            } finally {
                this.submittingOrder = false;
            }
        },
        async cancelOrder(order) {
            if (!window.confirm('确认取消工单 ' + order.orderNo + '？')) return;
            this.actingOrderId = order.id;
            try {
                await API.post('/api/work-orders/' + order.id + '/cancel', {});
                showToast('工单已取消', 'success');
                await this.loadOrders();
            } catch (e) {
                showToast(e.message, 'error');
            } finally {
                this.actingOrderId = '';
            }
        },
        async confirmOrder(order) {
            if (!window.confirm('确认 ' + order.orderNo + ' 已处理完成？')) return;
            this.actingOrderId = order.id;
            try {
                await API.post('/api/work-orders/' + order.id + '/confirm', {});
                showToast('已验收完成', 'success');
                await this.loadOrders();
            } catch (e) {
                showToast(e.message, 'error');
            } finally {
                this.actingOrderId = '';
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
        orderTagClass(status) {
            return {
                PENDING: 'wait',
                IN_PROGRESS: 'wait',
                WAITING_ACCEPT: 'ok',
                DONE: 'ok',
                CANCELLED: 'muted'
            }[status] || '';
        },
        displayName(device) {
            return (device.name || device.deviceCode || '')
                .replace(/\b(modbus|mqtt)\b/gi, '')
                .replace(/\s+/g, ' ')
                .replace(/([\u4e00-\u9fa5])\s+(?=[\u4e00-\u9fa5])/g, '$1')
                .trim();
        },
        deviceState(entries) {
            const stamps = entries
                .map(entry => entry.snapshot && entry.snapshot.timestamp
                    ? new Date(entry.snapshot.timestamp).getTime() : 0)
                .filter(value => value > 0);
            const lastTs = stamps.length ? Math.max(...stamps) : 0;
            const online = lastTs > 0 && this.now - lastTs < 60000;
            const anomalies = [];
            let dataBad = false;

            if (online) {
                entries.forEach(entry => {
                    const snapshot = entry.snapshot;
                    if (!snapshot) return;
                    if (String(snapshot.quality || '').toUpperCase() === 'BAD') {
                        dataBad = true;
                        return;
                    }
                    const value = Number(snapshot.value);
                    if (!Number.isFinite(value)) return;
                    const kind = this.metricKind(entry.point);
                    if (entry.point.max != null && value > Number(entry.point.max)) {
                        anomalies.push(this.anomalyText(kind, entry.point, snapshot, 'high'));
                    } else if (entry.point.min != null && value < Number(entry.point.min)) {
                        anomalies.push(this.anomalyText(kind, entry.point, snapshot, 'low'));
                    }
                });
            }

            let status = 'normal';
            if (!entries.length || !lastTs) status = 'pending';
            else if (!online) status = 'offline';
            else if (dataBad || anomalies.length) status = 'warning';

            return { status, online, lastTs, pointCount: entries.length, anomalies };
        },
        snapshotOf(device, point) {
            return this.snapshots[device.deviceCode + ':' + point.pointCode] || null;
        },
        snapshotText(point, snapshot) {
            const unit = snapshot.unit || point.unit;
            return formatByUnit(snapshot.value, unit) + (unit ? ' ' + unit : '');
        },
        metricKind(point) {
            const text = ((point.metric || '') + ' ' + (point.pointCode || '')).toLowerCase();
            if (text.includes('soil') || text.includes('土壤')) return 'soil';
            if (text.includes('temp') || text.includes('温')) return 'temperature';
            if (text.includes('humi') || text.includes('湿')) return 'humidity';
            if (text.includes('light') || text.includes('lux') || text.includes('光照')) return 'light';
            return 'other';
        },
        metricLabel(kind) {
            return {
                temperature: '温度',
                humidity: '空气湿度',
                soil: '土壤湿度',
                light: '光照'
            }[kind] || '指标';
        },
        metricPriority(point) {
            return {
                temperature: 0,
                humidity: 1,
                soil: 2,
                light: 3
            }[this.metricKind(point)] ?? 4;
        },
        anomalyText(kind, point, snapshot, direction) {
            const label = point.metric || this.metricLabel(kind);
            const value = this.snapshotText(point, snapshot);
            if (kind === 'soil' && /湿|水分/.test(label)) {
                return (direction === 'high' ? '土壤偏湿' : '土壤偏干') + '，当前 ' + value;
            }
            if (kind === 'light') {
                return (direction === 'high' ? '光照偏强' : '光照不足') + '，当前 ' + value;
            }
            return label + (direction === 'high' ? '偏高' : '偏低') + '，当前 ' + value;
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
        fmtTime(value) {
            if (!value) return '-';
            const d = new Date(value);
            const pad = n => String(n).padStart(2, '0');
            return pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' +
                pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds());
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
