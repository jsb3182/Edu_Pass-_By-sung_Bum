import { defineComponent, h, onMounted, ref } from './vendor/vue.runtime.esm-browser.prod.js';

export const SignaturePad = defineComponent({
  props: { disabled: Boolean },
  setup(props, { expose }) {
    const canvas = ref(null);
    const hasInk = ref(false);
    let pointer = null;

    function clear() {
      const context = canvas.value.getContext('2d');
      context.fillStyle = 'white';
      context.fillRect(0, 0, canvas.value.width, canvas.value.height);
      context.strokeStyle = '#1e2124';
      context.lineWidth = 5;
      context.lineCap = 'round';
      context.lineJoin = 'round';
      pointer = null;
      hasInk.value = false;
    }

    function point(event) {
      const box = canvas.value.getBoundingClientRect();
      return [(event.clientX - box.left) * canvas.value.width / box.width,
        (event.clientY - box.top) * canvas.value.height / box.height];
    }

    function start(event) {
      if (props.disabled || pointer !== null || event.button !== 0) return;
      pointer = event.pointerId;
      canvas.value.setPointerCapture(pointer);
      const context = canvas.value.getContext('2d');
      context.beginPath();
      context.moveTo(...point(event));
    }

    function draw(event) {
      if (props.disabled || pointer !== event.pointerId) return;
      const context = canvas.value.getContext('2d');
      context.lineTo(...point(event));
      context.stroke();
      hasInk.value = true;
    }

    function stop(event) {
      if (pointer === event.pointerId) pointer = null;
    }

    expose({ png: () => hasInk.value ? canvas.value.toDataURL('image/png').split(',')[1] : null });
    onMounted(clear);
    return () => [
      h('div', { class: 'sign-pad-heading' }, [
        h('h2', '직접 서명'),
        h('button', { type: 'button', class: 'btn btn-light', disabled: props.disabled, onClick: clear }, '서명 지우기')
      ]),
      h('div', { class: 'canvas-frame attendance-canvas' }, [
        h('canvas', {
          ref: canvas, width: 1000, height: 320, 'aria-label': '손가락이나 펜으로 직접 서명하는 영역',
          onPointerdown: start, onPointermove: draw, onPointerup: stop,
          onPointercancel: stop, onLostpointercapture: stop
        }),
        hasInk.value ? null : h('span', '여기에 서명해 주세요')
      ])
    ];
  }
});
