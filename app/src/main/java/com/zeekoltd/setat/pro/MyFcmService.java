package com.zeekoltd.setat.pro;

import android.app.Activity;
import android.widget.FrameLayout;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.google.firebase.messaging.FirebaseMessaging;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class MyFcmService extends FirebaseMessagingService {

    private static final String CHANNEL_ID = "high_priority_channel";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        // 1. النظام القديم: الإشعارات القادمة من لوحة تحكم فايربيس
        if (remoteMessage.getNotification() != null) {
            String title = remoteMessage.getNotification().getTitle();
            String body = remoteMessage.getNotification().getBody();
            
            saveNotification(title, body);
            sendSystemNotification(title, body, null);
            return;
        }

        // 2. النظام الجديد: إشعارات المحادثة المخصصة (Data Payload)
        if (remoteMessage.getData().size() > 0) {
            Map<String, String> data = remoteMessage.getData();
            String type = data.get("type");

            if ("chat".equals(type)) {
                String title = data.get("title");
                String body = data.get("body");
                String roomUid = data.get("room_uid");

                if (roomUid != null && roomUid.equals(SupportChatActivity.currentActiveRoom)) {
                    return;
                }

                Activity currentActivity = getCurrentActivity();

                if (currentActivity != null) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        showInAppGlassNotification(currentActivity, title, body, roomUid);
                    });
                } else {
                    sendSystemNotification(title, body, roomUid);
                }
            }
        }
    }

    @Override
    public void handleIntent(Intent intent) {
        try {
            if (intent == null || intent.getExtras() == null) {
                super.handleIntent(intent);
                return;
            }
            
            Bundle extras = intent.getExtras();
            if (extras.containsKey("google.message_id") || extras.containsKey("gcm.message_id")) {
                onMessageReceived(new RemoteMessage(extras));
                return;
            }
        } catch (Exception e) {
            Log.e("FCM_ERROR", "خطأ في handleIntent: " + e.getMessage());
        }
        super.handleIntent(intent);
    }

    private void sendSystemNotification(String title, String body, String roomUid) {
        Intent intent;
        
        if (roomUid != null && !roomUid.isEmpty()) {
            intent = new Intent(this, SupportChatActivity.class);
            intent.putExtra("room_uid", roomUid);
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            
            // ⚠️ ملاحظة هامة: يجب تغيير كود الأدمن هذا لاحقاً عندما تنشئ حساب الأدمن في الفايربيس الجديد
            boolean isAdmin = (user != null && "sHIadaIos5cPW5rQoidBGZsqDHO2".equals(user.getUid()));
            intent.putExtra("is_admin", isAdmin);
        } else {
            intent = new Intent(this, MainActivity.class);
            intent.putExtra("from_notification", true);
        }
        
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        
        int flags = PendingIntent.FLAG_ONE_SHOT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        
        PendingIntent pendingIntent = PendingIntent.getActivity(this, (int) System.currentTimeMillis(), intent, flags);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "App Notifications", NotificationManager.IMPORTANCE_HIGH);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        int smallIcon = getApplicationInfo().icon;
        if (smallIcon == 0) {
            smallIcon = android.R.drawable.ic_dialog_info;
        }
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(smallIcon)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setContentIntent(pendingIntent);

        if (manager != null) {
            manager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    // 🌟 استدعاء وتصميم النافذة الاحترافية باستخدام XML 🌟
    private void showInAppGlassNotification(Activity activity, String title, String body, String roomUid) {
        final ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
        final float density = activity.getResources().getDisplayMetrics().density;

        // جلب التصميم المخصص من XML
        View popupView = LayoutInflater.from(activity).inflate(R.layout.layout_notification_popup, decorView, false);

        // ربط العناصر
        FrameLayout blurView = popupView.findViewById(R.id.blurViewPopup);
        TextView tvTitle = popupView.findViewById(R.id.tvPopupTitle);
        TextView tvBody = popupView.findViewById(R.id.tvPopupBody);

        tvTitle.setText(title);
        tvBody.setText(body);

        // إنشاء خلفية "الدمعة" (الزاوية العلوية اليسرى حادة، والباقي دائري)
        GradientDrawable bgShape = new GradientDrawable();
        bgShape.setShape(GradientDrawable.RECTANGLE);
        bgShape.setColor(Color.parseColor("#6607A38A")); 
        float radius = 25f * density;
        bgShape.setCornerRadii(new float[]{0, 0, radius, radius, radius, radius, radius, radius});
        bgShape.setStroke((int) (1.5f * density), Color.parseColor("#4DFFFFFF")); 

        // تطبيق الزجاج وشكل الدمعة
        blurView.setBackground(bgShape);
        blurView.setClipToOutline(true);

        // حركات الدخول الانسيابية
        popupView.setTranslationY(-300f * density);
        popupView.setAlpha(0f);
        popupView.animate()
                .translationY(0)
                .alpha(1f)
                .setDuration(600)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .start();

        // فتح المحادثة عند النقر
        popupView.setOnClickListener(v -> {
            decorView.removeView(popupView);
            Intent intent = new Intent(activity, SupportChatActivity.class);
            intent.putExtra("room_uid", roomUid);
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            
            // ⚠️ ملاحظة هامة للأدمن
            boolean isAdmin = (user != null && "sHIadaIos5cPW5rQoidBGZsqDHO2".equals(user.getUid()));
            intent.putExtra("is_admin", isAdmin);
            activity.startActivity(intent);
        });

        // إخفاء تلقائي بعد 4 ثوانٍ
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (popupView.getParent() != null) {
                popupView.animate()
                        .translationY(-300f * density)
                        .alpha(0f)
                        .setDuration(400)
                        .withEndAction(() -> decorView.removeView(popupView))
                        .start();
            }
        }, 4000);

        // إضافة الإشعار للواجهة
        decorView.addView(popupView);
    }

    private void saveNotification(String title, String body) {
        try {
            SharedPreferences sharedPreferences = getSharedPreferences("notifications", Context.MODE_PRIVATE);
            String existing = sharedPreferences.getString("notification_list", "");
            
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            String timestamp = sdf.format(new Date());
            
            String newNotification = "📅 " + timestamp + "\n" +
                                    "📌 " + (title != null ? title : "بدون عنوان") + "\n" +
                                    "📝 " + (body != null ? body : "بدون محتوى") + "\n\n";
            
            String updatedNotifications;
            if (existing.isEmpty()) {
                updatedNotifications = newNotification;
            } else {
                updatedNotifications = newNotification + "---\n\n" + existing;
            }
            
            sharedPreferences.edit().putString("notification_list", updatedNotifications).apply();
            
        } catch (Exception e) {
            Log.e("SAVE_ERROR", "فشل حفظ الإشعار: " + e.getMessage());
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        
        // 🌟 التعديل السحري: التنظيف الشامل وإلغاء الاشتراك من جميع الغرف القديمة لضمان عدم تداخل الإشعارات
        FirebaseMessaging.getInstance().unsubscribeFromTopic("all");
        FirebaseMessaging.getInstance().unsubscribeFromTopic("all_users");
        FirebaseMessaging.getInstance().unsubscribeFromTopic("all_19");
        FirebaseMessaging.getInstance().unsubscribeFromTopic("all_27");
        FirebaseMessaging.getInstance().unsubscribeFromTopic("all_28");
        
        // الاشتراك الحصري في التوبيك الجديد الخاص بهذه النسخة الاحترافية (2026)
        FirebaseMessaging.getInstance().subscribeToTopic("pro_2026");
    }

    private Activity getCurrentActivity() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Object activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null);
            Field activitiesField = activityThreadClass.getDeclaredField("mActivities");
            activitiesField.setAccessible(true);
            
            Map<Object, Object> activities = (Map<Object, Object>) activitiesField.get(activityThread);
            if (activities == null) return null;

            for (Object activityRecord : activities.values()) {
                Class<?> activityRecordClass = activityRecord.getClass();
                Field pausedField = activityRecordClass.getDeclaredField("paused");
                pausedField.setAccessible(true);
                
                if (!pausedField.getBoolean(activityRecord)) {
                    Field activityField = activityRecordClass.getDeclaredField("activity");
                    activityField.setAccessible(true);
                    return (Activity) activityField.get(activityRecord);
                }
            }
        } catch (Exception e) {
            Log.e("MyFcmService", "Failed to get current activity: " + e.getMessage());
        }
        return null;
    }
}
