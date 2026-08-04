package com.palmistry.paymentdiagnostic;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 将支付匹配通知上报后端。
 */
public final class PaymentNotifyClient {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private PaymentNotifyClient() {
    }

    static void report(Context context, String amountWithSymbol, String title, String body, long postedAt) {
        if (!NotifyConfig.isConfigured(context)) {
            NotifyConfig.saveLastNotifyResult(context, "未配置后端地址或密钥");
            return;
        }

        EXECUTOR.execute(() -> {
            String status = postNotify(context, amountWithSymbol, title, body, postedAt);
            NotifyConfig.saveLastNotifyResult(context, status);
        });
    }

    private static String postNotify(
            Context context,
            String amountWithSymbol,
            String title,
            String body,
            long postedAt
    ) {
        HttpURLConnection connection = null;
        try {
            String backendUrl = NotifyConfig.getBackendUrl(context);
            if (!backendUrl.endsWith("/")) {
                backendUrl = backendUrl + "/";
            }
            URL url = new URL(backendUrl + "api/payment/notify");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("x-payment-secret", NotifyConfig.getNotifySecret(context));

            JSONObject payload = new JSONObject();
            payload.put("amount", normalizeAmount(amountWithSymbol));
            payload.put("title", title);
            payload.put("body", body);
            payload.put("postedAt", postedAt);

            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(bytes);
            }

            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String responseBody = readStream(stream);
            if (code >= 200 && code < 300) {
                return "上报成功 HTTP " + code + " " + responseBody;
            }
            return "上报失败 HTTP " + code + " " + responseBody;
        } catch (Exception error) {
            return "上报异常: " + error.getMessage();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String normalizeAmount(String amountWithSymbol) {
        if (amountWithSymbol == null) {
            return "";
        }
        return amountWithSymbol.replace("¥", "").replace("￥", "").trim();
    }

    private static String readStream(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }
}
