// 관리자 화면의 동작을 모아 둔 파일입니다.
// 화면에 표시할 때는 textContent를 사용해 입력값을 HTML로 실행하지 않습니다.

const element = (id) => document.getElementById(id);

let csrfToken;
let loginUser;
let selectedTask;
let currentPage = 0;
let messageTimer;

const titles = {
  overview: '대시보드',
  tasks: '신청 내역·서명',
  imports: '엑셀 적재',
  users: '계정 관리',
  audit: '감사 기록',
  password: '내 비밀번호'
};
const purposeNames = { EDUCATION: '교육', REVIEW: '심사', OTHER: '기타' };

// 모든 API 요청은 이 함수를 거칩니다.
async function api(path, method = 'GET', data = null) {
  const headers = new Headers();
  let body = null;

  if (data instanceof FormData) {
    body = data;
  } else if (data !== null) {
    headers.set('Content-Type', 'application/json');
    body = JSON.stringify(data);
  }

  if (method !== 'GET') {
    headers.set(csrfToken.headerName, csrfToken.token);
  }

  const response = await fetch(path, {
    method,
    headers,
    body,
    credentials: 'same-origin',
    cache: 'no-store'
  });

  if (response.redirected && new URL(response.url).pathname === '/master/login.html') {
    throw new Error('로그인이 만료되었습니다. 다시 로그인해 주세요.');
  }

  if (!response.ok) {
    const result = await response.json().catch(() => ({}));
    throw new Error(result.error || `요청에 실패했습니다. (${response.status})`);
  }

  const text = await response.text();
  if (!text) return null;
  if (!response.headers.get('Content-Type')?.includes('json')) {
    throw new Error('서버 응답 형식이 올바르지 않습니다.');
  }
  return JSON.parse(text);
}

function showMessage(text, isError = false) {
  const box = element('message');
  box.textContent = text;
  box.classList.toggle('error', isError);
  box.hidden = false;

  clearTimeout(messageTimer);
  messageTimer = setTimeout(() => { box.hidden = true; }, 7000);
}

async function safely(action) {
  try {
    await action();
  } catch (error) {
    showMessage(error.message, true);
  }
}

function formatDate(value) {
  return value ? new Date(value).toLocaleString('ko-KR') : '—';
}

function addCell(row, text) {
  const cell = document.createElement('td');
  cell.textContent = text == null ? '—' : String(text);
  row.append(cell);
  return cell;
}

function formValues(form) {
  return Object.fromEntries(new FormData(form));
}

async function showView(name) {
  for (const section of document.querySelectorAll('.view')) {
    section.hidden = section.id !== `${name}View`;
  }
  for (const button of document.querySelectorAll('.nav-item')) {
    button.classList.toggle('active', button.dataset.view === name);
  }
  element('viewTitle').textContent = titles[name];
  element('breadcrumbCurrent').textContent = titles[name];

  if (name === 'overview') await loadSummary();
  if (name === 'tasks') await loadTasks();
  if (name === 'users') await loadUsers();
  if (name === 'audit') await loadAudit();
}

async function loadSummary() {
  const summary = await api('/api/admin/summary');
  element('taskCount').textContent = summary.tasks.toLocaleString('ko-KR');
  element('signatureCount').textContent = summary.signatures.toLocaleString('ko-KR');
  element('pendingCount').textContent = summary.pendingEvents.toLocaleString('ko-KR');
  element('importCount').textContent = summary.imports.toLocaleString('ko-KR');
}

async function loadTasks() {
  const query = encodeURIComponent(element('taskSearch').value);
  const result = await api(`/api/admin/tasks?query=${query}&page=${currentPage}`);
  const rows = element('taskRows');
  rows.replaceChildren();
  selectedTask = null;
  element('taskDetail').hidden = true;

  element('pageLabel').textContent = `${result.total.toLocaleString('ko-KR')}건 · ${result.page + 1}페이지`;
  element('previousPage').disabled = currentPage === 0;
  element('nextPage').disabled = (currentPage + 1) * result.size >= result.total;

  if (result.items.length === 0) {
    const row = document.createElement('tr');
    addCell(row, '검색 결과가 없습니다.').colSpan = 5;
    rows.append(row);
    return;
  }

  for (const task of result.items) {
    const row = document.createElement('tr');
    // 첫 칸을 실제 버튼으로 만들어 키보드 사용자도 상세 내역을 열 수 있게 합니다.
    const numberCell = addCell(row, '');
    const openButton = document.createElement('button');
    openButton.type = 'button';
    openButton.className = 'krds-table-link';
    openButton.textContent = task.applicationNo;
    openButton.setAttribute('aria-label', `신청번호 ${task.applicationNo} 상세 보기`);
    openButton.addEventListener('click', () => safely(() => selectTask(task)));
    numberCell.append(openButton);
    addCell(row, task.applicantName);
    addCell(row, task.schoolName);
    addCell(row, task.status);
    addCell(row, formatDate(task.updatedAt));
    rows.append(row);
  }
}

async function selectTask(task) {
  selectedTask = task;
  element('taskDetail').hidden = false;
  element('detailTitle').textContent = task.applicationNo;
  element('detailSubtitle').textContent =
    `${task.applicantName} · ${task.schoolName} · 최종 수정 ${formatDate(task.updatedAt)}`;
  element('detailSource').textContent = task.source === 'EXCEL' ? '엑셀 적재' : '직접 등록';
  element('statusForm').elements.status.value = task.status;
  element('issuedSession').hidden = true;

  await loadSignatures();
  element('taskDetail').scrollIntoView({ behavior: 'smooth', block: 'start' });
}

async function loadSignatures() {
  if (!selectedTask) return;

  const signatures = await api(`/api/admin/tasks/${selectedTask.id}/signatures`);
  const list = element('signatureRows');
  list.replaceChildren();

  if (signatures.length === 0) {
    const notice = document.createElement('p');
    notice.className = 'muted';
    notice.textContent = '아직 서명이 없습니다.';
    list.append(notice);
    return;
  }

  for (const signature of signatures) {
    list.append(createSignatureCard(signature));
  }
}

function createSignatureCard(signature) {
  const card = document.createElement('article');
  card.className = 'signature-item';

  const details = document.createElement('div');
  const name = document.createElement('strong');
  name.textContent = `${signature.signerName} · ${signature.affiliation}`;
  details.append(name);

  const time = document.createElement('p');
  time.textContent = `${formatDate(signature.receivedAt)} · ${purposeNames[signature.purpose] || signature.purpose}`;
  details.append(time);

  const location = document.createElement('p');
  if (signature.locationStatus === 'AVAILABLE') {
    location.textContent = `위치 ${signature.latitude.toFixed(6)}, ` +
      `${signature.longitude.toFixed(6)} · 정확도 ±${Math.round(signature.accuracyM)}m`;
  } else {
    location.textContent = `위치 기록: ${signature.locationStatus}`;
  }
  details.append(location);

  const image = document.createElement('img');
  image.src = `/api/admin/signatures/${signature.id}/image`;
  image.alt = `${signature.signerName} 서명`;
  card.append(details, image);
  return card;
}

async function loadUsers() {
  if (loginUser.role !== 'ROLE_ADMIN') return;

  const users = await api('/api/admin/users');
  const rows = element('userRows');
  rows.replaceChildren();

  for (const user of users) {
    const row = document.createElement('tr');
    addCell(row, user.username);
    addCell(row, user.role === 'ADMIN' ? '관리자' : '운영자');
    addCell(row, user.active ? '사용 중' : '비활성');

    const actions = addCell(row, '');
    if (user.active && user.username !== loginUser.username) {
      const button = document.createElement('button');
      button.className = 'ghost-btn';
      button.textContent = '비활성화';
      button.addEventListener('click', () => safely(() => deactivateUser(user)));
      actions.append(button);
    }
    rows.append(row);
  }
}

async function deactivateUser(user) {
  if (!window.confirm(`${user.username} 계정을 비활성화할까요?`)) return;
  await api(`/api/admin/users/${user.id}/deactivate`, 'POST');
  await loadUsers();
  showMessage('계정을 비활성화했습니다.');
}

async function loadAudit() {
  const events = await api('/api/admin/audit');
  const rows = element('auditRows');
  rows.replaceChildren();

  for (const event of events) {
    const row = document.createElement('tr');
    addCell(row, formatDate(event.occurredAt));
    addCell(row, event.actor);
    addCell(row, event.action);
    addCell(row, `${event.entityType} · ${event.entityId || '—'}`);
    rows.append(row);
  }
}

async function createTask(event) {
  event.preventDefault();
  const task = await api('/api/admin/tasks', 'POST', formValues(event.target));
  event.target.reset();
  element('newTaskPanel').hidden = true;
  await loadTasks();
  await selectTask(task);
  showMessage('신청 내역을 등록했습니다.');
}

async function saveStatus(event) {
  event.preventDefault();
  if (!selectedTask) return;

  // 화면을 연 뒤 다른 관리자가 수정했다면 서버의 version 검사에서 저장을 거절합니다.
  const data = {
    status: event.target.elements.status.value,
    version: selectedTask.version
  };
  const task = await api(`/api/admin/tasks/${selectedTask.id}/status`, 'PUT', data);
  await loadTasks();
  await selectTask(task);
  showMessage('진행상태를 저장했습니다.');
}

async function issueLink(event) {
  event.preventDefault();
  if (!selectedTask) return;

  const data = { ...formValues(event.target), taskId: selectedTask.id };
  const issued = await api('/api/admin/signing-sessions', 'POST', data);
  element('sessionQr').src = issued.qrDataUrl;
  element('sessionUrl').value = issued.url;
  element('sessionExpiry').textContent = `${formatDate(issued.expiresAt)}까지 사용 가능 · 1회용`;
  element('issuedSession').hidden = false;
  event.target.reset();
  showMessage('서명 링크를 발급했습니다. 태블릿에서 QR을 스캔하세요.');
}

async function copyLink() {
  try {
    await navigator.clipboard.writeText(element('sessionUrl').value);
    showMessage('링크를 복사했습니다.');
  } catch {
    element('sessionUrl').select();
    showMessage('링크를 선택했습니다. 복사해서 사용하세요.');
  }
}

async function importExcel(event) {
  event.preventDefault();
  const result = await api('/api/admin/imports', 'POST', new FormData(event.target));
  element('importResult').textContent =
    `총 ${result.total}행 · 신규 ${result.inserted}건 · 변경 ${result.updated}건 · 동일 ${result.unchanged}건`;
  element('importResult').hidden = false;
  event.target.reset();
  await loadSummary();
  showMessage('엑셀 적재가 완료되었습니다.');
}

async function createUser(event) {
  event.preventDefault();
  await api('/api/admin/users', 'POST', formValues(event.target));
  event.target.reset();
  await loadUsers();
  showMessage('계정을 생성했습니다.');
}

async function changePassword(event) {
  event.preventDefault();
  await api('/api/admin/password', 'POST', formValues(event.target));
  window.location.assign('/master/login.html?passwordChanged');
}

function bindEvents() {
  for (const button of document.querySelectorAll('.nav-item')) {
    button.addEventListener('click', () => safely(() => showView(button.dataset.view)));
  }

  element('newTaskButton').addEventListener('click', () => {
    element('newTaskPanel').hidden = !element('newTaskPanel').hidden;
  });
  element('searchButton').addEventListener('click', () => {
    currentPage = 0;
    safely(loadTasks);
  });
  element('taskSearch').addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
      currentPage = 0;
      safely(loadTasks);
    }
  });
  element('previousPage').addEventListener('click', () => {
    if (currentPage > 0) currentPage--;
    safely(loadTasks);
  });
  element('nextPage').addEventListener('click', () => {
    currentPage++;
    safely(loadTasks);
  });

  element('newTaskForm').addEventListener('submit', (event) => safely(() => createTask(event)));
  element('statusForm').addEventListener('submit', (event) => safely(() => saveStatus(event)));
  element('issueForm').addEventListener('submit', (event) => safely(() => issueLink(event)));
  element('copyLink').addEventListener('click', () => safely(copyLink));
  element('importForm').addEventListener('submit', (event) => safely(() => importExcel(event)));
  element('userForm').addEventListener('submit', (event) => safely(() => createUser(event)));
  element('passwordForm').addEventListener('submit', (event) => safely(() => changePassword(event)));
}

async function start() {
  await safely(async () => {
    const response = await fetch('/api/csrf', {
      credentials: 'same-origin',
      cache: 'no-store'
    });
    if (!response.ok) throw new Error('관리자 화면을 준비하지 못했습니다.');
    csrfToken = await response.json();
    element('logoutCsrf').value = csrfToken.token;

    loginUser = await api('/api/admin/session');
    element('userLabel').textContent = loginUser.username;
    element('nodeLabel').textContent = `${loginUser.mode.toUpperCase()} · ${loginUser.nodeId}`;
    element('modeExplanation').textContent = loginUser.mode === 'aws'
      ? 'AWS 원격 서버에 연결되어 있습니다. 이 서버의 데이터베이스에 기록됩니다.'
      : '노트북 로컬 서버에 연결되어 있습니다. 이 서버의 데이터베이스에 기록됩니다.';

    if (loginUser.role !== 'ROLE_ADMIN') {
      element('usersNav').hidden = true;
      document.querySelector('[data-view="imports"]').hidden = true;
    }
    await loadSummary();
    bindEvents();
  });
}

start();
