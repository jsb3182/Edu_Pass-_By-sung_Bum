package kr.go.edu.edupass;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int BLUE = 0xff0b50d0;
    private static final int INK = 0xff1e2124;
    private WebView webView;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // 공용 단말의 최근 앱 미리보기에 참석자 정보와 서명이 남지 않게 합니다.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        showStart();
    }

    private void showStart() {
        disposeWebView();
        var root = root();
        var content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(28), dp(24), dp(24));
        root.addView(content);
        content.addView(label(getString(R.string.app_name), 28));
        content.addView(label(BuildConfig.ALLOW_PRIVATE_HTTP
                ? "같은 사설망에 있는 노트북에서 발급한 행사 링크를 붙여 넣으세요."
                : "관리자가 발급한 HTTPS 행사 링크를 붙여 넣으세요.", 17));
        var input = new EditText(this);
        input.setHint("행사 출석 링크");
        input.setContentDescription("행사 출석 링크");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setSingleLine();
        input.setSaveEnabled(false);
        if (Build.VERSION.SDK_INT >= 26) input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        content.addView(input);
        var error = label("", 16);
        error.setTextColor(0xffbd2c0f);
        error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        var open = new Button(this);
        open.setText("행사 열기");
        open.setTextColor(Color.WHITE);
        open.setBackgroundTintList(ColorStateList.valueOf(BLUE));
        open.setOnClickListener(view -> {
            try {
                showAttendance(AttendanceLink.parse(input.getText().toString(), BuildConfig.ALLOW_PRIVATE_HTTP));
            } catch (IllegalArgumentException exception) {
                error.setText(exception.getMessage());
            }
        });
        content.addView(open);
        content.addView(error);
        setContentView(root);
        root.requestApplyInsets();
    }

    private void showAttendance(AttendanceLink link) {
        var root = root();
        var change = new Button(this);
        change.setText("다른 행사 열기");
        change.setOnClickListener(view -> confirmExit());
        root.addView(change);

        var notice = label("", 16);
        notice.setTextColor(0xffbd2c0f);
        notice.setVisibility(View.GONE);
        notice.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        root.addView(notice);
        var reload = new Button(this);
        reload.setText("다시 연결");
        reload.setVisibility(View.GONE);
        root.addView(reload);

        webView = new WebView(this);
        webView.setSaveEnabled(false);
        var settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setGeolocationEnabled(false);
        settings.setSaveFormData(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !link.permitsNavigation(URI.create(request.getUrl().toString()));
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (link.permitsResource(URI.create(request.getUrl().toString()))) return null;
                return new WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden",
                        java.util.Collections.emptyMap(), new ByteArrayInputStream("Blocked".getBytes(StandardCharsets.UTF_8)));
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (!request.isForMainFrame()) return;
                notice.setText("서버에 연결할 수 없습니다. 노트북 또는 서버 연결을 확인해 주세요.");
                notice.setVisibility(View.VISIBLE);
                reload.setVisibility(View.VISIBLE);
            }
        });
        reload.setOnClickListener(view -> {
            notice.setVisibility(View.GONE);
            reload.setVisibility(View.GONE);
            webView.loadUrl(link.url().toASCIIString());
        });
        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        root.requestApplyInsets();
        webView.loadUrl(link.url().toASCIIString());
    }

    private LinearLayout root() {
        var root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                var bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                var keyboard = insets.getInsets(WindowInsets.Type.ime());
                view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard.bottom));
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        return root;
    }

    private TextView label(String text, int size) {
        var label = new TextView(this);
        label.setTextColor(INK);
        label.setTextSize(size);
        label.setText(text);
        label.setPadding(0, dp(8), 0, dp(8));
        return label;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void confirmExit() {
        new AlertDialog.Builder(this).setMessage("다른 행사를 열면 작성 중인 정보가 사라집니다.")
                .setNegativeButton("계속 작성", null)
                .setPositiveButton("행사 변경", (dialog, which) -> showStart()).show();
    }

    @Override
    public void onBackPressed() {
        if (webView == null) super.onBackPressed();
        else confirmExit();
    }

    private void disposeWebView() {
        if (webView == null) return;
        webView.stopLoading();
        ((LinearLayout) webView.getParent()).removeView(webView);
        webView.destroy();
        webView = null;
    }

    @Override
    protected void onDestroy() {
        disposeWebView();
        super.onDestroy();
    }
}
