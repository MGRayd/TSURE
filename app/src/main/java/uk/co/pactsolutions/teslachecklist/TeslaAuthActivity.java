package uk.co.pactsolutions.teslachecklist;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

public class TeslaAuthActivity extends Activity {
    private static final String CLIENT_ID = "ownerapi";
    private static final String REDIRECT_URI = "tesla://auth/callback";
    private static final String AUTH_URL = "https://auth.tesla.com/oauth2/v3/authorize";
    private static final String TOKEN_URL = "https://auth.tesla.com/oauth2/v3/token";

    private WebView webView;
    private ProgressBar progress;
    private String verifier;
    private String state;
    private boolean exchanging;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.rgb(10, 12, 18));
        webView = new WebView(this);
        progress = new ProgressBar(this);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(56, 56);
        progressParams.gravity = Gravity.CENTER;
        frame.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        frame.addView(progress, progressParams);
        setContentView(frame);

        verifier = randomUrlSafe(48);
        state = randomUrlSafe(24);
        String challenge = sha256UrlSafe(verifier);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleRedirect(request.getUrl());
            }

            @Override public void onPageFinished(WebView view, String url) {
                progress.setVisibility(android.view.View.GONE);
                Uri uri = Uri.parse(url);
                handleRedirect(uri);
            }
        });
        Uri auth = Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", "openid email offline_access")
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build();
        webView.loadUrl(auth.toString());
    }

    private boolean handleRedirect(Uri uri) {
        if (!"tesla".equalsIgnoreCase(uri.getScheme()) || !"auth".equalsIgnoreCase(uri.getHost())) {
            return false;
        }
        if (exchanging) return true;
        String returnedState = uri.getQueryParameter("state");
        String code = uri.getQueryParameter("code");
        if (code == null || !state.equals(returnedState)) {
            fail("Tesla sign-in response could not be verified");
            return true;
        }
        exchanging = true;
        progress.setVisibility(android.view.View.VISIBLE);
        webView.setVisibility(android.view.View.INVISIBLE);
        new Thread(() -> exchange(code)).start();
        return true;
    }

    private void exchange(String code) {
        HttpURLConnection connection = null;
        try {
            String body = form("grant_type", "authorization_code")
                + "&" + form("client_id", CLIENT_ID)
                + "&" + form("code", code)
                + "&" + form("redirect_uri", REDIRECT_URI)
                + "&" + form("code_verifier", verifier);
            connection = (HttpURLConnection) new URL(TOKEN_URL).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(20000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("Accept", "application/json");
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            JsonObject json = JsonParser.parseString(read(
                status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream()
            )).getAsJsonObject();
            if (status < 200 || status >= 300 || !json.has("access_token")) {
                throw new IllegalStateException("Tesla rejected the token exchange (" + status + ")");
            }
            long expires = System.currentTimeMillis() + json.get("expires_in").getAsLong() * 1000L;
            new TeslaTokenStore(this).save(
                json.get("access_token").getAsString(),
                json.get("refresh_token").getAsString(),
                expires
            );
            runOnUiThread(() -> {
                setResult(RESULT_OK);
                finish();
            });
        } catch (Exception error) {
            fail("Tesla connection failed: " + error.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void fail(String message) {
        runOnUiThread(() -> {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            setResult(RESULT_CANCELED);
            finish();
        });
    }

    private static String form(String key, String value) throws Exception {
        return URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8");
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "{}";
        StringBuilder value = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) value.append(line);
        }
        return value.toString();
    }

    private static String randomUrlSafe(int bytes) {
        byte[] value = new byte[bytes];
        new SecureRandom().nextBytes(value);
        return Base64.encodeToString(value, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static String sha256UrlSafe(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.US_ASCII));
            return Base64.encodeToString(digest, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    @Override protected void onDestroy() {
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
