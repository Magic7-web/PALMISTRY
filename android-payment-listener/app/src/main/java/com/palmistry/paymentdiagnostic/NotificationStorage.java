package com.palmistry.paymentdiagnostic;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 本地通知记录读写与支付关键词匹配。
 */
public final class NotificationStorage {

    static final String PREFS = "diagnostic";
    private static final String KEY_RECORDS = "records";
    private static final int MAX_RECORDS = 30;

    /** 按优先级排列：更长、更具体的短语优先，避免「已成功收款」被识别成「成功收款」 */
    private static final String[] PAYMENT_KEYWORDS = {
            "已成功收款",
            "成功收款",
            "收款",
            "到账",
            "收到"
    };

    /** 金额必须紧跟在收款语义关键词之后，例如「成功收款4.99元」 */
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(?:成功收款|已成功收款|收款|到账|收到)\\s*(\\d+(?:\\.\\d{1,2})?)\\s*元?"
    );

    private NotificationStorage() {
    }

    static PaymentMatch analyze(String title, String body) {
        String combined = title + " " + body;
        String keyword = findPaymentKeyword(combined);
        boolean matched = keyword != null;
        List<String> hits = new ArrayList<>();
        if (keyword != null) {
            hits.add(keyword);
        }
        String amount = matched ? extractAmount(combined) : null;
        return new PaymentMatch(matched, hits, amount);
    }

    private static String findPaymentKeyword(String text) {
        for (String keyword : PAYMENT_KEYWORDS) {
            if (text.contains(keyword)) {
                return keyword;
            }
        }
        return null;
    }

    private static String extractAmount(String text) {
        Matcher matcher = AMOUNT_PATTERN.matcher(text);
        if (matcher.find()) {
            return "¥" + matcher.group(1);
        }
        return null;
    }

    static PaymentMatch append(Context context, String title, String body, long postedAt) {
        PaymentMatch match = analyze(title, body);
        JSONObject item = new JSONObject();
        try {
            item.put("time", postedAt);
            item.put("title", title);
            item.put("body", body);
            item.put("matched", match.matched);
            item.put("keywords", joinKeywords(match.keywords));
            if (match.amount != null) {
                item.put("amount", match.amount);
            }
        } catch (JSONException ignored) {
            return match;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray array = readArray(prefs);
        JSONArray updated = new JSONArray();
        updated.put(item);
        int limit = Math.min(array.length(), MAX_RECORDS - 1);
        for (int i = 0; i < limit; i++) {
            updated.put(array.optJSONObject(i));
        }
        prefs.edit().putString(KEY_RECORDS, updated.toString()).apply();
        return match;
    }

    static List<JSONObject> loadRecords(Context context) {
        JSONArray array = readArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE));
        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.optJSONObject(i);
            if (obj != null) {
                list.add(obj);
            }
        }
        return list;
    }

    static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_RECORDS)
                .apply();
    }

    static String formatForDisplay(Context context) {
        List<JSONObject> records = loadRecords(context);
        if (records.isEmpty()) {
            return "";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < records.size(); i++) {
            JSONObject obj = records.get(i);
            if (i > 0) {
                sb.append("\n\n");
            }
            boolean matched = obj.optBoolean("matched", false);
            sb.append(matched ? "[支付匹配]" : "[普通通知]").append('\n');
            long time = obj.optLong("time", 0L);
            if (time > 0L) {
                sb.append("时间: ").append(sdf.format(new Date(time))).append('\n');
            }
            sb.append("标题: ").append(obj.optString("title", "")).append('\n');
            sb.append("正文: ").append(obj.optString("body", ""));
            String keywords = obj.optString("keywords", "");
            if (!keywords.isEmpty()) {
                sb.append("\n关键词: ").append(keywords);
            }
            if (obj.has("amount")) {
                sb.append("\n金额: ").append(obj.optString("amount", ""));
            }
        }
        return sb.toString();
    }

    private static JSONArray readArray(SharedPreferences prefs) {
        String raw = prefs.getString(KEY_RECORDS, "[]");
        try {
            return new JSONArray(raw);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private static String joinKeywords(List<String> keywords) {
        if (keywords.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keywords.size(); i++) {
            if (i > 0) {
                sb.append("、");
            }
            sb.append(keywords.get(i));
        }
        return sb.toString();
    }

    static final class PaymentMatch {
        final boolean matched;
        final List<String> keywords;
        final String amount;

        PaymentMatch(boolean matched, List<String> keywords, String amount) {
            this.matched = matched;
            this.keywords = keywords;
            this.amount = amount;
        }
    }
}
