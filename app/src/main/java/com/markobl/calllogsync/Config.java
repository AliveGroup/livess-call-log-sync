package com.markobl.calllogsync;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.google.gson.Gson;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class Config {

    static final String DEFAULT_ENDPOINT =
            "https://api.livess.com.br/webhooks/inbound/call-tracking";

    public URL endpoint;

    public String deviceName;
    public String deviceToken;
    public String deviceNumber;
    public boolean deviceLocked = false;

    public Map<String, String> additionalHeaders = new HashMap<>();

    @SuppressLint("HardwareIds")
    public static Config newConfig(Context context) {
        Config config = new Config();
        config.endpoint = getDefaultEndpoint();
        config.deviceName = Build.MODEL;
        config.deviceToken = getStableDeviceToken(context);
        config.deviceNumber = "";

        return  config;
    }

    private static String getStableDeviceToken(Context context) {
        try {
            String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            if (androidId == null || androidId.trim().isEmpty()) {
                return RandomString.getRandomString(32);
            }

            String input = String.format("%s|%s|%s", context.getPackageName(), Build.SERIAL, androidId);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] raw = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            StringBuilder token = new StringBuilder();
            for (byte b : raw) {
                token.append(String.format("%02x", b));
            }

            return token.substring(0, 32);
        }
        catch (Exception ex) {
            Log.e("CONFIG", "Failed to derive stable device token; falling back to random", ex);
            return RandomString.getRandomString(32);
        }
    }

    private static URL getDefaultEndpoint() {
        try {
            return new URL(DEFAULT_ENDPOINT);
        } catch (Exception ex) {
            Log.e("CONFIG", "Invalid built-in endpoint", ex);
            return null;
        }
    }

    public static SharedPreferences getSharedPreferences(Context context)
    {
        return context.getSharedPreferences("callhistory", Context.MODE_PRIVATE);
    }

    public static void reset(Context context)
    {
        Config config = Config.newConfig(context);
        config.save(context);
        Config.setLastCallLogId(context, -1);
    }

    public static Config load(@NonNull Context context)
    {
        boolean shouldSave = false;
        try {
            SharedPreferences settings = getSharedPreferences(context);
            if (settings.contains("config")) {
                String configJson = settings.getString("config", "{}");
                Gson gson = new Gson();
                Config config = gson.fromJson(configJson, Config.class);

                if (config.endpoint == null) {
                    config.endpoint = getDefaultEndpoint();
                    shouldSave = true;
                }
                if (config.deviceName == null || config.deviceName.trim().isEmpty()) {
                    config.deviceName = Build.MODEL;
                    shouldSave = true;
                }
                if (config.deviceToken == null || config.deviceToken.trim().isEmpty()) {
                    config.deviceToken = getStableDeviceToken(context);
                    shouldSave = true;
                }
                if(config.deviceNumber == null) {
                    config.deviceNumber = "";
                    shouldSave = true;
                }

                if(config.additionalHeaders == null) {
                    config.additionalHeaders = new HashMap<>();
                    shouldSave = true;
                }

                if (shouldSave) {
                    config.save(context);
                }

                return config;
            }
        }
        catch (Exception ex) {
            Log.e("CONFIG", "" + ex);
        }

        Config config = newConfig(context);
        config.save(context);
        return config;
    }

    public boolean save(@NonNull Context context)
    {
        try {
            Gson gson = new Gson();
            String json = gson.toJson(this);

            SharedPreferences settings = getSharedPreferences(context);
            SharedPreferences.Editor editor = settings.edit();

            editor.putString("config", json);

            editor.apply();
            return  true;
        }
        catch (Exception ex)
        {
            Log.e("CONFIG", "" + ex);
        }
        return  false;
    }

    public static long getLastCallLogId(Context context)
    {
        SharedPreferences settings = getSharedPreferences(context);
        return  settings.getLong("lastcalllogid", -1);
    }

    public static boolean setLastCallLogId(Context context, long lastCallLogId)
    {
        SharedPreferences settings = getSharedPreferences(context);
        SharedPreferences.Editor editor = settings.edit();

        editor.putLong("lastcalllogid", lastCallLogId);
        return editor.commit();
    }
}
