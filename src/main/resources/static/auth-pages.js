'use strict';

// 로그인 전 화면(회원가입 등)에서 함께 쓰는 스크립트

function showMessage(text, isError = false) {
    const el = document.querySelector('#auth-message');
    el.textContent = text;
    el.classList.toggle('error', isError);
    el.hidden = false;
}

async function postJson(path, body) {
    const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
    const res = await fetch(path, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-XSRF-TOKEN': match ? decodeURIComponent(match[1]) : '',
        },
        body: JSON.stringify(body),
    });
    if (!res.ok) {
        let detail = '요청을 처리하지 못했습니다. 잠시 후 다시 시도하세요.';
        try { detail = (await res.json()).detail || detail; } catch { /* 본문 없음 */ }
        throw new Error(detail);
    }
}

const signupForm = document.querySelector('#signup-form');
if (signupForm) {
    signupForm.addEventListener('submit', async e => {
        e.preventDefault();
        const { passwordConfirm, ...body } = Object.fromEntries(new FormData(signupForm));
        if (body.password !== passwordConfirm) {
            showMessage('비밀번호 확인이 일치하지 않습니다.', true);
            return;
        }
        try {
            await postJson('/api/auth/signup', body);
            signupForm.hidden = true;
            showMessage('가입 신청이 접수되었습니다. 관리자가 승인하면 로그인할 수 있습니다.');
        } catch (err) {
            showMessage(err.message, true);
        }
    });
}
