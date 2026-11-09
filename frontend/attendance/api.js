let csrf;

export async function api(path, data) {
  const options = { credentials: 'same-origin', cache: 'no-store', headers: { Accept: 'application/json' } };
  if (data !== undefined) {
    if (!csrf) {
      const token = await fetch('/api/csrf', options);
      if (!token.ok) throw new Error('보안 연결을 준비하지 못했습니다. 다시 시도해 주세요.');
      csrf = await token.json();
    }
    options.method = 'POST';
    options.headers['Content-Type'] = 'application/json';
    options.headers[csrf.headerName] = csrf.token;
    options.body = JSON.stringify(data);
  }
  const response = await fetch(path, options);
  if (!response.ok) {
    if (response.status === 403) csrf = undefined;
    const body = await response.json().catch(() => ({}));
    throw new Error(body.error || '요청을 처리하지 못했습니다. 다시 시도해 주세요.');
  }
  return response.status === 204 ? undefined : response.json();
}
