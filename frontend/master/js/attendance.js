import { createApp, h, onMounted, ref } from '/attendance/vendor/vue.runtime.esm-browser.prod.js';
import { api } from '/attendance/api.js';

const route = '/api/admin/attendance/events';
const koreanTime = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul', dateStyle: 'short', timeStyle: 'short'
});

createApp({
  setup() {
    const events = ref([]);
    const active = ref(null);
    const entries = ref([]);
    const invite = ref('');
    const title = ref('');
    const date = ref(new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Seoul' }));
    const error = ref('');
    const busy = ref(false);

    async function action(task) {
      if (busy.value) return;
      busy.value = true;
      error.value = '';
      try { await task(); }
      catch (failure) { error.value = failure.message; }
      finally { busy.value = false; }
    }

    async function refresh(id) {
      events.value = await api(route);
      const selected = events.value.find(item => item.event.id === id)?.event || null;
      const roster = selected ? await api(route + '/' + id + '/entries') : [];
      active.value = selected;
      entries.value = roster;
    }

    function select(id) {
      return action(async () => {
        invite.value = '';
        await refresh(id);
      });
    }

    function create(event) {
      event.preventDefault();
      return action(async () => {
        const result = await api(route, { title: title.value, eventDate: date.value });
        title.value = '';
        await refresh(result.event.id);
        invite.value = result.url;
      });
    }

    function renew() {
      return action(async () => {
        if (!window.confirm('이전 링크를 닫고 새 출석 링크를 발급할까요?')) return;
        const result = await api(route + '/' + active.value.id + '/renew', {});
        invite.value = result.url;
      });
    }

    function close() {
      return action(async () => {
        if (!window.confirm('이 행사의 출석 접수를 마감할까요?')) return;
        const id = active.value.id;
        await api(route + '/' + id + '/close', {});
        invite.value = '';
        await refresh(id);
      });
    }

    onMounted(() => action(() => refresh(null)));

    function roster() {
      if (!active.value) return null;
      const id = active.value.id;
      const heading = ['번호', '교육청 소속', '직급', '성함', '전화번호', '서명 시각', '서명'];
      return h('section', { class: 'card panel' }, [
        h('div', { class: 'split-heading' }, [
          h('h2', active.value.title),
          h('div', { class: 'attendance-actions' }, [
            h('button', { type: 'button', class: 'btn btn-light', disabled: busy.value,
              onClick: () => action(() => refresh(id)) }, '명단 새로고침'),
            h('a', { class: 'btn btn-primary', href: route + '/' + id + '/export.xlsx' }, '엑셀 다운로드')
          ])
        ]),
        h('p', { class: 'muted' }, active.value.eventDate + ' · ' + (active.value.closedAt ? '마감' : '접수 중')),
        active.value.closedAt ? null : h('div', { class: 'attendance-actions' }, [
          h('button', { type: 'button', class: 'btn btn-outline-primary', disabled: busy.value, onClick: renew }, '링크 다시 발급'),
          h('button', { type: 'button', class: 'btn btn-light', disabled: busy.value, onClick: close }, '접수 마감')
        ]),
        invite.value ? h('label', { class: 'attendance-invite' }, [
          '방문자에게 공유할 행사 링크',
          h('input', { class: 'form-control', readonly: true, value: invite.value,
            onFocus: event => event.target.select() }),
          h('small', '링크를 다시 발급하면 이전 링크로 새 출석을 등록할 수 없습니다.')
        ]) : null,
        entries.value.length ? h('div', { class: 'table-wrap attendance-table', role: 'region', tabindex: '0',
          'aria-label': '행사 출석부' }, h('table', { class: 'table table-hover' }, [
          h('thead', h('tr', heading.map(value => h('th', { scope: 'col' }, value)))),
          h('tbody', entries.value.map((entry, index) => h('tr', { key: entry.id }, [
            ...[index + 1, entry.educationOffice, entry.jobTitle, entry.fullName, entry.phone,
              koreanTime.format(new Date(entry.signedAt))].map(value => h('td', String(value))),
            h('td', h('img', { class: 'attendance-thumbnail', loading: 'lazy', alt: entry.fullName + ' 서명',
              src: route + '/' + id + '/entries/' + entry.id + '/image' }))
          ])))
        ])) : h('p', { class: 'fineprint' }, '등록된 참석자가 없습니다.')
      ]);
    }

    return () => [
      h('h1', '행사 출석 관리'),
      h('p', { class: 'section-lead' }, '행사별 방문자 서명과 출석부를 확인합니다.'),
      error.value ? h('p', { class: 'message error', role: 'alert' }, error.value) : null,
      h('section', { class: 'card panel' }, [
        h('h2', '새 행사 만들기'),
        h('form', { class: 'form-grid', onSubmit: create }, [
          h('label', ['행사명', h('input', { class: 'form-control', required: true, maxlength: 150, value: title.value,
            onInput: event => { title.value = event.target.value; } })]),
          h('label', ['행사일', h('input', { class: 'form-control', type: 'date', required: true, value: date.value,
            onInput: event => { date.value = event.target.value; } })]),
          h('button', { type: 'submit', class: 'btn btn-primary', disabled: busy.value }, '행사 만들고 링크 받기')
        ])
      ]),
      h('section', { class: 'card panel' }, [
        h('h2', '행사 목록'),
        events.value.length ? h('ul', { class: 'attendance-events' }, events.value.map(({ event, total }) =>
          h('li', { key: event.id }, h('button', { type: 'button', class: 'btn btn-light', disabled: busy.value,
            'aria-pressed': String(active.value?.id === event.id), onClick: () => select(event.id) },
          event.title + ' · ' + event.eventDate + ' · ' + total + '명 · ' + (event.closedAt ? '마감' : '접수 중')))
        )) : h('p', { class: 'fineprint' }, busy.value ? '행사를 불러오는 중입니다.' : '등록된 행사가 없습니다.')
      ]),
      roster()
    ];
  }
}).mount('#attendanceApp');
