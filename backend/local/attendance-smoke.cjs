// 로컬 개발 DB에 가상 행사와 참석자 1건을 생성하고 접수를 마감합니다.
const assert = require('node:assert/strict');
const { randomUUID } = require('node:crypto');
const { inflateRawSync } = require('node:zlib');
const { readConfig, browser, testSignature } = require('./smoke-test.cjs');

const adminRoute = '/api/admin/attendance/events';
const publicRoute = '/api/public/attendance';

async function main() {
  const config = readConfig(process.env.ATTENDANCE_TEST_ENV);
  const testUrl = new URL(process.env.ATTENDANCE_TEST_ORIGIN ||
    ('http://127.0.0.1:' + (config.LOCAL_HTTP_PORT || '8080')));
  assert.ok(['127.0.0.1', 'localhost'].includes(testUrl.hostname), '개발 PC에서만 통합 테스트를 실행하세요.');
  const base = testUrl.origin;
  const admin = browser(base);
  const visitor = browser(base);
  const loginToken = await json(await admin('/api/csrf'));
  const login = await admin('/login', { method: 'POST', body: new URLSearchParams({
    username: config.BOOTSTRAP_ADMIN_USER, password: config.BOOTSTRAP_ADMIN_PASSWORD, _csrf: loginToken.token
  }) });
  assert.equal(login.status, 302);
  assert.ok(login.headers.get('location').endsWith('/master/index.html'));
  if (testUrl.protocol === 'https:') {
    assert.ok(login.headers.getSetCookie().some(cookie => cookie.startsWith('JSESSIONID=') && /; Secure/i.test(cookie)));
  }
  const session = await json(await admin('/api/admin/session'));
  assert.equal(session.mode, process.env.ATTENDANCE_TEST_MODE || 'local');
  const adminPost = await poster(admin);
  const visitorPost = await poster(visitor);
  assert.equal((await admin('/master/attendance.html')).status, 200);
  assert.equal((await visitor('/attendance/index.html')).status, 200);
  assert.equal((await visitor(adminRoute)).status, 401);

  const title = '출석부 통합 검증 ' + randomUUID().slice(0, 8);
  const invitation = await json(await adminPost(adminRoute, { title, eventDate: '2026-11-09' }));
  const link = new URL(invitation.url);
  const token = new URLSearchParams(link.hash.slice(1)).get('token');
  assert.equal(link.pathname, '/attendance/index.html');
  assert.equal((await json(await visitorPost(publicRoute + '/lookup', { token }))).title, title);
  const event = adminRoute + '/' + invitation.event.id;
  const sample = {
    token, submissionId: randomUUID(), educationOffice: '가상교육청', jobTitle: '교육위원',
    fullName: '검증용 참석자', phone: '010-0000-0000', pngBase64: testSignature, consent: true
  };
  const submit = body => visitorPost(publicRoute + '/submit', body);
  assert.equal((await submit({ ...sample, consent: false })).status, 400);
  assert.equal((await submit({ ...sample, phone: '1' })).status, 400);
  assert.equal((await submit({ ...sample, pngBase64: 'invalid' })).status, 400);
  const results = await Promise.all([submit(sample), submit(sample)]);
  const [first, second] = await Promise.all(results.map(json));
  assert.equal(first.id, second.id);
  assert.equal((await submit({ ...sample, submissionId: randomUUID() })).status, 409);
  assert.equal((await submit({ ...sample, jobTitle: '다른 직급' })).status, 409);

  const entries = await json(await admin(event + '/entries'));
  assert.equal(entries.length, 1);
  assert.equal(entries[0].phone, '01000000000');
  assert.equal(entries[0].educationOffice, sample.educationOffice);
  assert.equal(entries[0].jobTitle, sample.jobTitle);
  assert.equal(entries[0].fullName, sample.fullName);
  const image = await admin(event + '/entries/' + first.id + '/image');
  assert.equal(image.status, 200);
  assert.deepEqual(Buffer.from(await image.arrayBuffer()), Buffer.from(testSignature, 'base64'));
  assert.equal((await visitor(event + '/export.xlsx')).status, 401);

  const xlsx = await admin(event + '/export.xlsx');
  assert.equal(xlsx.status, 200);
  assert.match(xlsx.headers.get('content-disposition'), /attachment.*\.xlsx/);
  const entriesInWorkbook = zipEntries(Buffer.from(await xlsx.arrayBuffer()));
  const strings = entriesInWorkbook.get('xl/sharedStrings.xml').toString('utf8');
  for (const value of [title, sample.educationOffice, sample.jobTitle, sample.fullName, '01000000000']) {
    assert.ok(strings.includes('>' + value + '<'), '출석부 엑셀 값이 일치해야 합니다.');
  }
  assert.deepEqual(entriesInWorkbook.get('xl/media/image1.png'), Buffer.from(testSignature, 'base64'));

  const renewed = await json(await adminPost(event + '/renew', {}));
  const nextToken = new URLSearchParams(new URL(renewed.url).hash.slice(1)).get('token');
  assert.equal((await visitorPost(publicRoute + '/lookup', { token })).status, 400);
  assert.equal((await visitorPost(publicRoute + '/lookup', { token: nextToken })).status, 200);
  assert.equal((await adminPost(event + '/close', {})).status, 204);
  assert.equal((await submit({ ...sample, token: nextToken, submissionId: randomUUID(), fullName: '추가 검증' })).status, 409);
  assert.equal((await admin(event + '/export.xlsx')).status, 200);
  console.log(JSON.stringify({
    result: 'PASS', eventId: invitation.event.id,
    verified: ['관리자 로그인', '행사 링크 발급', '입력·동의 검증', '서명 저장과 동시 재시도',
      '명단·이미지 조회 권한', '전화번호 및 이미지가 든 엑셀', '링크 갱신과 접수 마감']
  }, null, 2));
}

async function poster(client) {
  const csrf = await json(await client('/api/csrf'));
  return (route, body) => client(route, {
    method: 'POST', headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
    body: JSON.stringify(body)
  });
}

async function json(response) {
  assert.equal(response.status, 200, 'JSON API가 정상 응답해야 합니다.');
  return response.json();
}

// POI가 생성한 작은 테스트 XLSX의 중앙 디렉터리를 읽습니다.
function zipEntries(bytes) {
  const end = bytes.lastIndexOf(Buffer.from([0x50, 0x4b, 0x05, 0x06]));
  assert.ok(end >= 0, 'XLSX ZIP 중앙 디렉터리가 있어야 합니다.');
  const count = bytes.readUInt16LE(end + 10);
  const result = new Map();
  let cursor = bytes.readUInt32LE(end + 16);
  for (let index = 0; index < count; index++) {
    assert.equal(bytes.readUInt32LE(cursor), 0x02014b50);
    const method = bytes.readUInt16LE(cursor + 10);
    const size = bytes.readUInt32LE(cursor + 20);
    const nameLength = bytes.readUInt16LE(cursor + 28);
    const name = bytes.toString('utf8', cursor + 46, cursor + 46 + nameLength);
    const offset = bytes.readUInt32LE(cursor + 42);
    const start = offset + 30 + bytes.readUInt16LE(offset + 26) + bytes.readUInt16LE(offset + 28);
    const content = bytes.subarray(start, start + size);
    assert.ok(method === 0 || method === 8);
    result.set(name, method === 8 ? inflateRawSync(content) : content);
    cursor += 46 + nameLength + bytes.readUInt16LE(cursor + 30) + bytes.readUInt16LE(cursor + 32);
  }
  return result;
}

main().catch(error => {
  console.error('출석 통합 검증 실패:', error.message);
  process.exitCode = 1;
});
