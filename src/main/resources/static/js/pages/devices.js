/* 页面逻辑：devices.html */
const { createApp } = Vue;

function numOrNull(value) {
    if (value === '' || value === null || value === undefined) return null;
    const number = Number(value);
    return Number.isNaN(number) ? null : number;
}

if (new URLSearchParams(location.search).get('embed') === '1') {
    document.body.classList.add('embed');
}

function emptyDeviceForm() {
    return { deviceCode: '', name: '', tenantId: '', protocol: 'mqtt', connectionConfig: '' };
}

function emptyPointForm() {
    return {
        pointCode: '', name: '', address: '', timeAddress: '', dataType: 'double',
        unit: '', freqSeconds: 5, minValue: '', maxValue: '', rangeMin: '', rangeMax: ''
    };
}

createApp({
    data() {
        return {
            user: null,
            embedded: new URLSearchParams(location.search).get('embed') === '1',
            devices: [],
            current: null,
            snapshots: {},
            thresholdDraft: {},
            savingKey: '',
            snapshotTimer: null,
            snapshotSource: null,
            lastModalCloseAt: 0,
            keyword: '',
            loading: false,
            saving: false,
            deleting: false,
            deviceModal: { open: false, mode: 'create' },
            pointModal: { open: false, mode: 'create' },
            disabledModal: { open: false, list: [] },
            confirmState: null,
            deviceForm: emptyDeviceForm(),
            pointForm: emptyPointForm()
        };
    },
    computed: {
        roleLabel() {
            if (!this.user) return '';
            return { ADMIN: '管理员', OPS: '运维', FARMER: '种植户' }[this.user.role] || this.user.role;
        },
        isAdmin() {
            return !!this.user && this.user.role === 'ADMIN' && !this.embedded;
        },
        navItems() {
            if (!this.user) return [];
            const items = [];
            if (this.user.role === 'ADMIN') items.push({ label: '管理端', href: '/admin.html' });
            else if (this.user.role === 'OPS') items.push({ label: '运维端', href: '/ops.html' });
            else if (this.user.role === 'FARMER') items.push({ label: '种植户端', href: '/farmer.html' });
            items.push({ label: '设备管理', href: '/devices.html' });
            items.push({ label: '实时监测', href: '/monitor.html' });
            return items;
        },
        filteredDevices() {
            const key = this.keyword.toLowerCase();
            if (!key) return this.devices;
            return this.devices.filter(d =>
                (d.deviceCode || '').toLowerCase().includes(key) ||
                (d.name || '').toLowerCase().includes(key));
        },
        points() {
            return this.current && Array.isArray(this.current.points) ? this.current.points : [];
        },
        pointTotal() {
            return this.devices.reduce((sum, d) => sum + (d.points || []).length, 0);
        },
        hasInvalidPoint() {
            return this.points.some(p => p.invalid);
        }
    },
    mounted() {
        this.resetModals();
        window.addEventListener('pageshow', this.onPageShow);
        this.init();
        this.loadSnapshots();
        this.snapshotSource = openSnapshotStream(batch => this.applySnapshots(batch));
        this.snapshotTimer = setInterval(this.loadSnapshots, 5000);
    },
    beforeUnmount() {
        window.removeEventListener('pageshow', this.onPageShow);
        clearInterval(this.snapshotTimer);
        if (this.snapshotSource) this.snapshotSource.close();
    },
    methods: {
        resetModals() {
            this.deviceModal = { open: false, mode: 'create' };
            this.pointModal = { open: false, mode: 'create' };
            this.disabledModal = { open: false, list: [] };
            this.confirmState = null;
        },
        onPageShow(e) {
            if (e.persisted) this.resetModals();
        },
        async init() {
            try {
                this.user = await API.get('/api/auth/me');
                await this.loadDevices();
            } catch (e) {
                // 401 已由 API 封装跳转
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
        async loadDevices() {
            this.loading = true;
            try {
                this.devices = await API.get('/api/devices') || [];
                const draft = {};
                for (const d of this.devices) {
                    for (const p of (d.points || [])) {
                        draft[d.deviceCode + ':' + p.pointCode] = {
                            min: p.min == null ? '' : p.min,
                            max: p.max == null ? '' : p.max
                        };
                    }
                }
                this.thresholdDraft = draft;
                if (this.current) {
                    const exists = this.devices.some(d => d.deviceCode === this.current.deviceCode);
                    if (exists) {
                        await this.selectDevice(this.current.deviceCode);
                    } else {
                        this.current = null;
                    }
                }
                if (!this.current && this.devices.length) {
                    await this.selectDevice(this.devices[0].deviceCode);
                }
            } catch (e) {
                showToast(e.message, 'error');
            } finally {
                this.loading = false;
            }
        },
        async selectDevice(deviceCode) {
            try {
                this.current = await API.get('/api/devices/' + encodeURIComponent(deviceCode));
            } catch (e) {
                showToast(e.message, 'error');
            }
        },
        closeDeviceModal() {
            if (this.saving) return;
            this.deviceModal.open = false;
            this.afterModalClose();
        },
        closePointModal() {
            if (this.saving) return;
            this.pointModal.open = false;
            this.afterModalClose();
        },
        afterModalClose() {
            this.lastModalCloseAt = performance.now();
            if (document.activeElement && document.activeElement.blur) document.activeElement.blur();
        },
        openCreateDevice() {
            this.deviceForm = emptyDeviceForm();
            if (this.user && this.user.tenantId) {
                this.deviceForm.tenantId = this.user.tenantId;
            }
            this.deviceModal = { open: true, mode: 'create' };
        },
        openEditDevice(device, event) {
            if (!device || !event || !event.isTrusted) return;
            if (performance.now() - this.lastModalCloseAt < 350) return;
            this.deviceForm = {
                deviceCode: device.deviceCode,
                name: device.name || '',
                tenantId: device.tenantId == null ? '' : device.tenantId,
                protocol: (device.protocol || 'MQTT').toLowerCase(),
                connectionConfig: device.connection && device.connection.config ? device.connection.config : ''
            };
            this.deviceModal = { open: true, mode: 'edit' };
        },
        async openDisabled() {
            try {
                const list = await API.get('/api/devices/disabled') || [];
                this.disabledModal = { open: true, list };
            } catch (e) {
                showToast(e.message, 'error');
            }
        },
        openRestoreDevice(device) {
            this.disabledModal.open = false;
            this.deviceForm = {
                deviceCode: device.deviceCode,
                name: device.name || '',
                tenantId: device.tenantId == null ? '' : device.tenantId,
                protocol: (device.protocol || 'MQTT').toLowerCase(),
                connectionConfig: device.connectionConfig || ''
            };
            this.deviceModal = { open: true, mode: 'restore' };
        },
        async saveDevice() {
            const f = this.deviceForm;
            if (!f.deviceCode || !f.name) {
                showToast('设备编码和名称不能为空', 'error');
                return;
            }
            const body = {
                deviceCode: f.deviceCode,
                tenantId: f.tenantId === '' || f.tenantId == null ? null : Number(f.tenantId),
                name: f.name,
                protocol: f.protocol,
                connectionConfig: f.connectionConfig || null,
                enabled: this.deviceModal.mode === 'restore' ? true : null
            };
            this.saving = true;
            try {
                if (this.deviceModal.mode === 'create') {
                    await API.post('/api/devices', body);
                    showToast('设备已创建', 'success');
                } else {
                    await API.put('/api/devices/' + encodeURIComponent(f.deviceCode), body);
                    showToast(this.deviceModal.mode === 'restore' ? '设备已恢复' : '设备已更新', 'success');
                }
                const code = f.deviceCode;
                this.deviceModal.open = false;
                await this.loadDevices();
                await this.selectDevice(code);
            } catch (e) {
                showToast(e.message, 'error');
            } finally {
                this.saving = false;
            }
        },
        askRemoveDevice(device) {
            this.confirmState = {
                type: 'device',
                target: device,
                title: '删除设备',
                text: '删除 ' + device.deviceCode + '？历史数据会保留，可在“已删除设备”中恢复。'
            };
        },
        askRemovePoint(point) {
            this.confirmState = {
                type: 'point',
                target: point,
                title: '删除测点',
                text: '删除测点 ' + point.pointCode + '？该测点的实时值将停止更新。'
            };
        },
        closeConfirm() {
            if (this.deleting) return;
            this.confirmState = null;
        },
        async runConfirm() {
            if (!this.confirmState) return;
            const state = this.confirmState;
            this.deleting = true;
            try {
                if (state.type === 'device') {
                    await API.del('/api/devices/' + encodeURIComponent(state.target.deviceCode));
                    showToast('设备已删除', 'success');
                    if (this.current && this.current.deviceCode === state.target.deviceCode) {
                        this.current = null;
                    }
                    await this.loadDevices();
                } else {
                    await API.del('/api/points/' + encodeURIComponent(this.current.deviceCode) +
                        '/' + encodeURIComponent(state.target.pointCode));
                    showToast('测点已删除', 'success');
                    await this.loadDevices();
                }
                this.confirmState = null;
            } catch (e) {
                showToast(e.message, 'error');
            } finally {
                this.deleting = false;
            }
        },
        openCreatePoint() {
            this.pointForm = emptyPointForm();
            if (this.current && this.current.protocol === 'MODBUS') {
                this.pointForm.dataType = 'float32';
            }
            this.pointModal = { open: true, mode: 'create' };
        },
        openEditPoint(point, event) {
            if (!point || !event || !event.isTrusted) return;
            if (performance.now() - this.lastModalCloseAt < 350) return;
            this.pointForm = {
                pointCode: point.pointCode,
                name: point.metric || '',
                address: this.pointAddress(point),
                timeAddress: point.timeJsonPath || '',
                dataType: point.dataTypeCode || (this.current.protocol === 'MODBUS' ? 'float32' : 'double'),
                unit: point.unit || '',
                freqSeconds: point.freq == null ? '' : point.freq,
                minValue: point.min == null ? '' : point.min,
                maxValue: point.max == null ? '' : point.max,
                rangeMin: point.rangeMin == null ? '' : point.rangeMin,
                rangeMax: point.rangeMax == null ? '' : point.rangeMax
            };
            this.pointModal = { open: true, mode: 'edit' };
        },
        async savePoint() {
            if (!this.current) return;
            const f = this.pointForm;
            if (!f.pointCode || !f.name || !f.address || !f.dataType) {
                showToast('编码、名称、地址、数据类型不能为空', 'error');
                return;
            }
            const body = {
                pointCode: f.pointCode,
                name: f.name,
                address: f.address,
                timeAddress: f.timeAddress || null,
                dataType: f.dataType,
                unit: f.unit || null,
                freqSeconds: f.freqSeconds === '' || f.freqSeconds == null ? null : Number(f.freqSeconds),
                minValue: this.numOrNull(f.minValue),
                maxValue: this.numOrNull(f.maxValue),
                rangeMin: this.numOrNull(f.rangeMin),
                rangeMax: this.numOrNull(f.rangeMax)
            };
            this.saving = true;
            try {
                const base = '/api/points/' + encodeURIComponent(this.current.deviceCode);
                if (this.pointModal.mode === 'create') {
                    await API.post(base, body);
                    showToast('测点已创建', 'success');
                } else {
                    await API.put(base + '/' + encodeURIComponent(f.pointCode), body);
                    showToast('测点已更新', 'success');
                }
                this.pointModal.open = false;
                await this.loadDevices();
            } catch (e) {
                showToast(e.message, 'error');
            } finally {
                this.saving = false;
            }
        },
        async logout() {
            try {
                await API.post('/api/auth/logout', {});
            } catch (e) {
                // 忽略，统一回登录页
            }
            location.href = '/login.html';
        },
        numOrNull(value) {
            return value === '' || value == null ? null : Number(value);
        },
        protocolLabel(protocol) {
            return protocol === 'MODBUS' ? 'Modbus TCP' : 'MQTT';
        },
        connectionText(device) {
            return device.connection && device.connection.config ? device.connection.config : '未配置';
        },
        pointAddress(point) {
            if (this.current && this.current.protocol === 'MODBUS') {
                return point.addressText || '-';
            }
            return point.jsonPath || '-';
        },
        thresholdText(point) {
            if (point.min == null && point.max == null) return '未设置';
            return (point.min == null ? '-' : point.min) + ' ~ ' + (point.max == null ? '-' : point.max);
        },
        draftOf(point) {
            const key = (this.current ? this.current.deviceCode : '') + ':' + point.pointCode;
            if (!this.thresholdDraft[key]) {
                this.thresholdDraft[key] = {
                    min: point.min == null ? '' : point.min,
                    max: point.max == null ? '' : point.max
                };
            }
            return this.thresholdDraft[key];
        },
        async saveThreshold(point) {
            if (!this.current) return;
            const draft = this.draftOf(point);
            const min = numOrNull(draft.min);
            const max = numOrNull(draft.max);
            if (min != null && max != null && min > max) {
                showToast('阈值下限不能大于上限', 'error');
                return;
            }
            this.savingKey = point.pointCode;
            try {
                await API.put('/api/points/' + encodeURIComponent(this.current.deviceCode) +
                    '/' + encodeURIComponent(point.pointCode) + '/threshold', { min, max });
                showToast('阈值已更新', 'success');
                await this.loadDevices();
            } catch (e) {
                showToast(e.message, 'error');
            } finally {
                this.savingKey = '';
            }
        },
        rangeText(point) {
            if (point.rangeMin == null && point.rangeMax == null) return '不校验';
            return (point.rangeMin == null ? '-' : point.rangeMin) + ' ~ ' + (point.rangeMax == null ? '-' : point.rangeMax);
        },
        snapshotOf(point) {
            if (!this.current) return null;
            return this.snapshots[this.current.deviceCode + ':' + point.pointCode] || null;
        },
        snapshotText(point) {
            const snapshot = this.snapshotOf(point);
            if (!snapshot) return '-';
            const unit = snapshot.unit || point.unit;
            return formatByUnit(snapshot.value, unit) + (unit ? ' ' + unit : '');
        },
        snapshotQuality(point) {
            const snapshot = this.snapshotOf(point);
            return snapshot ? snapshot.quality : null;
        }
    }
}).mount('#app');
