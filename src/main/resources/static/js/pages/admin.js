/* 页面逻辑：admin.html */
const { createApp } = Vue;

createApp({
    data() {
        return {
            user: null,
            tab: 'applications',
            tabs: [
                { key: 'applications', label: '申请审批' },
                { key: 'workOrders', label: '工单管理' },
                { key: 'overview', label: '数据概览' }
            ],
            applications: [],
            appStatus: '',
            workOrders: [],
            orderStatus: '',
            devices: [],
            activeAlarms: [],
            overviewApplications: [],
            overviewOrders: [],
            loading: { applications: false, workOrders: false, overview: false },
            dialog: null,
            reason: '',
            dialogError: '',
            dialogBusy: false,
            pollTimer: null,
            tick: 0
        };
    },
    computed: {
        roleLabel() {
            return this.user ? roleText(this.user.role) : '';
        },
        navItems() {
            if (!this.user) return [];
            return [
                { label: '管理端', href: '/admin.html' },
                { label: '设备管理', href: '/devices.html' },
                { label: '实时监测', href: '/monitor.html' }
            ];
        },
        pointCount() {
            return this.devices.reduce((sum, d) => sum + (d.points || []).length, 0);
        },
        pendingApplications() {
            return this.overviewApplications.filter(a => a.status === 'PENDING');
        },
        inProgressCount() {
            return this.overviewOrders.filter(o => o.status === 'IN_PROGRESS').length;
        },
        waitingAcceptCount() {
            return this.overviewOrders.filter(o => o.status === 'WAITING_ACCEPT').length;
        },
        todayText() {
            const d = new Date();
            const week = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'][d.getDay()];
            return d.getFullYear() + '年' + (d.getMonth() + 1) + '月' + d.getDate() + '日 ' + week;
        },
        dialogKicker() {
            if (!this.dialog) return '';
            return { approve: 'REVIEW · 审批', reject: 'REJECT · 驳回', cancel: 'CANCEL · 取消' }[this.dialog.type];
        },
        dialogTitle() {
            if (!this.dialog) return '';
            return { approve: '通过申请', reject: '驳回申请', cancel: '取消工单' }[this.dialog.type];
        },
        dialogText() {
            if (!this.dialog) return '';
            const no = this.dialog.target.applicationNo || this.dialog.target.orderNo;
            if (this.dialog.type === 'approve') {
                return '确认通过 ' + no + '？通过后由运维按现场设备配置安排开通，申请人可在种植户端查看进度。';
            }
            if (this.dialog.type === 'reject') {
                return '驳回 ' + no + '，原因会展示给申请人。';
            }
            return '取消 ' + no + ' 后将无法继续流转，请确认。';
        },
        dialogConfirmText() {
            if (!this.dialog) return '';
            return { approve: '确认通过', reject: '确认驳回', cancel: '确认取消' }[this.dialog.type];
        }
    },
    mounted() {
        this.init();
        document.addEventListener('keydown', this.onKeydown);
        this.pollTimer = setInterval(() => {
            this.tick++;
            if (this.tab === 'applications') this.loadApplications();
            else if (this.tab === 'workOrders') this.loadWorkOrders();
            else this.loadOverview();
            // 其余页签下每 6 秒刷新一次概览，保证抬头数字不过期
            if (this.tab !== 'overview' && this.tick % 2 === 0) this.loadOverview();
        }, 3000);
    },
    beforeUnmount() {
        clearInterval(this.pollTimer);
        document.removeEventListener('keydown', this.onKeydown);
    },
    methods: {
        async init() {
            try {
                this.user = await API.get('/api/auth/me');
                await Promise.all([this.loadApplications(), this.loadOverview(), this.loadWorkOrders()]);
            } catch (e) {
                // 401 已由 API 封装跳转
            }
        },
        onKeydown(e) {
            if (e.key === 'Escape' && this.dialog && !this.dialogBusy) this.closeDialog();
        },
        switchTab(key) {
            this.tab = key;
            if (key === 'applications') this.loadApplications();
            else if (key === 'overview') this.loadOverview();
            else if (key === 'workOrders') this.loadWorkOrders();
        },
        async loadApplications() {
            this.loading.applications = true;
            try {
                const url = '/api/service-applications' +
                    (this.appStatus ? '?status=' + encodeURIComponent(this.appStatus) : '');
                this.applications = await API.get(url) || [];
            } catch (e) {
                showToast(e.message, 'error');
            } finally {
                this.loading.applications = false;
            }
        },
        async loadOverview() {
            this.loading.overview = true;
            try {
                const [devices, alarms, applications, orders] = await Promise.all([
                    API.get('/api/devices'),
                    API.get('/api/alarms/active'),
                    API.get('/api/service-applications'),
                    API.get('/api/work-orders')
                ]);
                this.devices = devices || [];
                this.activeAlarms = alarms || [];
                this.overviewApplications = applications || [];
                this.overviewOrders = orders || [];
            } catch (e) {
                showToast(e.message, 'error');
            } finally {
                this.loading.overview = false;
            }
        },
        async loadWorkOrders() {
            this.loading.workOrders = true;
            try {
                const url = '/api/work-orders' +
                    (this.orderStatus ? '?status=' + encodeURIComponent(this.orderStatus) : '');
                this.workOrders = await API.get(url) || [];
            } catch (e) {
                showToast(e.message, 'error');
            } finally {
                this.loading.workOrders = false;
            }
        },
        openDialog(type, target) {
            this.dialog = { type, target };
            this.reason = '';
            this.dialogError = '';
            this.dialogBusy = false;
        },
        closeDialog() {
            if (this.dialogBusy) return;
            this.dialog = null;
            this.reason = '';
            this.dialogError = '';
        },
        async confirmDialog() {
            if (!this.dialog) return;
            const type = this.dialog.type;
            const target = this.dialog.target;
            const reason = this.reason.trim();

            if (type !== 'approve') {
                if (!reason) {
                    this.dialogError = type === 'reject' ? '请填写驳回原因' : '请填写取消原因';
                    return;
                }
                if (reason.length > 255) {
                    this.dialogError = '原因不能超过 255 字';
                    return;
                }
            }

            this.dialogBusy = true;
            this.dialogError = '';
            try {
                if (type === 'approve') {
                    await API.post('/api/service-applications/' + target.id + '/approve', {});
                    showToast('申请已通过', 'success');
                    await this.loadApplications();
                } else if (type === 'reject') {
                    await API.post('/api/service-applications/' + target.id + '/reject', { reason });
                    showToast('申请已驳回', 'success');
                    await this.loadApplications();
                } else {
                    await API.post('/api/work-orders/' + target.id + '/cancel', { reason });
                    showToast('工单已取消', 'success');
                    await this.loadWorkOrders();
                }
                await this.loadOverview();
                this.dialog = null;
                this.reason = '';
            } catch (e) {
                this.dialogError = e.message;
            } finally {
                this.dialogBusy = false;
            }
        },
        pointTypeLabel(type) {
            return { temperature: '温度', humidity: '湿度', soil: '土壤', light: '光照' }[type] || type;
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
                PENDING: 'stamp-warn',
                IN_PROGRESS: 'stamp-info',
                WAITING_ACCEPT: 'stamp-warn',
                DONE: 'stamp-ok',
                CANCELLED: 'stamp-mute'
            }[status] || 'stamp-mute';
        },
        appStatusLabel(status) {
            return { PENDING: '待审批', APPROVED: '已通过', REJECTED: '已驳回', INSTALLED: '已开通' }[status] || status;
        },
        appBadgeClass(status) {
            return {
                PENDING: 'stamp-warn',
                APPROVED: 'stamp-ok',
                REJECTED: 'stamp-danger',
                INSTALLED: 'stamp-ok'
            }[status] || 'stamp-mute';
        },
        fmtTime(value) {
            if (!value) return '—';
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
