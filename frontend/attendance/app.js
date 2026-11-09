import { createApp, h, nextTick, onMounted, reactive, ref } from './vendor/vue.runtime.esm-browser.prod.js';
import { api } from './api.js';
import { SignaturePad } from './signature-pad.js';

const route = '/api/public/attendance';
const fields = [
  ['educationOffice', '교육청 소속', 150, 'text'],
  ['jobTitle', '직급', 100, 'text'],
  ['fullName', '성함', 100, 'text'],
  ['phone', '전화번호', 32, 'tel']
];

createApp({
  setup() {
    const token = new URLSearchParams(location.hash.slice(1)).get('token') || '';
    const event = ref(null);
    const receipt = ref(null);
    const error = ref('');
    const loading = ref(true);
    const busy = ref(false);
    const consent = ref(false);
    const pad = ref(null);
    const title = ref(null);
    const values = reactive({ educationOffice: '', jobTitle: '', fullName: '', phone: '' });
    let lastSubmission;

    onMounted(async () => {
      history.replaceState(null, '', location.pathname);
      try {
        if (!/^[A-Za-z0-9_-]{43}$/.test(token)) throw new Error('담당자가 발급한 행사 출석 링크로 열어 주세요.');
        event.value = await api(route + '/lookup', { token });
      } catch (failure) {
        error.value = failure.message;
      } finally {
        loading.value = false;
      }
    });

    async function submit(event) {
      event.preventDefault();
      if (busy.value) return;
      error.value = '';
      const png = pad.value.png();
      if (!png) {
        error.value = '서명 영역에 직접 서명해 주세요.';
        return;
      }
      const data = { token, ...values, pngBase64: png, consent: consent.value };
      const snapshot = JSON.stringify(data);
      // 동일 입력을 재시도하면 네트워크 응답 유실이 있어도 같은 접수 번호를 받습니다.
      if (lastSubmission?.snapshot !== snapshot) {
        lastSubmission = { snapshot, id: globalThis.crypto.randomUUID?.() || randomId() };
      }
      busy.value = true;
      try {
        receipt.value = await api(route + '/submit', { ...data, submissionId: lastSubmission.id });
        for (const [key] of fields) values[key] = '';
        consent.value = false;
        lastSubmission = undefined;
        await nextTick();
        title.value.focus();
      } catch (failure) {
        error.value = failure.message;
      } finally {
        busy.value = false;
      }
    }

    async function nextVisitor() {
      receipt.value = null;
      await nextTick();
      title.value.focus();
    }

    return () => h('section', { class: 'card sign-card' }, [
      h('h1', { ref: title, tabindex: '-1' }, receipt.value ? '출석을 등록했습니다' : '행사 출석 등록'),
      error.value ? h('p', { class: 'message error', role: 'alert' }, error.value) : null,
      loading.value ? h('p', { role: 'status' }, '행사 정보를 확인하고 있습니다.') : null,
      receipt.value ? h('div', { class: 'attendance-receipt' }, [
        h('p', '확인 번호'),
        h('code', receipt.value.id),
        h('button', { type: 'button', class: 'btn btn-primary', onClick: nextVisitor }, '다음 방문자 등록')
      ]) : event.value ? [
        h('div', { class: 'location-card' }, [h('strong', event.value.title), h('p', '행사일 ' + event.value.eventDate)]),
        h('form', { autocomplete: 'off', onSubmit: submit, 'aria-busy': String(busy.value) }, [
          h('fieldset', { disabled: busy.value, class: 'attendance-fieldset' }, [
            h('legend', '참석자 정보'),
            h('div', { class: 'form-grid' }, fields.map(([key, label, max, type]) =>
              h('label', { key }, [
                h('span', [label, ' ', h('span', { class: 'krds-required', 'aria-hidden': 'true' }, '*')]),
                h('input', { class: 'form-control', type, maxlength: max, required: true, value: values[key],
                  onInput: event => { values[key] = event.target.value; } })
              ])
            )),
            h(SignaturePad, { ref: pad, disabled: busy.value }),
            h('label', { class: 'consent' }, [
              h('input', { type: 'checkbox', class: 'form-check-input', required: true, checked: consent.value,
                onChange: event => { consent.value = event.target.checked; } }),
              '행사 출석부 작성과 출석 확인을 위해 위 정보와 서명을 저장하는 데 동의합니다.'
            ]),
            h('button', { type: 'submit', class: 'btn btn-primary wide' }, busy.value ? '등록 중…' : '출석 및 서명 저장')
          ])
        ])
      ] : null
    ]);
  }
}).mount('#attendanceApp');

// HTTP 사설망 실습에서 randomUUID를 제공하지 않는 WebView를 지원합니다.
function randomId() {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 15) | 64;
  bytes[8] = (bytes[8] & 63) | 128;
  const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('');
  return [hex.slice(0, 8), hex.slice(8, 12), hex.slice(12, 16), hex.slice(16, 20), hex.slice(20)].join('-');
}
