package kr.go.edu.edupass;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import android.app.Instrumentation;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;

/** 가상 행사 링크로 설치된 APK의 실제 WebView와 서명 터치 입력을 검증합니다. */
public final class AttendanceFlowTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    @Test
    public void submitsSignatureAndClearsNextVisitorForm() throws Exception {
        String link = InstrumentationRegistry.getArguments().getString("attendanceLink");
        assertNotNull("개발 서버에서 발급한 가상 행사 링크를 attendanceLink 인자로 제공하세요.", link);
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            AtomicReference<WebView> browser = new AtomicReference<>();
            activity.onActivity(main -> {
                View root = main.findViewById(android.R.id.content);
                EditText field = find(root, EditText.class, view -> true);
                Button open = find(root, Button.class, view -> "행사 열기".contentEquals(view.getText()));
                assertNotNull(field);
                assertNotNull(open);
                field.setText(link);
                open.performClick();
                browser.set(find(main.findViewById(android.R.id.content), WebView.class, view -> true));
            });
            WebView webView = browser.get();
            assertNotNull("행사 링크가 열리지 않았습니다.", webView);
            await(webView, "!!document.querySelector('form canvas')", "행사 출석 양식 로딩");

            assertEquals("true", eval(webView, """
                    (() => {
                      const values = {
                        '교육청 소속': 'Android 가상교육청', '직급': '교육위원',
                        '성함': 'Android 가상 참석자', '전화번호': '010-0000-0002'
                      };
                      const labels = [...document.querySelectorAll('form label')];
                      for (const [name, value] of Object.entries(values)) {
                        const field = labels.find(label => label.textContent.trim().startsWith(name))?.querySelector('input');
                        if (!field) return false;
                        field.value = value;
                        field.dispatchEvent(new Event('input', { bubbles: true }));
                      }
                      const consent = document.querySelector('form input[type="checkbox"]');
                      if (!consent.checked) consent.click();
                      document.querySelector('canvas').scrollIntoView({ block: 'center', behavior: 'instant' });
                      return consent.checked;
                    })()
                    """));
            draw(webView);
            eval(webView, "document.querySelector('form button[type=submit]').click()");
            await(webView, "document.querySelector('h1')?.textContent === '출석을 등록했습니다'", "서명 저장 완료 화면");
            String receipt = (String) new JSONTokener(eval(webView, "document.querySelector('code').textContent")).nextValue();
            assertTrue(receipt.matches("[0-9a-f-]{36}"));

            eval(webView, "document.querySelector('.attendance-receipt button').click()");
            await(webView, "!!document.querySelector('form canvas')", "다음 방문자 양식");
            assertEquals("true", eval(webView, """
                    [...document.querySelectorAll('form input')].every(field =>
                      field.type === 'checkbox' ? !field.checked : field.value === '')
                    """));
            Bundle evidence = new Bundle();
            evidence.putString("attendanceReceipt", receipt);
            instrumentation.sendStatus(0, evidence);
        }
    }

    private void draw(WebView webView) throws Exception {
        var canvas = new JSONObject(eval(webView, """
                (() => {
                  const {left, top, width, height} = document.querySelector('canvas').getBoundingClientRect();
                  return {left, top, width, height, scale: window.devicePixelRatio};
                })()
                """));
        int[] origin = new int[2];
        instrumentation.runOnMainSync(() -> webView.getLocationOnScreen(origin));
        long down = SystemClock.uptimeMillis();
        sendTouch(origin, canvas, down, MotionEvent.ACTION_DOWN, .2, .42);
        sendTouch(origin, canvas, down, MotionEvent.ACTION_MOVE, .35, .66);
        sendTouch(origin, canvas, down, MotionEvent.ACTION_MOVE, .48, .29);
        sendTouch(origin, canvas, down, MotionEvent.ACTION_MOVE, .63, .57);
        sendTouch(origin, canvas, down, MotionEvent.ACTION_MOVE, .78, .42);
        sendTouch(origin, canvas, down, MotionEvent.ACTION_UP, .78, .42);
    }

    private void sendTouch(int[] origin, JSONObject canvas, long down, int action, double fractionX, double fractionY)
            throws Exception {
        float x = (float) (origin[0] + (canvas.getDouble("left") + fractionX * canvas.getDouble("width"))
                * canvas.getDouble("scale"));
        float y = (float) (origin[1] + (canvas.getDouble("top") + fractionY * canvas.getDouble("height"))
                * canvas.getDouble("scale"));
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try { instrumentation.sendPointerSync(event); }
        finally { event.recycle(); }
    }

    private void await(WebView webView, String expression, String description) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 15_000;
        while (SystemClock.uptimeMillis() < deadline) {
            if ("true".equals(eval(webView, expression))) return;
            SystemClock.sleep(100);
        }
        fail(description + " 검증 실패. 화면 오류: " + eval(webView, "document.querySelector('[role=alert]')?.textContent || ''"));
    }

    private String eval(WebView webView, String script) throws InterruptedException {
        AtomicReference<String> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        instrumentation.runOnMainSync(() -> webView.evaluateJavascript(script, value -> {
            result.set(value);
            done.countDown();
        }));
        assertTrue("WebView 스크립트 응답 시간 초과", done.await(4, TimeUnit.SECONDS));
        return result.get();
    }

    private static <T extends View> T find(View view, Class<T> type, Predicate<T> predicate) {
        if (type.isInstance(view)) {
            T match = type.cast(view);
            if (predicate.test(match)) return match;
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                T match = find(group.getChildAt(i), type, predicate);
                if (match != null) return match;
            }
        }
        return null;
    }
}
