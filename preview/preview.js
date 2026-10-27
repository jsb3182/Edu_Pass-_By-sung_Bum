// This file is used only by the two local preview pages.
// It contains no API calls, location requests, or data storage.

const viewNames = {
  overview: '대시보드',
  tasks: '신청 내역·서명',
  imports: '엑셀 적재',
  users: '계정 관리',
  audit: '감사 기록',
  password: '내 비밀번호'
};

for (const button of document.querySelectorAll('[data-view]')) {
  button.addEventListener('click', () => {
    const selected = button.dataset.view;
    for (const section of document.querySelectorAll('.view')) {
      section.hidden = section.id !== `${selected}View`;
    }
    for (const menuItem of document.querySelectorAll('[data-view]')) {
      menuItem.classList.toggle('active', menuItem === button);
    }
    document.getElementById('viewTitle').textContent = viewNames[selected];
    document.getElementById('breadcrumbCurrent').textContent = viewNames[selected];
  });
}

for (const button of document.querySelectorAll('.krds-table-link')) {
  button.addEventListener('click', () => {
    document.querySelector('.detail-panel').scrollIntoView({ behavior: 'smooth' });
  });
}

for (const button of document.querySelectorAll('.demo-button')) {
  button.addEventListener('click', () => {
    window.alert('미리보기 화면입니다. 실제 저장은 관리자 화면에서만 가능합니다.');
  });
}

const canvas = document.getElementById('previewCanvas');
if (canvas) {
  const context = canvas.getContext('2d');
  let drawing = false;

  function clearCanvas() {
    const box = canvas.getBoundingClientRect();
    canvas.width = box.width;
    canvas.height = box.height;
    context.fillStyle = 'white';
    context.fillRect(0, 0, canvas.width, canvas.height);
    context.strokeStyle = '#182b45';
    context.lineWidth = 3;
    context.lineCap = 'round';
    document.getElementById('previewHint').hidden = false;
    showPreviewStep(0);
  }

  function showPreviewStep(index) {
    for (const [position, item] of [...document.querySelectorAll('.sign-steps li')].entries()) {
      const active = position === index;
      item.classList.toggle('active', active);
      if (active) item.setAttribute('aria-current', 'step');
      else item.removeAttribute('aria-current');
    }
  }

  canvas.addEventListener('pointerdown', (event) => {
    drawing = true;
    canvas.setPointerCapture(event.pointerId);
    const box = canvas.getBoundingClientRect();
    context.beginPath();
    context.moveTo(event.clientX - box.left, event.clientY - box.top);
  });

  canvas.addEventListener('pointermove', (event) => {
    if (!drawing) return;
    const box = canvas.getBoundingClientRect();
    context.lineTo(event.clientX - box.left, event.clientY - box.top);
    context.stroke();
    showPreviewStep(1);
    document.getElementById('previewHint').hidden = true;
  });

  canvas.addEventListener('pointerup', () => { drawing = false; });
  document.getElementById('clearPreview').addEventListener('click', clearCanvas);
  document.getElementById('previewSubmit').addEventListener('click', () => {
    document.getElementById('previewResult').textContent = '미리보기입니다. 서명과 위치는 저장하지 않았습니다.';
  });
  requestAnimationFrame(clearCanvas);
}
