/* 页面逻辑：monitor.html */
const { createApp } = Vue;

createApp({
    data() {
        return {
            user: null,
            devices: [],
            snapshots: {},
            activeAlarms: [],
            selected: null,
            history: [],
            rangeOptions: [
                { value: 1, label: '1h' },
                { value: 6, label: '6h' },
                { value: 24, label: '24h' },
                { value: null, label: '全部' }
            ],
            rangeHours: null,
            loadingHistory: false,
            snapshotTimer: null,
            snapshotSource: null,
            streamOk: false,
            alarmTimer: null
        };
    },
    computed: {
        roleLabel() {
            if (!this.user) return '';
            return { ADMIN: '管理员', OPS: '运维', FARMER: '种植户' }[this.user.role] || this.user.role;
        },
        navItems() {
            if (!this.user) return [];
            const items = [];
            if (this.user.role === 'ADMIN') items.push({ label: '管理端', href: '/admin.html' });
            else if (this.user.role === 'OPS') items.push({ label: '运维端', href: '/ops.html' });
            else if (this.user.role === 'FARMER') items.push({ label: '种植户端', href: '/farmer.html' });
            if (this.user.role !== 'FARMER') items.push({ label: '设备管理', href: '/devices.html' });
            if (this.user.role !== 'FARMER') items.push({ label: '实时监测', href: '/monitor.html' });
            return items;
        },
        pointCount() {
            return this.devices.reduce((sum, d) => sum + (d.points || []).length, 0);
        },
        chart() {
            const raw = this.history.filter(p => typeof p.value === 'number' && this.sampleTime(p));
            if (!raw.length) return null;

            // 数据量大时抽样画线，避免 SVG 节点过多
            const step = Math.max(1, Math.ceil(raw.length / 800));
            const list = raw.filter((_, index) => index % step === 0 || index === raw.length - 1);

            const w = 720, h = 240, padL = 56, padR = 18, padT = 18, padB = 30;
            let lo = Infinity, hi = -Infinity;
            for (const item of raw) {
                if (item.value < lo) lo = item.value;
                if (item.value > hi) hi = item.value;
            }
            const rawLo = lo;
            const rawHi = hi;

            // 百分比测点固定 0～100，刻度取整数；其他测点按数据自动缩放
            const unit = this.selected ? this.selected.unit : null;
            const isPercent = !!unit && unit.includes('%');
            let tickCount;
            if (isPercent) {
                lo = 0;
                hi = 100;
                tickCount = 5;
            } else {
                if (lo === hi) {
                    lo -= 1;
                    hi += 1;
                }
                const padding = (hi - lo) * 0.12;
                lo -= padding;
                hi += padding;
                tickCount = 4;
            }

            const t0 = new Date(this.sampleTime(list[0])).getTime();
            const t1 = new Date(this.sampleTime(list[list.length - 1])).getTime();
            const innerW = w - padL - padR;
            const innerH = h - padT - padB;

            const points = list.map(item => {
                const t = new Date(this.sampleTime(item)).getTime();
                const x = t1 === t0 ? padL + innerW / 2 : padL + (t - t0) / (t1 - t0) * innerW;
                const y = padT + (hi - item.value) / (hi - lo) * innerH;
                return { x: Number(x.toFixed(1)), y: Number(y.toFixed(1)) };
            });

            const ticks = [];
            for (let i = 0; i < tickCount; i++) {
                const ratio = i / (tickCount - 1);
                const value = hi - (hi - lo) * ratio;
                ticks.push({
                    y: Number((padT + innerH * ratio).toFixed(1)),
                    label: isPercent ? String(Math.round(value)) : this.fmtValue(value)
                });
            }

            return {
                w, h, padL, padR, padT, padB, points, ticks,
                polyline: points.map(p => p.x + ',' + p.y).join(' '),
                minLabel: this.fmtValue(rawLo),
                maxLabel: this.fmtValue(rawHi),
                startLabel: this.fmtTime(this.sampleTime(list[0])),
                endLabel: this.fmtTime(this.sampleTime(list[list.length - 1]))
            };
        }
    },
    mounted() {
        this.init();
        // SSE 实时推送，轮询只做断线兜底
        this.snapshotSource = openSnapshotStream(batch => this.applySnapshots(batch));
        this.snapshotTimer = setInterval(this.loadSnapshots, 3000);
        this.alarmTimer = setInterval(this.loadAlarms, 3000);
    },
    beforeUnmount() {
        clearInterval(this.snapshotTimer);
        clearInterval(this.alarmTimer);
        if (this.snapshotSource) this.snapshotSource.close();
    },
    methods: {
        async init() {
            try {
                this.user = await API.get('/api/auth/me');
                if (this.user.role === 'FARMER') {
                    location.href = '/farmer.html';
                    return;
                }
                await this.loadDevices();
                await this.loadSnapshots();
                await this.loadAlarms();
            } catch (e) {
                // 401 已由 API 封装跳转
            }
        },
        async loadDevices() {
            try {
                this.devices = await API.get('/api/devices') || [];
                if (!this.selected) {
                    const device = this.devices.find(d => (d.points || []).length);
                    if (device) {
                        await this.selectPoint(device, device.points[0]);
                    }
                }
            } catch (e) {
                showToast(e.message, 'error');
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
                this.streamOk = true;
            }
        },
        async loadAlarms() {
            try {
                this.activeAlarms = await API.get('/api/alarms/active') || [];
            } catch (e) {
                // 轮询失败不打断页面
            }
        },
        async selectPoint(device, point) {
            this.selected = {
                deviceCode: device.deviceCode,
                pointCode: point.pointCode,
                metric: point.metric,
                unit: point.unit,
                protocol: device.protocol
            };
            await this.loadHistory();
        },
        async loadHistory() {
            if (!this.selected) return;
            this.loadingHistory = true;
            try {
                const end = new Date();
                const start = this.rangeHours == null
                    ? new Date(0)
                    : new Date(end.getTime() - this.rangeHours * 3600 * 1000);
                const url = '/api/points/' + encodeURIComponent(this.selected.deviceCode) +
                    '/' + encodeURIComponent(this.selected.pointCode) + '/range' +
                    '?start=' + encodeURIComponent(start.toISOString()) +
                    '&end=' + encodeURIComponent(end.toISOString());
                const list = await API.get(url) || [];
                this.history = list.slice().sort((a, b) =>
                    new Date(this.sampleTime(a)) - new Date(this.sampleTime(b)));
            } catch (e) {
                this.history = [];
                showToast(e.message, 'error');
            } finally {
                this.loadingHistory = false;
            }
        },
        changeRange(hours) {
            this.rangeHours = hours;
            this.loadHistory();
        },
        async confirmAlarm(alarm) {
            try {
                await API.request('/api/alarms/' + alarm.id + '/confirm', { method: 'POST' });
                showToast('告警已确认', 'success');
                await this.loadAlarms();
            } catch (e) {
                showToast(e.message, 'error');
            }
        },
        isSelected(device, point) {
            return this.selected &&
                this.selected.deviceCode === device.deviceCode &&
                this.selected.pointCode === point.pointCode;
        },
        snapshotOf(device, point) {
            return this.snapshots[device.deviceCode + ':' + point.pointCode] || null;
        },
        valueText(device, point) {
            const snapshot = this.snapshotOf(device, point);
            if (!snapshot) return '-';
            const unit = snapshot.unit || point.unit;
            return formatByUnit(snapshot.value, unit) + (unit ? ' ' + unit : '');
        },
        isBad(device, point) {
            const snapshot = this.snapshotOf(device, point);
            return snapshot && snapshot.quality === 'BAD';
        },
        protocolLabel(protocol) {
            return protocol === 'MODBUS' ? 'Modbus TCP' : 'MQTT';
        },
        limitLabel(type) {
            return type === 'MAX' ? '上限' : '下限';
        },
        sampleTime(point) {
            return point.sampledAt || point.timestamp;
        },
        fmtTime(value) {
            if (!value) return '-';
            const d = new Date(value);
            const pad = n => String(n).padStart(2, '0');
            return pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' +
                pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds());
        },
        fmtValue(value) {
            return formatByUnit(value, this.selected ? this.selected.unit : null);
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
