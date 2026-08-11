package uk.co.pactsolutions.teslachecklist;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

final class TeslaOrderClient {
    interface Callback {
        void onSuccess(List<TeslaOrder> orders);
        void onError(String message);
    }

    static final class TeslaOrder {
        String referenceNumber = "";
        String vin = "";
        String modelCode = "";
        String countryCode = "";
        String eddStart = "";
        String eddEnd = "";
        String collectionDate = "";
        String collectionTime = "";
        String collectionSummary = "";
        String collectionCentre = "";

        String title() {
            String model = modelCode.isEmpty() ? "Tesla order" : "Model " + modelCode;
            return model + (referenceNumber.isEmpty() ? "" : " · " + referenceNumber);
        }
    }

    private static final String TOKEN_URL = "https://auth.tesla.com/oauth2/v3/token";
    private static final String ORDERS_URL = "https://owner-api.teslamotors.com/api/1/users/orders";
    private static final String TASKS_URL = "https://akamai-apigateway-vfx.tesla.com/tasks"
        + "?deviceLanguage=en&deviceCountry=GB&appVersion=9.99.9-9999&referenceNumber=";
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final TeslaTokenStore tokens;

    TeslaOrderClient(Context context) {
        this.context = context.getApplicationContext();
        tokens = new TeslaTokenStore(context);
    }

    void fetch(Callback callback) {
        new Thread(() -> {
            try {
                String access = validAccessToken();
                JsonObject ordersResponse = getJson(ORDERS_URL, access);
                JsonArray orders = responseArray(ordersResponse);
                List<TeslaOrder> mapped = new ArrayList<>();
                for (JsonElement element : orders) {
                    if (!element.isJsonObject()) continue;
                    TeslaOrder order = mapBasicOrder(element.getAsJsonObject());
                    if (!order.referenceNumber.isEmpty()) {
                        try {
                            JsonObject details = getJson(
                                TASKS_URL + URLEncoder.encode(order.referenceNumber, "UTF-8"), access
                            );
                            mapDetails(order, details);
                        } catch (Exception ignored) { }
                    }
                    mapped.add(order);
                }
                main.post(() -> callback.onSuccess(mapped));
            } catch (Exception error) {
                main.post(() -> callback.onError(readableError(error)));
            }
        }).start();
    }

    void disconnect() {
        tokens.clear();
    }

    private String validAccessToken() throws Exception {
        if (tokens.expiresAt() > System.currentTimeMillis() + 60_000L) return tokens.accessToken();
        String refresh = tokens.refreshToken();
        if (refresh.isEmpty()) throw new IllegalStateException("Connect your Tesla account first");
        String body = form("grant_type", "refresh_token")
            + "&" + form("client_id", "ownerapi")
            + "&" + form("refresh_token", refresh);
        JsonObject response = requestJson(TOKEN_URL, "POST", body, null);
        if (!response.has("access_token")) throw new IllegalStateException("Tesla sign-in has expired");
        String access = response.get("access_token").getAsString();
        String nextRefresh = response.has("refresh_token")
            ? response.get("refresh_token").getAsString() : refresh;
        long expires = System.currentTimeMillis()
            + (response.has("expires_in") ? response.get("expires_in").getAsLong() : 28_800L) * 1000L;
        tokens.save(access, nextRefresh, expires);
        return access;
    }

    private JsonObject getJson(String url, String accessToken) throws Exception {
        return requestJson(url, "GET", null, accessToken);
    }

    private JsonObject requestJson(String url, String method, String body, String accessToken) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(25000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "TeslaApp/4.48.0-3600/Android/16");
            if (accessToken != null) connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int status = connection.getResponseCode();
            String response = read(status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream());
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Tesla returned " + status);
            }
            JsonElement parsed = JsonParser.parseString(response);
            if (!parsed.isJsonObject()) throw new IllegalStateException("Tesla returned an unexpected response");
            return parsed.getAsJsonObject();
        } finally {
            connection.disconnect();
        }
    }

    private static JsonArray responseArray(JsonObject root) {
        JsonElement response = root.get("response");
        return response != null && response.isJsonArray() ? response.getAsJsonArray() : new JsonArray();
    }

    private static TeslaOrder mapBasicOrder(JsonObject json) {
        TeslaOrder order = new TeslaOrder();
        order.referenceNumber = firstString(json, "referenceNumber", "orderNumber");
        order.vin = firstString(json, "vin", "vehicleVin").toUpperCase(Locale.UK);
        order.modelCode = firstString(json, "modelCode", "model");
        order.countryCode = firstString(json, "countryCode", "country").toUpperCase(Locale.UK);
        return order;
    }

    private static void mapDetails(TeslaOrder order, JsonObject details) {
        if (order.vin.isEmpty()) order.vin = findString(details, "vin", "vehicleVin").toUpperCase(Locale.UK);
        String window = findString(details, "deliveryWindowDisplay", "deliveryWindow");
        String start = findString(details, "deliveryWindowStartDate", "deliveryWindowStart", "eddStartDate");
        String end = findString(details, "deliveryWindowEndDate", "deliveryWindowEnd", "eddEndDate");
        String[] parsedWindow = parseDeliveryWindow(window);
        order.eddStart = compactDate(!start.isEmpty() ? start : parsedWindow[0]);
        order.eddEnd = compactDate(!end.isEmpty() ? end : parsedWindow[1]);

        String appointment = findString(
            details, "deliveryAppointmentDate", "appointmentDateTime", "apptDateTime",
            "scheduledDateTime", "deliveryAppointment"
        );
        order.collectionSummary = findString(details, "apptDateTimeAddressStr", "appointmentDisplay");
        order.collectionCentre = findString(
            details, "deliveryAddressTitle", "deliveryCenterName", "deliveryCentreName"
        );
        Date appointmentDate = parseDate(appointment);
        if (appointmentDate == null) appointmentDate = parseDate(order.collectionSummary);
        if (appointmentDate != null) {
            order.collectionDate = new SimpleDateFormat("dd MMM yyyy", Locale.UK)
                .format(appointmentDate).toUpperCase(Locale.UK);
            order.collectionTime = new SimpleDateFormat("HH:mm", Locale.UK).format(appointmentDate);
        }
    }

    private static String findString(JsonElement element, String... keys) {
        if (element == null || element.isJsonNull()) return "";
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            String direct = firstString(object, keys);
            if (!direct.isEmpty()) return direct;
            for (java.util.Map.Entry<String, JsonElement> entry : object.entrySet()) {
                String nested = findString(entry.getValue(), keys);
                if (!nested.isEmpty()) return nested;
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                String nested = findString(child, keys);
                if (!nested.isEmpty()) return nested;
            }
        }
        return "";
    }

    private static String firstString(JsonObject object, String... keys) {
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && !value.isJsonNull() && value.isJsonPrimitive()) {
                String text = value.getAsString().trim();
                if (!text.isEmpty() && !"null".equalsIgnoreCase(text)) return text;
            }
        }
        return "";
    }

    private static String[] parseDeliveryWindow(String window) {
        if (window == null || window.trim().isEmpty()) return new String[] {"", ""};
        String normalized = window.replace('–', '-').replace('—', '-').trim();
        String[] halves = normalized.split("\\s+-\\s+", 2);
        if (halves.length != 2) return new String[] {"", ""};
        int year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        String left = halves[0].trim();
        String right = halves[1].trim();
        if (!left.matches(".*\\d{4}.*") && !right.matches(".*\\d{4}.*")) {
            left += " " + year;
            right += " " + year;
        } else if (!left.matches(".*\\d{4}.*") && right.matches(".*\\d{4}.*")) {
            left += " " + right.replaceAll(".*?(\\d{4}).*", "$1");
        }
        return new String[] {left, right};
    }

    private static String compactDate(String source) {
        Date date = parseDate(source);
        return date == null ? "" : new SimpleDateFormat("dd MMM yyyy", Locale.UK)
            .format(date).toUpperCase(Locale.UK);
    }

    private static Date parseDate(String source) {
        if (source == null || source.trim().isEmpty()) return null;
        String value = source.trim().replaceAll("(?i)(st|nd|rd|th)", "");
        String[] patterns = {
            "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd", "dd MMM yyyy HH:mm",
            "dd MMMM yyyy HH:mm", "dd MMM yyyy", "dd MMMM yyyy",
            "MMM dd yyyy", "MMMM dd yyyy", "EEE, dd MMM yyyy HH:mm"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.UK);
                format.setLenient(false);
                format.setTimeZone(TimeZone.getDefault());
                return format.parse(value);
            } catch (Exception ignored) { }
        }
        return null;
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

    private static String readableError(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) return "Tesla order import failed";
        if (message.contains("401") || message.contains("403")) {
            return "Tesla rejected this connection. Disconnect and sign in again.";
        }
        return message;
    }
}
