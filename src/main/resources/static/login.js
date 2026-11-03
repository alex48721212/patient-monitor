'use strict';

const params = new URLSearchParams(location.search);
const message = document.querySelector('#login-message');

if (params.has('error')) {
    message.textContent = '아이디 또는 비밀번호가 올바르지 않거나, 가입 승인 대기 중입니다.';
    message.classList.add('error');
    message.hidden = false;
} else if (params.has('logout')) {
    message.textContent = '로그아웃되었습니다.';
    message.hidden = false;
}

document.querySelector('#login-form').addEventListener('submit', e => {
    const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
    e.target.elements._csrf.value = match ? decodeURIComponent(match[1]) : '';
});
