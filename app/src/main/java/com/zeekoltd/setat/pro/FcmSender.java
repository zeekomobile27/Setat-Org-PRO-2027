package com.zeekoltd.setat.pro;

import android.util.Log;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FcmSender {
    
    // رابط الجسر الآمن الجديد الخاص بك على Google Apps Script
    private static final String SCRIPT_URL = "https://script.google.com/macros/s/AKfycbwnOMUlVQ4oXyWEmDKoRHPlYvD8BisxVZtqnX_wKc_csIlY4rus0uuWnpm-Karj8OLY/exec";
    
    // استخدام Thread منفصل حتى لا يتأثر أداء واجهة المستخدم عند إرسال الإشعار
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static void sendChatNotification(String targetToken, String title, String body, String roomUid) {
        if (targetToken == null || targetToken.trim().isEmpty()) {
            return;
        }

        executor.execute(() -> {
            try {
                URL url = new URL(SCRIPT_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);

                // تجهيز البيانات بصيغة JSON كما يتوقعها الجسر
                JSONObject jsonParam = new JSONObject();
                jsonParam.put("token", targetToken);
                jsonParam.put("title", title);
                jsonParam.put("body", body);
                jsonParam.put("type", "chat");
                jsonParam.put("room_uid", roomUid != null ? roomUid : "");

                // إرسال الطلب
                OutputStream os = conn.getOutputStream();
                os.write(jsonParam.toString().getBytes("UTF-8"));
                os.flush();
                os.close();

                // قراءة كود الاستجابة للتأكد من نجاح العملية (200 يعني نجاح)
                int responseCode = conn.getResponseCode();
                Log.d("FcmSender", "Response Code: " + responseCode);

                conn.disconnect();
            } catch (Exception e) {
                Log.e("FcmSender", "Error sending FCM: " + e.getMessage());
            }
        });
    }
}
