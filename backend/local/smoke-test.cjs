// Node.js 20+ 필요. 실행 중인 로컬 서버에 검증용 신청 내역과 가짜 서명을 각각 1건 저장합니다.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { createHash, randomUUID } = require('node:crypto');

// 160×80 흰 배경에 검은 곡선을 그린 테스트 PNG입니다.
const testSignature = 'iVBORw0KGgoAAAANSUhEUgAAAKAAAABQCAAAAAC7NiHoAAABI0lEQVR4nO2YQQ7CMAwE8/9PwwGpB3AS27OBSuzcqsS7o1ZVA+Nxc8avBXZYkGJBigUpFqRYkGJBigUpFqRYkGJBigUpFqSoBMeFKPAKloS8oci8snlChMTtFU/nZ4j8qODUT6aIYkIbsSIImYsoDfsZSwmdYjthJ6AybAZk6jWKvfFktcKwM53vFRg2hkutWLE+WqykhtXJRh8zLA627ge6ibWxZhMxrEyBnsKLv7mWlPSHP7d8723Mfnt6gorP1jYjXE5Vis4m65jJ4sf10dPdNGrMV8KdwaDofBzlLTtiwQCF3rJg0pEUVOktFePdmWmlXrUkeuqH9Wodp3/zLMiV/M2/W8ewIMWCFAtSLEixIMWCFAtSLEixIMWCFAtSbi/4BGUsSUxRP1TMAAAAAElFTkSuQmCC';

function readConfig(file = path.join(__dirname, '.env')) {
  const config = {};
  for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
    const match = line.match(/^([A-Z_]+)=(.*)$/);
    if (match) config[match[1]] = match[2].trim();
  }
  return config;
}

function browser(base) {
  const cookies = new Map();
  return async (route, options = {}) => {
    const response = await fetch(base + route, {
      ...options,
      redirect: 'manual',
      signal: AbortSignal.timeout(15000),
      headers: {
        Cookie: [...cookies].map(([key, value]) => `${key}=${value}`).join('; '),
        ...options.headers
      }
    });
    for (const cookie of response.headers.getSetCookie()) {
      const pair = cookie.split(';')[0];
      const split = pair.indexOf('=');
      cookies.set(pair.slice(0, split), pair.slice(split + 1));
    }
    return response;
  };
}

async function main() {
  const config = readConfig();
  const base = `http://127.0.0.1:${config.LOCAL_HTTP_PORT || '8080'}`;
  const admin = browser(base);
  const visitor = browser(base);
  const json = async response => {
    assert.equal(response.status, 200, 'API 응답은 200이어야 합니다.');
    return response.json();
  };
  const post = (body, csrf) => ({
    method: 'POST',
    headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
    body: JSON.stringify(body)
  });
  const expectError = async (response, status) => {
    assert.equal(response.status, status);
    assert.ok(response.headers.get('content-type')?.includes('application/json'));
    assert.equal(typeof (await response.json()).error, 'string');
  };

  assert.equal((await json(await admin('/actuator/health'))).status, 'UP');
  await expectError(await visitor('/api/admin/session'), 401);
  assert.equal((await admin('/master/index.html')).status, 302);
  assert.equal((await visitor('/user/index.html')).status, 200);
  let csrf = await json(await admin('/api/csrf'));
  const login = await admin('/login', {
    method: 'POST',
    body: new URLSearchParams({
      username: config.BOOTSTRAP_ADMIN_USER,
      password: config.BOOTSTRAP_ADMIN_PASSWORD,
      _csrf: csrf.token
    })
  });
  assert.equal(login.status, 302);
  assert.ok(login.headers.get('location').endsWith('/master/index.html'), '로그인 성공 경로 확인');
  const session = await json(await admin('/api/admin/session'));
  assert.equal(session.mode, 'local');
  assert.equal(session.role, 'ROLE_ADMIN');
  assert.equal((await admin('/master/index.html')).status, 200);
  csrf = await json(await admin('/api/csrf'));

  // 로그인 이후에도 CSRF 없이 쓰기를 수행할 수 없어야 합니다.
  await expectError(await admin('/api/admin/tasks', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}'
  }), 403);
  await expectError(await admin('/api/admin/tasks?page=abc'), 400);
  await expectError(await admin('/api/admin/tasks/not-a-uuid'), 400);
  await expectError(await admin('/api/admin/tasks', { ...post({}, csrf), body: '{' }), 400);
  await expectError(await admin('/api/admin/imports', {
    method: 'POST', headers: { [csrf.headerName]: csrf.token }, body: new FormData()
  }), 400);
  const oversizedFile = new FormData();
  oversizedFile.set('file', new Blob([new Uint8Array(10 * 1024 * 1024 + 1)]), 'oversized.xlsx');
  await expectError(await admin('/api/admin/imports', {
    method: 'POST', headers: { [csrf.headerName]: csrf.token }, body: oversizedFile
  }), 413);
  await expectError(await admin('/api/admin/users', post({
    username: `invalid-${randomUUID()}`, password: '한'.repeat(25), role: 'OPERATOR'
  }, csrf)), 400);
  const task = await json(await admin('/api/admin/tasks', post({
    applicationNo: `SMOKE-${randomUUID()}`,
    applicantName: '로컬 연결 검증용', schoolName: '테스트 학교', status: '접수'
  }, csrf)));
  assert.equal((await json(await admin(`/api/admin/tasks/${task.id}`))).id, task.id);
  const page = await json(await admin(`/api/admin/tasks?query=${encodeURIComponent(task.applicationNo)}&page=0`));
  assert.equal(page.total, 1);
  assert.equal(page.page, 0);
  assert.equal(page.size, 50);
  assert.equal(page.items[0].id, task.id);
  assert.equal((await admin('/api/admin/tasks', post({
    applicationNo: task.applicationNo, applicantName: '중복 검증', schoolName: '학교', status: '접수'
  }, csrf))).status, 409);
  assert.equal((await admin('/api/admin/tasks', post({
    applicationNo: 'INVALID', applicantName: '', schoolName: '학교', status: '접수'
  }, csrf))).status, 400);

  const changeStatus = status => admin(`/api/admin/tasks/${task.id}/status`, {
    ...post({ status, version: task.version }, csrf), method: 'PUT'
  });
  const updated = await json(await changeStatus('심사'));
  assert.equal(updated.version, task.version + 1);
  assert.equal(updated.status, '심사');
  assert.equal((await changeStatus('덮어쓰기')).status, 409);
  assert.equal((await json(await admin(`/api/admin/tasks/${task.id}`))).status, '심사');
  const signerName = ('테스트 방문자 ' + '가'.repeat(200)).slice(0, 200);
  const issued = await json(await admin('/api/admin/signing-sessions', post({
    taskId: task.id, signerName, affiliation: '개발 테스트', purpose: 'OTHER'
  }, csrf)));
  const url = new URL(issued.url);
  assert.equal(url.pathname, '/user/index.html');
  assert.equal(url.origin, new URL(config.APP_PUBLIC_BASE_URL).origin);
  const token = new URLSearchParams(url.hash.slice(1)).get('token');
  assert.ok(token);
  const visitorCsrf = await json(await visitor('/api/csrf'));
  const details = await json(await visitor('/api/public/signing-sessions/lookup', post({ token }, visitorCsrf)));
  assert.equal(details.signerName, signerName);
  assert.equal(details.applicationNo, task.applicationNo);

  const data = {
    token, pngBase64: testSignature, consent: true, locationConsent: false, locationStatus: 'NOT_REQUESTED',
    locationCapturedAt: '2026-01-01T00:00:00Z', latitude: 37.1, longitude: 127.0, accuracyM: 10
  };
  const submit = overrides => visitor('/api/public/signatures', post({ ...data, ...overrides }, visitorCsrf));
  const signaturesRoute = `/api/admin/tasks/${task.id}/signatures`;
  await expectError(await submit({ pngBase64: 'invalid-png' }), 400);
  await expectError(await submit({ locationStatus: 'AVAILABLE' }), 400);
  assert.deepEqual(await json(await admin(signaturesRoute)), []);
  assert.equal((await json(await visitor('/api/public/signing-sessions/lookup', post({ token }, visitorCsrf))))
    .applicationNo, task.applicationNo, '저장 실패 후에도 같은 링크로 다시 시도할 수 있어야 합니다.');

  const attempts = await Promise.all([submit({}), submit({})]);
  assert.deepEqual(attempts.map(response => response.status).sort(), [200, 409]);
  const receipt = await json(attempts.find(response => response.ok));
  await expectError(attempts.find(response => !response.ok), 409);
  await expectError(await visitor('/api/public/signing-sessions/lookup', post({ token }, visitorCsrf)), 409);
  const [stored, ...duplicates] = await json(await admin(signaturesRoute));
  assert.deepEqual(duplicates, []);
  assert.equal(stored.id, receipt.signatureId);
  assert.equal(stored.signerName, signerName);
  assert.equal(stored.locationStatus, 'NOT_REQUESTED');
  assert.equal(stored.latitude, null);
  assert.equal(stored.longitude, null);
  assert.equal(stored.accuracyM, null);
  const image = await admin(`/api/admin/signatures/${receipt.signatureId}/image`);
  assert.equal(image.status, 200);
  assert.equal(image.headers.get('content-type'), 'image/png');
  const png = Buffer.from(testSignature, 'base64');
  assert.deepEqual(Buffer.from(await image.arrayBuffer()), png);
  assert.equal(receipt.imageSha256, createHash('sha256').update(png).digest('hex'));
  const audit = await json(await admin('/api/admin/audit'));
  assert.equal(audit.find(event => event.entityId === receipt.signatureId && event.action === 'SIGNATURE_SUBMITTED')
    ?.actor, signerName);

  console.log(JSON.stringify({
    result: 'PASS', baseUrl: base, taskId: task.id, signatureId: receipt.signatureId,
    checked: ['DB health', '관리자 로그인', 'JSON 인증·CSRF·입력 오류', '업로드 누락·용량 초과',
      '비밀번호 바이트 제한', '내역 저장·검색·조회',
      '입력 검증 및 중복 차단', '상태 변경 및 동시 수정 충돌',
      '서명 링크 발급·저장 실패 후 재시도·동시 제출 차단', '서명 이미지·감사 기록 조회']
  }, null, 2));
}

if (require.main === module) {
  main().catch(error => {
    console.error('로컬 연결 검증 실패:', error.message);
    process.exitCode = 1;
  });
}

module.exports = { readConfig, browser, testSignature };
