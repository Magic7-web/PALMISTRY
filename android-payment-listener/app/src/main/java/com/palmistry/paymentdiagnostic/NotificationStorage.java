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

    /** 按优先级排列：更长、更具体的短语优先 */
    private static final String[] PAYMENT_KEYWORDS = {
            "你已成功收款",
            "已成功收款",
            "成功收款",
            "收款成功",
            "收到转账",
            "转账成功",
            "付款成功",
            "向你付款",
            "收款",
            "到账",
            "收到",
            "转入",
            "收钱"
    };

    /** 金额紧跟在收款语义之后 */
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(?:你已成功收款|成功收款|已成功收款|收款成功|收到转账|转账成功|付款成功|向你付款|收款|到账|收到|转入|收钱|付款)"
                    + "\\s*(\\d+(?:\\.\\d{1,2})?)\\s*元?"
    );

    /** 兜底：通知里任意位置的 x.xx 元 */
    private static final Pattern LOOSE_AMOUNT_PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d{1,2})?)\\s*元"
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
        return new PaymentMatch(matched, hits, amount, true, false);
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
        if (findPaymentKeyword(text) != null) {
            Matcher loose = LOOSE_AMOUNT_PATTERN.matcher(text);
            if (loose.find()) {
                return "¥" + loose.group(1);
            }
        }
        return null;
    }

    static PaymentMatch append(Context context, String title, String body, long postedAt) {
        return append(context, title, body, postedAt, null);
    }

    static PaymentMatch append(
            Context context,
            String title,
            String body,
            long postedAt,
            String notificationKey
    ) {
        PaymentMatch match = analyze(title, body);
        if (notificationKey != null && !notificationKey.isEmpty()) {
            int existingIndex = findNotificationKeyIndex(context, notificationKey);
            if (existingIndex >= 0) {
                return updateExistingRecord(context, existingIndex, title, body, postedAt, notificationKey, match);
            }
        }

        JSONObject item = buildRecordItem(title, body, postedAt, notificationKey, match);
        if (item == null) {
            return new PaymentMatch(match.matched, match.keywords, match.amount, false, false);
        }
        prependRecord(context, item);
        return new PaymentMatch(match.matched, match.keywords, match.amount, true, false);
    }

    private static JSONObject buildRecordItem(
            String title,
            String body,
            long postedAt,
            String notificationKey,
            PaymentMatch match
    ) {
        JSONObject item = new JSONObject();
        try {
            item.put("time", postedAt);
            item.put("title", title);
            item.put("body", body);
            item.put("matched", match.matched);
            item.put("keywords", joinKeywords(match.keywords));
            if (notificationKey != null && !notificationKey.isEmpty()) {
                item.put("notificationKey", notificationKey);
            }
            if (match.amount != null) {
                item.put("amount", match.amount);
            }
            return item;
        } catch (JSONException ignored) {
            return null;
        }
    }

    private static PaymentMatch updateExistingRecord(
            Context context,
            int index,
            String title,
            String body,
            long postedAt,
            String notificationKey,
            PaymentMatch match
    ) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray array = readArray(prefs);
        JSONObject existing = array.optJSONObject(index);
        if (existing == null) {
            return new PaymentMatch(match.matched, match.keywords, match.amount, false, false);
        }
        String oldBody = existing.optString("body", "");
        String oldAmount = existing.optString("amount", "");
        boolean contentChanged = !oldBody.equals(body)
                || (match.amount != null && !match.amount.equals(oldAmount));
        if (!contentChanged) {
            return new PaymentMatch(match.matched, match.keywords, match.amount, false, false);
        }

        JSONObject item = buildRecordItem(title, body, postedAt, notificationKey, match);
        if (item == null) {
            return new PaymentMatch(match.matched, match.keywords, match.amount, false, false);
        }
        JSONArray updated = new JSONArray();
        updated.put(item);
        for (int i = 0; i < array.length(); i += 1) {
            if (i == index) {
                continue;
            }
            updated.put(array.optJSONObject(i));
        }
        prefs.edit().putString(KEY_RECORDS, updated.toString()).apply();
        return new PaymentMatch(match.matched, match.keywords, match.amount, false, true);
    }

    private static void prependRecord(Context context, JSONObject item) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray array = readArray(prefs);
        JSONArray updated = new JSONArray();
        updated.put(item);
        int limit = Math.min(array.length(), MAX_RECORDS - 1);
        for (int i = 0; i < limit; i += 1) {
            updated.put(array.optJSONObject(i));
        }
        prefs.edit().putString(KEY_RECORDS, updated.toString()).apply();
    }

    private static int findNotificationKeyIndex(Context context, String notificationKey) {
        JSONArray array = readArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE));
        for (int i = 0; i < array.length(); i += 1) {
            JSONObject obj = array.optJSONObject(i);
            if (obj != null && notificationKey.equals(obj.optString("notificationKey", ""))) {
                return i;
            }
        }
        return -1;
    }

    static PaymentMatch getLatestMatchedRecord(Context context) {
        for (JSONObject record : loadRecords(context)) {
            if (record.optBoolean("matched", false) && record.has("amount")) {
                PaymentMatch match = analyze(
                        record.optString("title", ""),
                        record.optString("body", "")
                );
                return new PaymentMatch(true, match.keywords, record.optString("amount", ""), false, false);
            }
        }
        return null;
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
        final boolean appended;
        final boolean updated;

        PaymentMatch(boolean matched, List<String> keywords, String amount, boolean appended, boolean updated) {
            this.matched = matched;
            this.keywords = keywords;
            this.amount = amount;
            this.appended = appended;
            this.updated = updated;
        }
    }
}
