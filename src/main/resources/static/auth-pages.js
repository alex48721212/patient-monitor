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

const resetRequestForm = document.querySelector('#reset-request-form');
if (resetRequestForm) {
    const resetConfirmForm = document.querySelector('#reset-confirm-form');

    resetRequestForm.addEventListener('submit', async e => {
        e.preventDefault();
        try {
            await postJson('/api/auth/password-reset/request', Object.fromEntries(new FormData(resetRequestForm)));
            resetRequestForm.hidden = true;
            resetConfirmForm.hidden = false;
            showMessage('입력한 정보와 일치하는 계정이 있으면 이메일로 인증 코드를 보냈습니다. 코드는 10분 동안 유효합니다.');
        } catch (err) {
            showMessage(err.message, true);
        }
    });

    resetConfirmForm.addEventListener('submit', async e => {
        e.preventDefault();
        const { newPasswordConfirm, ...body } = Object.fromEntries(new FormData(resetConfirmForm));
        if (body.newPassword !== newPasswordConfirm) {
            showMessage('새 비밀번호 확인이 일치하지 않습니다.', true);
            return;
        }
        body.username = resetRequestForm.elements.username.value;
        try {
            await postJson('/api/auth/password-reset/confirm', body);
            resetConfirmForm.hidden = true;
            showMessage('비밀번호를 변경했습니다. 새 비밀번호로 로그인하세요.');
        } catch (err) {
            showMessage(err.message, true);
        }
    });
}

const findIdForm = document.querySelector('#find-id-form');
if (findIdForm) {
    findIdForm.addEventListener('submit', async e => {
        e.preventDefault();
        try {
            await postJson('/api/auth/find-id', Object.fromEntries(new FormData(findIdForm)));
            findIdForm.hidden = true;
            showMessage('입력한 정보와 일치하는 계정이 있으면 가입한 이메일로 아이디를 보냈습니다.');
        } catch (err) {
            showMessage(err.message, true);
        }
    });
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
