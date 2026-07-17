package com.markobl.calllogsync;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.provider.CallLog;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class Sync {
    private static final String SYNC_ENABLED_KEY = "sync-enabled";
    private static final String LAST_CALL_LOG_ID_KEY = "lastcalllogid";

    public static boolean isEnabled(@NonNull final Context context) {
        SharedPreferences settings = Config.getSharedPreferences(context);
        if (!settings.contains(SYNC_ENABLED_KEY)) {
            return enableByDefault(context, settings);
        }
        return settings.getBoolean(SYNC_ENABLED_KEY, false);
    }

    private static boolean enableByDefault(@NonNull final Context context, SharedPreferences settings) {
        SharedPreferences.Editor edit = settings.edit();
        edit.putBoolean(SYNC_ENABLED_KEY, true);

        if (!settings.contains(LAST_CALL_LOG_ID_KEY)) {
            try {
                edit.putLong(LAST_CALL_LOG_ID_KEY, getLastCallLogId(context));
            }
            catch (Exception ex) {
                Log.e("SYNC", "Could not initialize call log cursor; starting from -1", ex);
                edit.putLong(LAST_CALL_LOG_ID_KEY, -1);
            }
        }

        return edit.commit();
    }

    public static boolean setEnabled(@NonNull final Context context, boolean enabled) {
        SharedPreferences settings = Config.getSharedPreferences(context);
        SharedPreferences.Editor edit = settings.edit();
        edit.putBoolean(SYNC_ENABLED_KEY, enabled);
        return edit.commit();
    }

    public  static long getLastCallLogId(Context context)
    {
        Bundle queryArgs = new Bundle();

        queryArgs.putInt(ContentResolver.QUERY_ARG_LIMIT, 1);
        queryArgs.putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "_id DESC");

        final Cursor managedCursor = context.getContentResolver().query(CallLog.Calls.CONTENT_URI, null, queryArgs, null);
        final int idIndex = managedCursor.getColumnIndex("_id");

        long id = -1;
        while (managedCursor.moveToNext())
            id = Math.max(managedCursor.getLong(idIndex), id);

        managedCursor.close();

        return id;
    }

    public static void syncCallHistory(@NonNull final Context context,
                                       @NonNull final Config config,
                                       final long lastCallLogId,
                                       @NonNull final SyncResultRunner syncResultRunner) {
        Handler mainHandler = new Handler(context.getMainLooper());
        new Thread(() -> {
            SyncResult result = syncCallHistoryBlocking(context, config, lastCallLogId);
            mainHandler.post(() -> syncResultRunner.run(result));
        }).start();
    }

    public static void testEndpoint(@NonNull final Context context,
                                    @NonNull final Config config,
                                    @NonNull final SyncResultRunner syncResultRunner) {
        Handler mainHandler = new Handler(context.getMainLooper());
        new Thread(() -> {
            SyncResult result = testEndpointBlocking(config);
            mainHandler.post(() -> syncResultRunner.run(result));
        }).start();
    }

    static SyncResult testEndpointBlocking(@NonNull final Config config) {
        config.additionalHeaders.put("Test-Run", "1");
        return postPayload(config, new JSONArray(), 0L, 0);
    }

    static boolean isAllowedEndpoint(URL endpoint, boolean allowInsecureEndpoints) {
        if (endpoint == null)
            return false;
        if ("https".equalsIgnoreCase(endpoint.getProtocol()))
            return true;
        return allowInsecureEndpoints && "http".equalsIgnoreCase(endpoint.getProtocol());
    }

    static boolean isSuccessfulHttpCode(int responseCode) {
        return responseCode >= 200 && responseCode < 300;
    }

    public static SyncResult syncCallHistoryBlocking(@NonNull final Context context,
                                                     @NonNull final Config config,
                                                     final long lastCallLogId) {
        try {
            if (!isAllowedEndpoint(config.endpoint, BuildConfig.ALLOW_INSECURE_ENDPOINTS))
                return new SyncResult("A secure HTTPS endpoint is required", 400);

            long lastId = lastCallLogId;
            if (lastId == -1)
                lastId = getLastCallLogId(context) - 1;

            Bundle queryArgs = new Bundle();
            queryArgs.putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "_id ASC");
            queryArgs.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "_id > ?");
            queryArgs.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                    new String[]{Long.toString(lastId)});

            final JSONArray json = new JSONArray();
            int count = 0;

            try (Cursor managedCursor = context.getContentResolver().query(
                    CallLog.Calls.CONTENT_URI, null, queryArgs, null)) {
                if (managedCursor == null)
                    return new SyncResult(new IllegalStateException("Call log query returned no cursor"));

                final int idIndex = managedCursor.getColumnIndexOrThrow("_id");
                final int numberIndex = managedCursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER);
                final int typeIndex = managedCursor.getColumnIndexOrThrow(CallLog.Calls.TYPE);
                final int dateIndex = managedCursor.getColumnIndexOrThrow(CallLog.Calls.DATE);
                final int durationIndex = managedCursor.getColumnIndexOrThrow(CallLog.Calls.DURATION);

                while (managedCursor.moveToNext()) {
                    final long id = managedCursor.getLong(idIndex);
                    lastId = Math.max(lastId, id);

                    final String number = managedCursor.getString(numberIndex);
                    if (number == null)
                        continue;

                    final StringBuilder numberBuilder = new StringBuilder();
                    for (int i = 0; i < number.length(); i++) {
                        char c = number.charAt(i);
                        if (c == '+')
                            numberBuilder.append("00");
                        else if ((c >= '0' && c <= '9') || c == '#' || c == '*')
                            numberBuilder.append(c);
                    }

                    final String finalNumber = numberBuilder.toString();
                    if (finalNumber.length() == 0)
                        continue;

                    JSONObject item = new JSONObject();
                    item.put("ID", id);
                    item.put("NUMBER", finalNumber);
                    item.put("TYPE", managedCursor.getInt(typeIndex));
                    item.put("DATE", managedCursor.getLong(dateIndex));
                    item.put("DURATION", managedCursor.getLong(durationIndex));
                    json.put(item);
                    count++;
                }
            }

            if (count == 0)
                return new SyncResult(lastId);

            if (!isEnabled(context))
                return new SyncResult("Sync disabled before upload", 409);

            return postPayload(config, json, lastId, count);
        } catch (Exception ex) {
            Log.e("SYNC", "" + ex);
            return new SyncResult(ex);
        }
    }

    private static SyncResult postPayload(@NonNull final Config config,
                                          @NonNull final JSONArray json,
                                          final long lastId,
                                          final int count) {
        HttpURLConnection connection = null;
        try {
            if (!isAllowedEndpoint(config.endpoint, BuildConfig.ALLOW_INSECURE_ENDPOINTS))
                return new SyncResult("A secure HTTPS endpoint is required", 400);

            byte[] data = json.toString().getBytes(StandardCharsets.UTF_8);
            connection = (HttpURLConnection) config.endpoint.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            connection.setRequestProperty("Content-Length", Long.toString(data.length));
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Device-Name", config.deviceName);
            connection.setRequestProperty("Device-Token", config.deviceToken);
            connection.setRequestProperty("Device-Number", config.deviceNumber);

            for (Map.Entry<String, String> entry : config.additionalHeaders.entrySet())
                connection.setRequestProperty(entry.getKey(), entry.getValue());

            connection.setInstanceFollowRedirects(true);
            connection.setDoOutput(true);
            connection.setConnectTimeout(25000);
            connection.setReadTimeout(25000);

            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(data);
            }

            int responseCode = connection.getResponseCode();
            InputStream responseStream = responseCode >= 100 && responseCode <= 399
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            StringBuilder response = new StringBuilder();
            if (responseStream != null) {
                try (BufferedReader responseReader = new BufferedReader(
                        new InputStreamReader(responseStream, StandardCharsets.UTF_8))) {
                    String responseLine;
                    while ((responseLine = responseReader.readLine()) != null)
                        response.append(responseLine);
                }
            }

            if (isSuccessfulHttpCode(responseCode))
                return new SyncResult(response.toString(), lastId, false, count);

            return new SyncResult(response.toString(), responseCode);
        } catch (Exception ex) {
            Log.e("SYNC", "" + ex);
            return new SyncResult(ex);
        } finally {
            if (connection != null)
                connection.disconnect();
        }
    }
}
