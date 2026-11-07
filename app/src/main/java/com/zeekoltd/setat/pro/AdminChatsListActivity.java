package com.zeekoltd.setat.pro;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.*;
import com.bumptech.glide.Glide;
import com.google.firebase.database.*;

import java.text.SimpleDateFormat;
import java.util.*;
public class AdminChatsListActivity extends AppCompatActivity {

    private RecyclerView recyclerAdminChats;
    private TextView tvEmptyAdmin, tvTotalUnread, tvAdminBackText, tvAdminTitle;
    private AdminChatsAdapter adapter;
    private List<SupportRoom> roomsList;
    private DatabaseReference metadataRef;
    private String currentLang = "ar";
    private SimpleDateFormat timeFormat;
    private ViewGroup appRootView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyLanguage();
        EdgeToEdgeHelper.enable(getWindow());
        setContentView(R.layout.activity_admin_chats);
        initializeAdmin();
    }

    private void initializeAdmin() {
        metadataRef = FirebaseDatabase.getInstance().getReference("support_metadata");
        timeFormat = new SimpleDateFormat("hh:mm a", new Locale(currentLang));

        appRootView = findViewById(R.id.mainContentView);
        
        // 🌟 حماية الواجهة لكي لا تتداخل مع شريط إشعارات الهاتف 🌟
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            ViewCompat.setOnApplyWindowInsetsListener(appRootView, (v, insets) -> {
                View blurTopBarAdmin = findViewById(R.id.blurTopBarAdmin);
                float d = getResources().getDisplayMetrics().density;
                
                if (blurTopBarAdmin != null) {
                    ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) blurTopBarAdmin.getLayoutParams();
                    params.topMargin = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()).top + (int)(15 * d);
                    blurTopBarAdmin.setLayoutParams(params);
                }
                return insets;
            });
            ViewCompat.requestApplyInsets(appRootView);
        }

        recyclerAdminChats = findViewById(R.id.recyclerAdminChats);
        tvEmptyAdmin = findViewById(R.id.tvEmptyAdmin);
        tvTotalUnread = findViewById(R.id.tvTotalUnread);
        tvAdminBackText = findViewById(R.id.tvAdminBackText);
        tvAdminTitle = findViewById(R.id.tvAdminTitle);
        LinearLayout btnBack = findViewById(R.id.btnBackFromAdmin);

        tvAdminBackText.setText(tr("رجوع", "Back"));
        tvAdminTitle.setText(tr("الدعم الفني", "Support"));
        tvEmptyAdmin.setText(tr("لا توجد محادثات حالياً", "No conversations yet"));

        roomsList = new ArrayList<>();
        adapter = new AdminChatsAdapter(roomsList);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerAdminChats.setLayoutManager(layoutManager);
        recyclerAdminChats.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());
        
        
        loadChatsList();
    }
    
    // 🌟 تفعيل الزجاج للشريط العلوي 🌟
private void loadChatsList() {
        metadataRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                roomsList.clear();
                int totalUnread = 0;

                for (DataSnapshot child : snapshot.getChildren()) {
                    SupportRoom room = new SupportRoom();
                    room.uid = child.getKey();
                    room.userName = child.child("userName").getValue(String.class);
                    room.userProfileImage = child.child("userProfileImage").getValue(String.class);
                    room.lastMessage = child.child("lastMessage").getValue(String.class);
                    
                    Long lastMsgTime = child.child("lastMessageTime").getValue(Long.class);
                    room.lastMessageTime = lastMsgTime != null ? lastMsgTime : 0;
                    
                    Integer unreadAdmin = child.child("unreadCountAdmin").getValue(Integer.class);
                    room.unreadCountAdmin = unreadAdmin != null ? unreadAdmin : 0;
                    
                    Integer unreadUser = child.child("unreadCountUser").getValue(Integer.class);
                    room.unreadCountUser = unreadUser != null ? unreadUser : 0;

                    totalUnread += room.unreadCountAdmin;

                    if (room.userName != null && room.lastMessage != null) {
                        roomsList.add(room);
                    }
                }

                Collections.sort(roomsList, (a, b) -> Long.compare(b.lastMessageTime, a.lastMessageTime));
                adapter.notifyDataSetChanged();

                if (roomsList.isEmpty()) {
                    tvEmptyAdmin.setVisibility(View.VISIBLE);
                    recyclerAdminChats.setVisibility(View.GONE);
                } else {
                    tvEmptyAdmin.setVisibility(View.GONE);
                    recyclerAdminChats.setVisibility(View.VISIBLE);
                }

                if (totalUnread > 0) {
                    tvTotalUnread.setVisibility(View.VISIBLE);
                    tvTotalUnread.setText(totalUnread + " " + tr("غير مقروء", "unread"));
                } else {
                    tvTotalUnread.setVisibility(View.GONE);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    private class AdminChatsAdapter extends RecyclerView.Adapter<AdminChatsAdapter.ViewHolder> {

        private List<SupportRoom> rooms;

        public AdminChatsAdapter(List<SupportRoom> rooms) {
            this.rooms = rooms;
        }

        @Override
        public ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.item_support_chat, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            SupportRoom room = rooms.get(position);

            holder.tvUserName.setText(room.userName != null ? room.userName : tr("مستخدم", "User"));
            holder.tvLastMessage.setText(room.lastMessage != null ? room.lastMessage : "");

            if (room.lastMessageTime > 0) {
                Date date = new Date(room.lastMessageTime);
                holder.tvTime.setText(timeFormat.format(date));
            } else {
                holder.tvTime.setText("");
            }

            if (room.unreadCountAdmin > 0) {
                holder.tvUnreadBadge.setVisibility(View.VISIBLE);
                holder.tvUnreadBadge.setText(String.valueOf(room.unreadCountAdmin));
                holder.dotUnread.setVisibility(View.VISIBLE);
            } else {
                holder.tvUnreadBadge.setVisibility(View.GONE);
                holder.dotUnread.setVisibility(View.GONE);
            }

            if (room.userProfileImage != null && !room.userProfileImage.isEmpty()) {
                try {
                    byte[] decoded = Base64.decode(room.userProfileImage, Base64.DEFAULT);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                    Glide.with(AdminChatsListActivity.this).load(bitmap).circleCrop().into(holder.imgUserAvatar);
                } catch (Exception e) {
                    holder.imgUserAvatar.setImageResource(R.drawable.ic_default_user);
                    holder.imgUserAvatar.clearColorFilter();
                }
            } else {
                holder.imgUserAvatar.setImageResource(R.drawable.ic_default_user);
                holder.imgUserAvatar.clearColorFilter();
            }

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(AdminChatsListActivity.this, SupportChatActivity.class);
                intent.putExtra("room_uid", room.uid);
                intent.putExtra("is_admin", true);
                intent.putExtra("partner_name", room.userName);
                intent.putExtra("partner_image", room.userProfileImage);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return rooms.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imgUserAvatar;
            View dotUnread;
            TextView tvUserName, tvLastMessage, tvTime, tvUnreadBadge;

            public ViewHolder(View itemView) {
                super(itemView);
                imgUserAvatar = itemView.findViewById(R.id.imgUserAvatar);
                dotUnread = itemView.findViewById(R.id.dotUnread);
                tvUserName = itemView.findViewById(R.id.tvUserName);
                tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
                tvTime = itemView.findViewById(R.id.tvTime);
                tvUnreadBadge = itemView.findViewById(R.id.tvUnreadBadge);
            }
        }
    }

    private void applyLanguage() {
        currentLang = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("app_language", "ar");
        Locale locale = new Locale(currentLang);
        Locale.setDefault(locale);
        android.content.res.Configuration config = new android.content.res.Configuration();
        config.setLocale(locale);
        config.setLayoutDirection(Locale.forLanguageTag("ar"));
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
    }

    public String tr(String ar, String en) {
        return "en".equals(currentLang) ? en : ar;
    }
}