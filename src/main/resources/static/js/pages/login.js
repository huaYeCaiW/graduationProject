/* 页面逻辑：login.html */
const { createApp } = Vue;

createApp({
    data() {
        return {
            username: '',
            password: '',
            error: '',
            loading: false
        };
    },
    mounted() {
        // 已登录直接进对应角色工作台
        API.get('/api/auth/me')
            .then(user => location.href = homeForRole(user.role))
            .catch(() => {});
    },
    methods: {
        async login() {
            this.error = '';
            if (!this.username || !this.password) {
                this.error = '请输入用户名和密码';
                return;
            }
            this.loading = true;
            try {
                const user = await API.post('/api/auth/login', {
                    username: this.username,
                    password: this.password
                });
                location.href = homeForRole(user.role);
            } catch (e) {
                this.error = e.message;
            } finally {
                this.loading = false;
            }
        }
    }
}).mount('#app');
