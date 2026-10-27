// 실제 브라우저나 위치 서비스를 열지 않고 동의 철회와 늦은 응답을 검사합니다.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

test('재측위와 동의 철회 후에는 이전 좌표를 저장하지 않는다', async () => {
  const elements = new Map();
  const requests = [];
  let fetchCount = 0;

  function element(id) {
    if (!elements.has(id)) {
      elements.set(id, {
        checked: false,
        disabled: false,
        hidden: false,
        textContent: '',
        addEventListener() {}
      });
    }
    return elements.get(id);
  }

  element('signatureCanvas').getContext = () => ({});
  const context = vm.createContext({
    document: { getElementById: element },
    navigator: {
      geolocation: {
        getCurrentPosition(success, failure) {
          requests.push({ success, failure });
        }
      }
    },
    window: { location: { hash: '' } },
    fetch() {
      fetchCount++;
      throw new Error('테스트 중 네트워크 요청이 발생했습니다.');
    }
  });
  const source = path.resolve(__dirname, '../js/sign.js');
  vm.runInContext(fs.readFileSync(source, 'utf8'), context);

  element('locationConsentCheck').checked = true;
  vm.runInContext('checkLocation()', context);
  requests[0].success(position(37.1));
  assert.equal(vm.runInContext('visitLocation.latitude', context), 37.1);

  vm.runInContext('checkLocation()', context);
  assert.equal(vm.runInContext('visitLocation.latitude', context), null);
  assert.equal(element('checkLocation').disabled, true);
  vm.runInContext('hasSignature = true', context);
  element('consentCheck').checked = true;
  await vm.runInContext('submitSignature()', context);
  assert.match(element('signError').textContent, /위치 확인이 끝난 뒤/);
  assert.equal(fetchCount, 0);

  element('locationConsentCheck').checked = false;
  vm.runInContext('onLocationConsentChange()', context);
  element('locationConsentCheck').checked = true;
  requests[1].success(position(38.2));
  assert.equal(vm.runInContext('visitLocation.locationStatus', context), 'NOT_REQUESTED');
  assert.equal(vm.runInContext('visitLocation.latitude', context), null);
});

function position(latitude) {
  return {
    coords: { latitude, longitude: 127, accuracy: 10 },
    timestamp: Date.now()
  };
}
