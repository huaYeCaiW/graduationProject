/* 页面逻辑：index.html */
(function () {
    const status = document.getElementById('status');
    const loginLink = document.getElementById('login-link');
    loginLink.style.display = 'none';

    API.get('/api/auth/me')
        .then(function (user) {
            location.replace(homeForRole(user.role));
        })
        .catch(function () {
            status.textContent = '请先登录';
            loginLink.style.display = '';
            location.replace('/login.html');
        });
})();
