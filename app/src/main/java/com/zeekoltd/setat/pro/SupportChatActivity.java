package com.zeekoltd.setat.pro;

import android.content.Context;
import android.widget.FrameLayout;
import android.content.Intent;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.recyclerview.widget.*;
import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;
import com.vanniktech.emoji.EmojiManager;
import com.vanniktech.emoji.EmojiPopup;
import com.vanniktech.emoji.google.GoogleEmojiProvider;
import android.view.Window;
import android.view.Gravity;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.WindowManager;

public class SupportChatActivity extends AppCompatActivity {

    public static String currentActiveRoom = ""; // متغير لتتبع المحادثة النشطة حالياً
    private RecyclerView recyclerChat;
    private EditText etMessage;
    private ImageView btnSend, btnEmoji, imgVerifiedSupport, imgDevProfileTopBar, btnBanUser, btnDeleteUser;
    private TextView tvTypingIndicator, tvChatTitle, tvBackText, tvBannedMessage;
    private SupportChatAdapter adapter;
    private List<SupportChatMessage> messagesList;
    private DatabaseReference chatRef;
    private DatabaseReference metadataRef;
    private DatabaseReference banRef;
    private FirebaseAuth mAuth;
    private String currentLang = "ar";
    private SimpleDateFormat timeFormat;
    private EmojiPopup emojiPopup;
    private boolean isEmojiShowing = false;
    private boolean isKeyboardShowing = false;
    private static final String DEV_UID = "GrWIoyYfgCVU3BJjZzfu2T0PYdo2";
    private String roomUid;
    private boolean isAdmin;
    private String chatPartnerName;
    private String chatPartnerImage;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private ViewGroup appRootView;
    
    // 🌟 مساعد إعلانات أدموب الجديد 🌟
    private YandexAdsHelper yandexAdsHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyLanguage();
        EdgeToEdgeHelper.enable(getWindow());
        setContentView(R.layout.activity_support_chat);
        EmojiManager.install(new GoogleEmojiProvider());
        
        roomUid = getIntent().getStringExtra("room_uid");
        isAdmin = getIntent().getBooleanExtra("is_admin", false);
        chatPartnerName = getIntent().getStringExtra("partner_name");
        chatPartnerImage = getIntent().getStringExtra("partner_image");
        
        initializeChat();
    }

    private void initializeChat() {
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();
        
        // 🌟 تهيئة وتجهيز مساعد الإعلانات 🌟
        yandexAdsHelper = YandexAdsHelper.getInstance(this);
        yandexAdsHelper.setCurrentLanguage(currentLang);
        yandexAdsHelper.initialize(() -> runOnUiThread(() -> {}));

        if (roomUid == null || roomUid.isEmpty()) {
            if (user != null) {
                roomUid = user.getUid();
                isAdmin = false;
            } else {
                Toast.makeText(this, tr("خطأ", "Error"), Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }

        if (isAdmin) {
            chatRef = FirebaseDatabase.getInstance().getReference("support_messages").child(roomUid);
        } else {
            if (user != null) {
                chatRef = FirebaseDatabase.getInstance().getReference("support_messages").child(user.getUid());
            }
        }
        
        metadataRef = FirebaseDatabase.getInstance().getReference("support_metadata").child(roomUid);
        banRef = FirebaseDatabase.getInstance().getReference("banned_users").child(roomUid);
        timeFormat = new SimpleDateFormat("hh:mm a", new Locale(currentLang));

        appRootView = findViewById(R.id.mainContentView);
        
        // 🌟 حماية الواجهة لكي لا تتداخل مع شريط إشعارات الهاتف والأزرار السفلية 🌟
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            ViewCompat.setOnApplyWindowInsetsListener(appRootView, (v, insets) -> {
                View blurTopBar = findViewById(R.id.blurTopBar);
                View blurBottomBar = findViewById(R.id.blurBottomBar);
                float d = getResources().getDisplayMetrics().density;
                
                if (blurTopBar != null) {
                    ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) blurTopBar.getLayoutParams();
                    params.topMargin = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()).top + (int)(15 * d);
                    blurTopBar.setLayoutParams(params);
                }
                if (blurBottomBar != null) {
                    ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) blurBottomBar.getLayoutParams();
                    params.bottomMargin = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime()).bottom + (int)(15 * d);
                    blurBottomBar.setLayoutParams(params);
                }
                return insets;
            });
            ViewCompat.requestApplyInsets(appRootView);
        }

        recyclerChat = findViewById(R.id.recyclerChat);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnEmoji = findViewById(R.id.btnEmoji);
        tvTypingIndicator = findViewById(R.id.tvTypingIndicator);
        tvChatTitle = findViewById(R.id.tvChatTitle);
        tvBackText = findViewById(R.id.tvBackText);
        imgVerifiedSupport = findViewById(R.id.imgVerifiedSupport);
        imgDevProfileTopBar = findViewById(R.id.imgDevProfileTopBar);
        btnBanUser = findViewById(R.id.btnBanUser);
        btnDeleteUser = findViewById(R.id.btnDeleteUser);
        tvBannedMessage = findViewById(R.id.tvBannedMessage);
        LinearLayout btnBack = findViewById(R.id.btnBackFromChat);

        tvBackText.setText(tr("رجوع", "Back"));
        etMessage.setHint(tr("اكتب رسالتك...", "Type a message..."));
        tvBannedMessage.setText(tr("عذراً، لقد تم إيقافك من مراسلة الدعم الفني.", "Sorry, you have been banned from support chat."));

        if (isAdmin && chatPartnerName != null) {
            tvChatTitle.setText(chatPartnerName);
            imgVerifiedSupport.setVisibility(View.GONE);
            loadAvatarForTopBar(roomUid, imgDevProfileTopBar);
            
            btnBanUser.setVisibility(View.VISIBLE);
            btnBanUser.setOnClickListener(v -> toggleBanUser());

            // برمجة زر الحذف الجديد
            btnDeleteUser.setVisibility(View.VISIBLE);
            btnDeleteUser.setOnClickListener(v -> showDeleteUserDialog());
        } else {
            tvChatTitle.setText("ZEEKO ORG");
            imgVerifiedSupport.setVisibility(View.VISIBLE);
            loadAvatarForTopBar(DEV_UID, imgDevProfileTopBar);
            
            btnBanUser.setVisibility(View.GONE);
            btnDeleteUser.setVisibility(View.GONE);
        }

        messagesList = new ArrayList<>();
        adapter = new SupportChatAdapter(messagesList, mAuth);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerChat.setLayoutManager(layoutManager);
        recyclerChat.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());

        emojiPopup = new EmojiPopup(findViewById(android.R.id.content), etMessage);

        etMessage.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                isKeyboardShowing = true;
                if (isEmojiShowing) {
                    emojiPopup.dismiss();
                    isEmojiShowing = false;
                    btnEmoji.setImageResource(R.drawable.ic_emoji);
                }
            } else {
                isKeyboardShowing = false;
            }
        });

        etMessage.setOnClickListener(v -> {
            if (isEmojiShowing) {
                emojiPopup.dismiss();
                isEmojiShowing = false;
                btnEmoji.setImageResource(R.drawable.ic_emoji);
            }
            isKeyboardShowing = true;
        });

        btnEmoji.setOnClickListener(v -> {
            if (isEmojiShowing) {
                emojiPopup.dismiss();
                isEmojiShowing = false;
                btnEmoji.setImageResource(R.drawable.ic_emoji);
                etMessage.requestFocus();
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(etMessage, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                isKeyboardShowing = true;
            } else if (isKeyboardShowing) {
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(etMessage.getWindowToken(), 0);
                isKeyboardShowing = false;
                new Handler().postDelayed(() -> {
                    btnEmoji.setImageResource(R.drawable.ic_keyboard);
                    emojiPopup.toggle();
                    isEmojiShowing = true;
                }, 150);
            } else {
                btnEmoji.setImageResource(R.drawable.ic_keyboard);
                emojiPopup.toggle();
                isEmojiShowing = true;
            }
        });

        btnSend.setOnClickListener(v -> sendMessage());
        
        setupFrameLayouts();
        loadMessagesWithWelcome();
        resetUnreadCount();
        checkBanStatus();
    }
    
    // 🌟 دالة لفتح الرابط في المتصفح الداخلي 🌟
    public void openUrlInBrowser(String url) {
        if (yandexAdsHelper != null) yandexAdsHelper.markRewardedExternalLaunchStarted();
        try {
            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
            builder.setToolbarColor(Color.parseColor("#0A0F14"));
            CustomTabsIntent customTabsIntent = builder.build();
            customTabsIntent.launchUrl(this, Uri.parse(url));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception ex) {
                if (yandexAdsHelper != null) yandexAdsHelper.onRewardedExternalLaunchFailed();
                Toast.makeText(this, tr("خطأ في فتح الرابط", "Error opening link"), Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void toggleBanUser() {
        banRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Boolean isBanned = snapshot.getValue(Boolean.class);
                if (isBanned != null && isBanned) {
                    banRef.setValue(false).addOnSuccessListener(aVoid -> 
                        Toast.makeText(SupportChatActivity.this, tr("تم رفع الحظر عن المستخدم", "User unbanned"), Toast.LENGTH_SHORT).show()
                    );
                } else {
                    banRef.setValue(true).addOnSuccessListener(aVoid -> 
                        Toast.makeText(SupportChatActivity.this, tr("تم حظر المستخدم", "User banned"), Toast.LENGTH_SHORT).show()
                    );
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    private void checkBanStatus() {
        banRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Boolean isBanned = snapshot.getValue(Boolean.class);
                View blurBottomBar = findViewById(R.id.blurBottomBar);
                
                if (isBanned != null && isBanned) {
                    if (!isAdmin) {
                        if (blurBottomBar != null) blurBottomBar.setVisibility(View.GONE);
                        tvBannedMessage.setVisibility(View.VISIBLE);
                    } else {
                        btnBanUser.setColorFilter(Color.parseColor("#FF4C4C")); // تغيير لون زر المطور للإشارة بأن المستخدم محظور
                    }
                } else {
                    if (!isAdmin) {
                        if (blurBottomBar != null) blurBottomBar.setVisibility(View.VISIBLE);
                        tvBannedMessage.setVisibility(View.GONE);
                    } else {
                        btnBanUser.setColorFilter(Color.parseColor("#FFFFFF")); // اللون الطبيعي لزر المطور
                    }
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    // 🌟 تفعيل الزجاج للشريط العلوي والسفلي 🌟
    private void setupFrameLayouts() {}

    @Override
    public void onBackPressed() {
        if (isEmojiShowing) {
            emojiPopup.dismiss();
            isEmojiShowing = false;
            btnEmoji.setImageResource(R.drawable.ic_emoji);
        } else {
            super.onBackPressed();
        }
    }

    private void sendMessage() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, tr("الرجاء تسجيل الدخول", "Please login"), Toast.LENGTH_SHORT).show();
            return;
        }

        String messageText = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(messageText)) return;
        if (messageText.length() > 400) {
            Toast.makeText(this, tr("الرسالة طويلة جداً (الحد 400 حرف)", "Message too long (max 400 chars)"), Toast.LENGTH_SHORT).show();
            return;
        }

        String key = chatRef.push().getKey();
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("senderUid", user.getUid());
        messageData.put("senderName", user.getDisplayName() != null ? user.getDisplayName() : tr("مستخدم", "User"));
        messageData.put("messageText", messageText);
        messageData.put("timestamp", ServerValue.TIMESTAMP);

        chatRef.child(key).setValue(messageData)
            .addOnSuccessListener(aVoid -> {
                etMessage.setText("");
                updateMetadata(messageText);
                
                // إرسال إشعار للطرف الآخر بعد نجاح إضافة الرسالة لقاعدة البيانات
                String senderName = user.getDisplayName() != null ? user.getDisplayName() : tr("مستخدم", "User");
                sendNotificationToPartner(messageText, senderName);
            })
            .addOnFailureListener(e -> {
                Toast.makeText(this, tr("فشل الإرسال", "Send failed"), Toast.LENGTH_SHORT).show();
            });
    }

    private void sendNotificationToPartner(String messageBody, String senderName) {
        DatabaseReference tokenRef;
        if (isAdmin) {
            // أنا كأدمن أرسل للمستخدم، سأجلب توكن المستخدم
            tokenRef = FirebaseDatabase.getInstance().getReference("users").child(roomUid).child("fcm_token");
        } else {
            // المستخدم يرسل لي كأدمن، سأجلب التوكن الخاص بي
            tokenRef = FirebaseDatabase.getInstance().getReference("admin_data").child("fcm_token");
        }

        final String finalSenderName = isAdmin ? "ZEEKO ORG" : senderName;

        tokenRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                String targetToken = snapshot.getValue(String.class);
                if (targetToken != null && !targetToken.trim().isEmpty()) {
                    FcmSender.sendChatNotification(targetToken, finalSenderName, messageBody, roomUid);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
            }
        });
    }

    private void updateMetadata(String lastMsg) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        String userName;
        if (isAdmin) {
            userName = chatPartnerName != null ? chatPartnerName : tr("مستخدم", "User");
        } else {
            userName = user.getDisplayName() != null ? user.getDisplayName() : tr("مستخدم", "User");
        }

        metadataRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Integer currentAdmin = snapshot.child("unreadCountAdmin").getValue(Integer.class);
                Integer currentUser = snapshot.child("unreadCountUser").getValue(Integer.class);
                if (currentAdmin == null) currentAdmin = 0;
                if (currentUser == null) currentUser = 0;

                Map<String, Object> metaData = new HashMap<>();
                metaData.put("userName", userName);
                metaData.put("lastMessage", lastMsg);
                metaData.put("lastMessageTime", ServerValue.TIMESTAMP);

                if (isAdmin) {
                    metaData.put("unreadCountAdmin", 0);
                    metaData.put("unreadCountUser", currentUser + 1);
                } else {
                    metaData.put("unreadCountAdmin", currentAdmin + 1);
                    metaData.put("unreadCountUser", 0);
                }

                String partnerImage = isAdmin ? chatPartnerImage : (user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : "");
                if (partnerImage != null && !partnerImage.isEmpty()) {
                    metaData.put("userProfileImage", partnerImage);
                }

                metadataRef.setValue(metaData);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Map<String, Object> metaData = new HashMap<>();
                metaData.put("userName", userName);
                metaData.put("lastMessage", lastMsg);
                metaData.put("lastMessageTime", ServerValue.TIMESTAMP);
                metaData.put("unreadCountAdmin", isAdmin ? 0 : 1);
                metaData.put("unreadCountUser", isAdmin ? 1 : 0);
                metadataRef.setValue(metaData);
            }
        });
    }

    private void resetUnreadCount() {
        if (isAdmin) {
            metadataRef.child("unreadCountAdmin").setValue(0);
        } else {
            metadataRef.child("unreadCountUser").setValue(0);
        }
    }

    private void loadMessagesWithWelcome() {
        chatRef.limitToLast(100).addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                SupportChatMessage msg = snapshot.getValue(SupportChatMessage.class);
                if (msg != null) {
                    msg.messageId = snapshot.getKey();
                    messagesList.add(msg);
                    adapter.notifyItemInserted(messagesList.size() - 1);
                    recyclerChat.smoothScrollToPosition(messagesList.size() - 1);
                }
            }
            @Override public void onChildChanged(DataSnapshot snapshot, String previousChildName) {}
            @Override public void onChildRemoved(DataSnapshot snapshot) {
                String key = snapshot.getKey();
                for (int i = 0; i < messagesList.size(); i++) {
                    if (messagesList.get(i).messageId != null && messagesList.get(i).messageId.equals(key)) {
                        messagesList.remove(i);
                        adapter.notifyItemRemoved(i);
                        break;
                    }
                }
            }
            @Override public void onChildMoved(DataSnapshot snapshot, String previousChildName) {}
            @Override public void onCancelled(DatabaseError error) {}
        });

        chatRef.limitToLast(1).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    sendWelcomeMessage();
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    private void sendWelcomeMessage() {
        String welcomeTextAr = "مرحباً بك\nكيف يمكنني مساعدتك؟";
        String welcomeTextEn = "Welcome\nHow can I help you?";

        String key = chatRef.push().getKey();
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("senderUid", DEV_UID);
        messageData.put("senderName", "ZEEKO ORG");
        messageData.put("messageText", tr(welcomeTextAr, welcomeTextEn));
        messageData.put("timestamp", ServerValue.TIMESTAMP);

        chatRef.child(key).setValue(messageData)
            .addOnSuccessListener(aVoid -> {
                Map<String, Object> metaData = new HashMap<>();
                metaData.put("userName", "ZEEKO ORG");
                metaData.put("lastMessage", tr(welcomeTextAr, welcomeTextEn));
                metaData.put("lastMessageTime", ServerValue.TIMESTAMP);
                metaData.put("unreadCountAdmin", 0);
                metaData.put("unreadCountUser", 1);
                metadataRef.setValue(metaData);
            });
    }

    private void loadAvatarForTopBar(String uid, ImageView imageView) {
        File localFile = new File(getFilesDir(), "avatar_" + uid + ".jpg");
        
        if (localFile.exists() && localFile.length() > 0) {
            Glide.with(this)
                .load(localFile)
                .circleCrop()
                .skipMemoryCache(true)
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                .placeholder(R.drawable.ic_default_user)
                .into(imageView);
        } else {
            downloadAvatarForTopBar(uid, imageView);
        }
    }
    
    private void downloadAvatarForTopBar(String uid, ImageView imageView) {
        FirebaseDatabase.getInstance().getReference("user_avatars")
            .child(uid)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    String base64 = snapshot.getValue(String.class);
                    if (base64 != null && !base64.isEmpty()) {
                        executorService.execute(() -> {
                            try {
                                Bitmap bitmap = BitmapDecodeUtils.decodeBase64Sampled(base64, 256);
                                if (bitmap != null) {
                                    File localFile = new File(getFilesDir(), "avatar_" + uid + ".jpg");
                                    FileOutputStream fos = new FileOutputStream(localFile);
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos);
                                    fos.close();
                                    
                                    runOnUiThread(() -> {
                                        Glide.with(SupportChatActivity.this)
                                            .load(localFile)
                                            .circleCrop()
                                            .skipMemoryCache(true)
                                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                                            .placeholder(R.drawable.ic_default_user)
                                            .into(imageView);
                                    });
                                }
                            } catch (Exception e) {}
                        });
                    }
                }
                @Override
                public void onCancelled(DatabaseError error) {}
            });
    }

    private void applyLanguage() {
        currentLang = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("app_language", "ar");
        Locale locale = new Locale(currentLang);
        Locale.setDefault(locale);
        android.content.res.Configuration config = new android.content.res.Configuration(getResources().getConfiguration());
        config.setLocale(locale);
        
        // 🌟 تثبيت حجم الخط وتجاهل تكبير الهاتف 🌟
        config.fontScale = 1.0f;
        
        config.setLayoutDirection(Locale.forLanguageTag("ar"));
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
    }

    public String tr(String ar, String en) {
        return "en".equals(currentLang) ? en : ar;
    }

    @Override
    protected void onResume() {
        super.onResume();
        resetUnreadCount();
        currentActiveRoom = roomUid; // تسجيل الدخول للمحادثة
        if (yandexAdsHelper != null) yandexAdsHelper.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        currentActiveRoom = ""; // الخروج من المحادثة
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (emojiPopup != null && isEmojiShowing) {
            emojiPopup.dismiss();
            isEmojiShowing = false;
        }
        if (executorService != null) {
            executorService.shutdown();
        }
        if (yandexAdsHelper != null) {
            yandexAdsHelper.onDestroy();
        }
    }

    public static class SupportChatMessage {
        public String senderUid;
        public String senderName;
        public String messageText;
        public long timestamp;
        public String messageId;

        public SupportChatMessage() {}

        public SupportChatMessage(String senderUid, String senderName, String messageText, long timestamp) {
            this.senderUid = senderUid;
            this.senderName = senderName;
            this.messageText = messageText;
            this.timestamp = timestamp;
        }
    }

    private class SupportChatAdapter extends RecyclerView.Adapter<SupportChatAdapter.ViewHolder> {

        private List<SupportChatMessage> messages;
        private FirebaseAuth auth;
        private SimpleDateFormat sdf;
        private String myUid;

        public SupportChatAdapter(List<SupportChatMessage> messages, FirebaseAuth auth) {
            this.messages = messages;
            this.auth = auth;
            this.sdf = new SimpleDateFormat("hh:mm a", new Locale(currentLang));
            FirebaseUser user = auth.getCurrentUser();
            this.myUid = user != null ? user.getUid() : "";
        }

        @Override
        public ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.item_chat_message, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            SupportChatMessage msg = messages.get(position);
            boolean isMine = (myUid != null && myUid.equals(msg.senderUid));
            boolean isDev = DEV_UID.equals(msg.senderUid);

            // 🌟 كود اكتشاف واستخراج الروابط بذكاء 🌟
            String originalText = msg.messageText != null ? msg.messageText : "";
            String extractedUrl = null;
            String displayText = originalText;

            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(https?://\\S+)").matcher(originalText);
            if (matcher.find()) {
                extractedUrl = matcher.group(1);
                // حذف الرابط من النص الذي سيظهر للمستخدم (لن يراه كنص مزعج)
                displayText = originalText.replace(extractedUrl, "").trim();
            }

            // مؤثر زجاجي حركي للزر عند النقر عليه
            View.OnTouchListener btnTouchListener = (v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN: v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start(); break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL: v.animate().scaleX(1f).scaleY(1f).setDuration(150).start(); break;
                }
                return false;
            };

            if (isMine) {
                holder.layoutLeft.setVisibility(View.VISIBLE);
                holder.layoutRight.setVisibility(View.GONE);

                if (isDev) {
                    holder.tvNameLeft.setText("ZEEKO ORG");
                } else {
                    holder.tvNameLeft.setText(msg.senderName != null ? msg.senderName : SupportChatActivity.this.tr("مستخدم", "User"));
                }

                holder.imgVerifiedLeft.setVisibility(isDev ? View.VISIBLE : View.GONE);
                loadAvatarImage(myUid, holder.imgAvatarLeft);

                if (isDev) {
                    holder.bubbleLeft.setBackgroundResource(R.drawable.bg_bubble_dev_left);
                } else {
                    holder.bubbleLeft.setBackgroundResource(R.drawable.bg_bubble_mine);
                }

                holder.tvTextLeft.setText(displayText);
                if (displayText.isEmpty()) {
                    holder.tvTextLeft.setVisibility(View.GONE);
                } else {
                    holder.tvTextLeft.setVisibility(View.VISIBLE);
                }

                // برمجة إظهار الزر إذا اكتشفنا رابطاً
                if (extractedUrl != null) {
                    holder.btnOpenLinkLeft.setVisibility(View.VISIBLE);
                    holder.btnOpenLinkLeft.setText(SupportChatActivity.this.tr("فـتـح", "Open"));
                    holder.btnOpenLinkLeft.setOnTouchListener(btnTouchListener);
                    
                    final String finalUrl = extractedUrl;
                    holder.btnOpenLinkLeft.setOnClickListener(v -> {
                        if (isAdmin) { // إذا كنت أنت المدير افتح الرابط فوراً
                            openUrlInBrowser(finalUrl);
                        } else { // المستخدم العادي يجبر على مشاهدة الإعلان
                            if (yandexAdsHelper != null) {
                                yandexAdsHelper.showAdDialog(finalUrl, pendingUrl -> openUrlInBrowser(pendingUrl));
                            } else {
                                openUrlInBrowser(finalUrl);
                            }
                        }
                    });
                } else {
                    holder.btnOpenLinkLeft.setVisibility(View.GONE);
                }

                if (msg.timestamp > 0) {
                    holder.tvTimeLeft.setText(sdf.format(new Date(msg.timestamp)));
                } else {
                    holder.tvTimeLeft.setText("");
                }
                
                // 🌟 تفعيل النسخ عند النقر (للمطور فقط لمنع سرقة الروابط) 🌟
                if (isAdmin) {
                    View.OnClickListener copyClick = v -> copyMessage(msg.messageText);
                    View.OnLongClickListener copyLongClick = v -> {
                        copyMessage(msg.messageText);
                        return true;
                    };
                    holder.bubbleLeft.setOnClickListener(copyClick);
                    holder.bubbleLeft.setOnLongClickListener(copyLongClick);
                    holder.tvTextLeft.setOnClickListener(copyClick);
                    holder.tvTextLeft.setOnLongClickListener(copyLongClick);
                } else {
                    holder.bubbleLeft.setOnClickListener(null);
                    holder.bubbleLeft.setOnLongClickListener(null);
                    holder.tvTextLeft.setOnClickListener(null);
                    holder.tvTextLeft.setOnLongClickListener(null);
                }

            } else {
                holder.layoutRight.setVisibility(View.VISIBLE);
                holder.layoutLeft.setVisibility(View.GONE);

                if (isDev) {
                    holder.tvNameRight.setText("ZEEKO ORG");
                } else {
                    holder.tvNameRight.setText(msg.senderName != null ? msg.senderName : SupportChatActivity.this.tr("مستخدم", "User"));
                }

                holder.imgVerifiedRight.setVisibility(isDev ? View.VISIBLE : View.GONE);
                loadAvatarImage(msg.senderUid, holder.imgAvatarRight);

                if (isDev) {
                    holder.bubbleRight.setBackgroundResource(R.drawable.bg_bubble_dev_right);
                } else {
                    holder.bubbleRight.setBackgroundResource(R.drawable.bg_bubble_other);
                }

                holder.tvTextRight.setText(displayText);
                if (displayText.isEmpty()) {
                    holder.tvTextRight.setVisibility(View.GONE);
                } else {
                    holder.tvTextRight.setVisibility(View.VISIBLE);
                }

                // برمجة إظهار الزر إذا اكتشفنا رابطاً للطرف الآخر
                if (extractedUrl != null) {
                    holder.btnOpenLinkRight.setVisibility(View.VISIBLE);
                    holder.btnOpenLinkRight.setText(SupportChatActivity.this.tr("فـتـح", "Open"));
                    holder.btnOpenLinkRight.setOnTouchListener(btnTouchListener);
                    
                    final String finalUrl = extractedUrl;
                    holder.btnOpenLinkRight.setOnClickListener(v -> {
                        if (isAdmin) { // إذا كنت أنت المدير افتح الرابط فوراً
                            openUrlInBrowser(finalUrl);
                        } else { // المستخدم العادي يجبر على مشاهدة الإعلان
                            if (yandexAdsHelper != null) {
                                yandexAdsHelper.showAdDialog(finalUrl, pendingUrl -> openUrlInBrowser(pendingUrl));
                            } else {
                                openUrlInBrowser(finalUrl);
                            }
                        }
                    });
                } else {
                    holder.btnOpenLinkRight.setVisibility(View.GONE);
                }

                if (msg.timestamp > 0) {
                    holder.tvTimeRight.setText(sdf.format(new Date(msg.timestamp)));
                } else {
                    holder.tvTimeRight.setText("");
                }
                
                // 🌟 تفعيل النسخ عند النقر (للمطور فقط لمنع سرقة الروابط) 🌟
                if (isAdmin) {
                    View.OnClickListener copyClick = v -> copyMessage(msg.messageText);
                    View.OnLongClickListener copyLongClick = v -> {
                        copyMessage(msg.messageText);
                        return true;
                    };
                    holder.bubbleRight.setOnClickListener(copyClick);
                    holder.bubbleRight.setOnLongClickListener(copyLongClick);
                    holder.tvTextRight.setOnClickListener(copyClick);
                    holder.tvTextRight.setOnLongClickListener(copyLongClick);
                } else {
                    holder.bubbleRight.setOnClickListener(null);
                    holder.bubbleRight.setOnLongClickListener(null);
                    holder.tvTextRight.setOnClickListener(null);
                    holder.tvTextRight.setOnLongClickListener(null);
                }
            }
        }
        
        // 🌟 دالة لنسخ النص كاملاً (شاملاً الرابط الأصلي) إلى الحافظة 🌟
        private void copyMessage(String text) {
            if (!isAdmin) return; // حماية إضافية تمنع النسخ نهائياً لغير المطور
            if (text == null || text.isEmpty()) return;
            ClipboardManager clipboard = (ClipboardManager) SupportChatActivity.this.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("chat_message", text);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(SupportChatActivity.this, SupportChatActivity.this.tr("تم نسخ الرسالة", "Message copied"), Toast.LENGTH_SHORT).show();
            }
        }

        private void loadAvatarImage(String uid, ImageView imageView) {
            imageView.setImageResource(R.drawable.ic_default_user);
            
            File localFile = new File(getFilesDir(), "avatar_" + uid + ".jpg");
            
            if (localFile.exists() && localFile.length() > 0) {
                Glide.with(SupportChatActivity.this)
                    .load(localFile)
                    .circleCrop()
                    .skipMemoryCache(false)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                    .placeholder(R.drawable.ic_default_user)
                    .into(imageView);
            } else {
                FirebaseDatabase.getInstance().getReference("user_avatars")
                    .child(uid)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot snapshot) {
                            String base64 = snapshot.getValue(String.class);
                            if (base64 != null && !base64.isEmpty()) {
                                executorService.execute(() -> {
                                    try {
                                                Bitmap bitmap = BitmapDecodeUtils.decodeBase64Sampled(base64, 256);
                                        if (bitmap != null) {
                                            File file = new File(getFilesDir(), "avatar_" + uid + ".jpg");
                                            FileOutputStream fos = new FileOutputStream(file);
                                            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos);
                                            fos.close();
                                            
                                            runOnUiThread(() -> {
                                                Glide.with(SupportChatActivity.this)
                                                    .load(file)
                                                    .circleCrop()
                                                    .skipMemoryCache(false)
                                                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                                                    .placeholder(R.drawable.ic_default_user)
                                                    .into(imageView);
                                                notifyDataSetChanged();
                                            });
                                        }
                                    } catch (Exception e) {}
                                });
                            }
                        }
                        @Override
                        public void onCancelled(DatabaseError error) {}
                    });
            }
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            LinearLayout layoutLeft, contentLeft, senderInfoLeft, bubbleLeft;
            TextView tvNameLeft, tvTextLeft, tvTimeLeft, btnOpenLinkLeft;
            ImageView imgAvatarLeft, imgVerifiedLeft;

            LinearLayout layoutRight, contentRight, senderInfoRight, bubbleRight;
            TextView tvNameRight, tvTextRight, tvTimeRight, btnOpenLinkRight;
            ImageView imgAvatarRight, imgVerifiedRight;

            public ViewHolder(View itemView) {
                super(itemView);
                layoutLeft = itemView.findViewById(R.id.layoutLeft);
                contentLeft = itemView.findViewById(R.id.contentLeft);
                senderInfoLeft = itemView.findViewById(R.id.senderInfoLeft);
                bubbleLeft = itemView.findViewById(R.id.bubbleLeft);
                tvNameLeft = itemView.findViewById(R.id.tvNameLeft);
                tvTextLeft = itemView.findViewById(R.id.tvTextLeft);
                tvTimeLeft = itemView.findViewById(R.id.tvTimeLeft);
                btnOpenLinkLeft = itemView.findViewById(R.id.btnOpenLinkLeft);
                imgAvatarLeft = itemView.findViewById(R.id.imgAvatarLeft);
                imgVerifiedLeft = itemView.findViewById(R.id.imgVerifiedLeft);

                layoutRight = itemView.findViewById(R.id.layoutRight);
                contentRight = itemView.findViewById(R.id.contentRight);
                senderInfoRight = itemView.findViewById(R.id.senderInfoRight);
                bubbleRight = itemView.findViewById(R.id.bubbleRight);
                tvNameRight = itemView.findViewById(R.id.tvNameRight);
                tvTextRight = itemView.findViewById(R.id.tvTextRight);
                tvTimeRight = itemView.findViewById(R.id.tvTimeRight);
                btnOpenLinkRight = itemView.findViewById(R.id.btnOpenLinkRight);
                imgAvatarRight = itemView.findViewById(R.id.imgAvatarRight);
                imgVerifiedRight = itemView.findViewById(R.id.imgVerifiedRight);
            }
        }
    }

    // 🌟 نافذة تأكيد مسح المستخدم نهائياً 🌟
    private void showDeleteUserDialog() {
        final Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.75f);
        }

        View baseView = LayoutInflater.from(this).inflate(R.layout.dialog_base_glass, null);
        dialog.setContentView(baseView);

        MovingBorderView centerMovingBorder = baseView.findViewById(R.id.centerMovingBorder);
        if (centerMovingBorder != null) {
            try {
                java.lang.reflect.Method setCorner = centerMovingBorder.getClass().getMethod("setCornerRadius", float.class);
                java.lang.reflect.Method setGlow = centerMovingBorder.getClass().getMethod("setGlowColor", String.class);
                java.lang.reflect.Method setMode = centerMovingBorder.getClass().getMethod("setContinuousMode", boolean.class);
                setCorner.invoke(centerMovingBorder, 14f);
                setGlow.invoke(centerMovingBorder, "#FF4C4C"); // توهج أحمر
                setMode.invoke(centerMovingBorder, true);
            } catch (Exception e) {}
        }

        TextView tvTitle = baseView.findViewById(R.id.tvDialogTitle);
        if (tvTitle != null) {
            tvTitle.setVisibility(View.VISIBLE);
            tvTitle.setText(tr("حذف نهائي! ⚠️", "Permanent Delete! ⚠️"));
            tvTitle.setTextColor(Color.parseColor("#FF4C4C"));
            tvTitle.setShadowLayer(4f, 1f, 1f, Color.parseColor("#FF000000"));
        }

        LinearLayout dynamicContainer = baseView.findViewById(R.id.dialogDynamicContainer);
        float density = getResources().getDisplayMetrics().density;

        TextView msg = new TextView(this);
        msg.setText(tr("هل أنت متأكد من حذف هذا المستخدم نهائياً؟\n\nسيتم مسح جميع رسائله، صورته، بياناته من الدعم الفني والمتصدرين للأبد ولا يمكن التراجع!", "Are you sure you want to completely delete this user?\n\nAll messages, profile, and data will be erased forever!"));
        msg.setTextColor(Color.WHITE);
        msg.setTextSize(15);
        msg.setGravity(Gravity.CENTER);
        msg.setLineSpacing(6 * density, 1);
        msg.setShadowLayer(4f, 1f, 1f, Color.parseColor("#FF000000"));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = (int)(20 * density);
        msg.setLayoutParams(p);
        dynamicContainer.addView(msg);

        LinearLayout btnContainer = new LinearLayout(this);
        btnContainer.setOrientation(LinearLayout.VERTICAL);
        btnContainer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        try {
            View btnConfirm = LayoutInflater.from(this).inflate(R.layout.item_dialog_button, btnContainer, false);
            LinearLayout.LayoutParams btnParams1 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            btnParams1.bottomMargin = (int)(8 * density);
            btnConfirm.setLayoutParams(btnParams1);
            TextView tvConfirm = btnConfirm.findViewById(R.id.tvButtonText);
            tvConfirm.setText(tr("نعم، احذفه للأبد", "Yes, Delete Forever"));
            tvConfirm.setTextColor(Color.parseColor("#FF4C4C"));
            btnConfirm.setOnClickListener(v -> {
                dialog.dismiss();
                deleteUserCompletely(roomUid);
            });
            btnContainer.addView(btnConfirm);

            View btnCancel = LayoutInflater.from(this).inflate(R.layout.item_dialog_button, btnContainer, false);
            TextView tvCancel = btnCancel.findViewById(R.id.tvButtonText);
            tvCancel.setText(tr("إلغاء", "Cancel"));
            btnCancel.setOnClickListener(v -> dialog.dismiss());
            btnContainer.addView(btnCancel);
        } catch (Exception e) {}

        dynamicContainer.addView(btnContainer);
        dialog.setCancelable(true);
        dialog.show();
    }

    // 🌟 تنفيذ المسح الشامل من قاعدة البيانات 🌟
    private void deleteUserCompletely(String targetUid) {
        Dialog loading = DialogHelper.showLoadingDialog(this, currentLang, tr("جاري محو بيانات المستخدم...", "Wiping user data..."));
        DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        
        // مسح بيانات المستخدم من كل مكان في التطبيق
        db.child("users").child(targetUid).removeValue();
        db.child("user_avatars").child(targetUid).removeValue();
        db.child("user_avatars_metadata").child(targetUid).removeValue();
        db.child("support_messages").child(targetUid).removeValue();
        db.child("support_metadata").child(targetUid).removeValue();
        db.child("banned_users").child(targetUid).removeValue();
        
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (loading != null && loading.isShowing()) loading.dismiss();
            Toast.makeText(this, tr("تم مسح بيانات المستخدم والمحادثة بنجاح", "User data wiped successfully"), Toast.LENGTH_LONG).show();
            finish(); // إغلاق الشاشة والعودة لقائمة المحادثات
        }, 1500);
    }
}
