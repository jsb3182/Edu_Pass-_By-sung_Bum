// 방문자 서명 화면: 링크 확인 → 서명 → 선택적 위치 확인 → 저장
const element = (id) => document.getElementById(id);
const canvas = element('signatureCanvas');
const pen = canvas.getContext('2d');
const purposeNames = { EDUCATION: '교육', REVIEW: '심사', OTHER: '기타' };

let csrfToken;
let signingToken;
let isDrawing = false;
let hasSignature = false;
let locationPending = false;
let locationRequestId = 0;
let visitLocation = emptyLocation();

function emptyLocation() {
  return {
    locationStatus: 'NOT_REQUESTED',
    latitude: null,
    longitude: null,
    accuracyM: null,
    locationCapturedAt: null
  };
}

function showError(message) {
  const box = element('signError');
  box.textContent = message;
  box.hidden = false;
}

function showStep(index) {
  for (const [position, item] of [...document.querySelectorAll('.sign-steps li')].entries()) {
    const active = position === index;
    item.classList.toggle('active', active);
    if (active) item.setAttribute('aria-current', 'step');
    else item.removeAttribute('aria-current');
  }
}

async function post(path, data) {
  const response = await fetch(path, {
    method: 'POST',
    credentials: 'same-origin',
    cache: 'no-store',
    headers: {
      'Content-Type': 'application/json',
      [csrfToken.headerName]: csrfToken.token
    },
    body: JSON.stringify(data)
  });

  if (!response.ok) {
    const result = await response.json().catch(() => ({}));
    throw new Error(result.error || `요청에 실패했습니다. (${response.status})`);
  }
  return response.json();
}

// 캔버스를 화면 크기에 맞추고 흰 배경으로 초기화합니다.
function clearSignature() {
  const box = canvas.getBoundingClientRect();
  const scale = window.devicePixelRatio || 1;
  canvas.width = Math.round(box.width * scale);
  canvas.height = Math.round(box.height * scale);
  pen.setTransform(scale, 0, 0, scale, 0, 0);
  pen.fillStyle = '#ffffff';
  pen.fillRect(0, 0, box.width, box.height);
  pen.strokeStyle = '#182b45';
  pen.lineWidth = 2.8;
  pen.lineCap = 'round';
  pen.lineJoin = 'round';
  hasSignature = false;
  element('canvasHint').hidden = false;
  showStep(0);
}

function pointerPoint(event) {
  const box = canvas.getBoundingClientRect();
  return { x: event.clientX - box.left, y: event.clientY - box.top };
}

function startDrawing(event) {
  event.preventDefault();
  isDrawing = true;
  canvas.setPointerCapture(event.pointerId);
  const point = pointerPoint(event);
  pen.beginPath();
  pen.moveTo(point.x, point.y);
}

function draw(event) {
  if (!isDrawing) return;
  event.preventDefault();
  const point = pointerPoint(event);
  pen.lineTo(point.x, point.y);
  pen.stroke();
  if (!hasSignature) showStep(1);
  hasSignature = true;
  element('canvasHint').hidden = true;
}

function stopDrawing() {
  isDrawing = false;
}

// 위치는 방문자가 별도로 동의하고 버튼을 눌렀을 때만 요청합니다.
function checkLocation() {
  if (locationPending) return;
  if (!element('locationConsentCheck').checked) {
    showError('위치 확인에 먼저 동의해 주세요.');
    return;
  }
  element('signError').hidden = true;

  if (!navigator.geolocation) {
    visitLocation = emptyLocation();
    visitLocation.locationStatus = 'UNAVAILABLE';
    element('locationMessage').textContent = '이 기기에서는 위치 확인을 지원하지 않습니다.';
    return;
  }

  // 재측위 중에는 이전 좌표를 사용하지 않습니다.
  visitLocation = emptyLocation();
  locationPending = true;
  const requestId = ++locationRequestId;
  element('checkLocation').disabled = true;
  element('locationMessage').textContent = '위치를 확인하고 있습니다…';
  try {
    navigator.geolocation.getCurrentPosition(
      (position) => {
        if (requestId !== locationRequestId) return;
        finishLocationCheck();
        if (!element('locationConsentCheck').checked) return;
        visitLocation = {
          locationStatus: 'AVAILABLE',
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
          accuracyM: position.coords.accuracy,
          locationCapturedAt: new Date(position.timestamp).toISOString()
        };
        element('locationMessage').textContent =
          `위치 확인 완료 · 정확도 약 ±${Math.round(position.coords.accuracy)}m`;
      },
      (error) => {
        if (requestId !== locationRequestId) return;
        finishLocationCheck();
        if (!element('locationConsentCheck').checked) return;
        visitLocation = emptyLocation();
        if (error.code === 1) visitLocation.locationStatus = 'DENIED';
        else if (error.code === 3) visitLocation.locationStatus = 'TIMEOUT';
        else visitLocation.locationStatus = 'UNAVAILABLE';
        element('locationMessage').textContent = visitLocation.locationStatus === 'DENIED'
          ? '위치 권한이 거부되었습니다.'
          : '위치를 확인하지 못했습니다.';
      },
      { enableHighAccuracy: true, timeout: 12000, maximumAge: 0 }
    );
  } catch {
    finishLocationCheck();
    visitLocation.locationStatus = 'UNAVAILABLE';
    element('locationMessage').textContent = '위치를 확인하지 못했습니다.';
  }
}

function finishLocationCheck() {
  locationPending = false;
  element('checkLocation').disabled = false;
}

function onLocationConsentChange() {
  if (element('locationConsentCheck').checked) return;
  // 동의를 철회하면 이미 시작된 위치 요청의 늦은 결과도 무시합니다.
  locationRequestId++;
  finishLocationCheck();
  visitLocation = emptyLocation();
  element('locationMessage').textContent = '위치 기록을 선택하지 않았습니다.';
}

async function submitSignature() {
  element('signError').hidden = true;
  if (!hasSignature) return showError('서명을 입력해 주세요.');
  if (!element('consentCheck').checked) {
    return showError('방문 내역과 기록 안내를 확인해 주세요.');
  }
  if (locationPending) return showError('위치 확인이 끝난 뒤 저장해 주세요.');

  const button = element('submitSignature');
  button.disabled = true;
  button.textContent = '저장 중…';

  try {
    const image = canvas.toDataURL('image/png').split(',')[1];
    const result = await post('/api/public/signatures', {
      token: signingToken,
      pngBase64: image,
      consent: true,
      locationConsent: element('locationConsentCheck').checked,
      deviceLabel: navigator.userAgent.slice(0, 120),
      clientSignedAt: new Date().toISOString(),
      ...visitLocation
    });
    element('signPanel').hidden = true;
    element('receiptId').textContent = result.signatureId;
    element('successPanel').hidden = false;
    signingToken = null;
  } catch (error) {
    showError(error.message);
    button.disabled = false;
    button.textContent = '서명 완료 및 저장';
  }
}

function showSession(session) {
  element('signApplication').textContent = session.applicationNo;
  element('signApplicant').textContent = session.applicantName;
  element('signSchool').textContent = session.schoolName;
  element('signStatus').textContent = session.taskStatus;
  element('signSigner').textContent = session.signerName;
  element('signAffiliation').textContent = session.affiliation;
  element('signPurpose').textContent = purposeNames[session.purpose] || session.purpose;
  element('consentText').textContent = session.consentText;
  element('locationConsentText').textContent = session.locationConsentText;
  element('signPanel').hidden = false;
  requestAnimationFrame(clearSignature);
}

async function start() {
  try {
    // URL의 #token은 서버 요청 로그에 포함되지 않습니다.
    const match = window.location.hash.match(/^#token=([A-Za-z0-9_-]{43})$/);
    if (!match) throw new Error('올바른 서명 링크가 아닙니다. 담당자에게 새 링크를 요청하세요.');
    signingToken = match[1];
    history.replaceState(null, '', window.location.pathname);

    const response = await fetch('/api/csrf', {
      credentials: 'same-origin',
      cache: 'no-store'
    });
    if (!response.ok) throw new Error('서명 화면을 준비하지 못했습니다. 다시 시도해 주세요.');
    csrfToken = await response.json();

    const session = await post('/api/public/signing-sessions/lookup', {
      token: signingToken
    });
    showSession(session);
  } catch (error) {
    showError(error.message);
  }
}

canvas.addEventListener('pointerdown', startDrawing);
canvas.addEventListener('pointermove', draw);
canvas.addEventListener('pointerup', stopDrawing);
canvas.addEventListener('pointercancel', stopDrawing);
element('clearSignature').addEventListener('click', clearSignature);
element('checkLocation').addEventListener('click', checkLocation);
element('locationConsentCheck').addEventListener('change', onLocationConsentChange);
element('submitSignature').addEventListener('click', submitSignature);
start();
