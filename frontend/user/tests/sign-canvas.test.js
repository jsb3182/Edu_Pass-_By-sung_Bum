const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

test('고해상도 태블릿에서도 저장 크기를 지키고 회전 후 입력 위치를 맞춘다', () => {
  let bounds = { left: 20, top: 30, width: 650, height: 231 };
  const canvas = {
    getBoundingClientRect: () => bounds,
    getContext: () => ({ fillRect() {} }),
    addEventListener() {}
  };
  const other = { addEventListener() {} };
  const context = vm.createContext({
    document: {
      getElementById: id => id === 'signatureCanvas' ? canvas : other,
      querySelectorAll: () => []
    },
    window: { location: { hash: '' }, devicePixelRatio: 4 }
  });
  vm.runInContext(fs.readFileSync(path.join(__dirname, '../js/sign.js'), 'utf8'), context);
  vm.runInContext('clearSignature()', context);
  assert.ok(canvas.width * canvas.height <= 2_000_000);

  bounds = { ...bounds, width: 325, height: 205 };
  const point = vm.runInContext('pointerPoint({clientX: 182.5, clientY: 132.5})', context);
  assert.equal(point.x, canvas.width / 2);
  assert.equal(point.y, canvas.height / 2);
});
