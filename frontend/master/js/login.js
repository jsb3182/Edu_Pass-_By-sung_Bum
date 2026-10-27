// 서버가 제공하는 CSRF 토큰을 로그인 양식에 넣습니다.
async function prepareLogin() {
  const message = document.getElementById('loginMessage');
  const button = document.getElementById('loginButton');
  const params = new URLSearchParams(window.location.search);

  if (params.has('error')) {
    message.textContent = '계정명 또는 비밀번호를 확인해 주세요.';
    message.hidden = false;
  } else if (params.has('logout') || params.has('passwordChanged')) {
    message.textContent = '다시 로그인해 주세요.';
    message.classList.remove('error');
    message.hidden = false;
  }

  try {
    const response = await fetch('/api/csrf', {
      credentials: 'same-origin',
      cache: 'no-store'
    });
    if (!response.ok) throw new Error('로그인 화면을 준비하지 못했습니다.');
    const csrf = await response.json();
    document.getElementById('loginCsrf').value = csrf.token;
    button.disabled = false;
    button.textContent = '로그인';
  } catch (error) {
    message.textContent = error.message;
    message.classList.add('error');
    message.hidden = false;
    button.textContent = '로그인 불가';
  }
}

prepareLogin();
