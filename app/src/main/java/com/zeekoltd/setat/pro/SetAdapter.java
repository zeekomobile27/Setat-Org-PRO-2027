package com.zeekoltd.setat.pro;

import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class SetAdapter extends RecyclerView.Adapter<SetAdapter.ViewHolder> {

    private List<ExclusiveSet> sets;
    private String currentLang;
    private Resources resources;
    private String packageName;
    private OnSetClickListener listener;

    public interface OnSetClickListener {
        void onSetClick(int position, ExclusiveSet set);
    }

    public SetAdapter(List<ExclusiveSet> sets, String currentLang, Resources resources, String packageName, OnSetClickListener listener) {
        this.sets = sets != null ? sets : new ArrayList<>();
        this.currentLang = currentLang;
        this.resources = resources;
        this.packageName = packageName;
        this.listener = listener;
    }

    public void updateList(List<ExclusiveSet> newList) {
        this.sets = newList != null ? newList : new ArrayList<>();
        notifyDataSetChanged();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        float density = resources.getDisplayMetrics().density;
        
        // ✅ حاوية خارجية بعرض كامل لتوسيط الزر
        LinearLayout container = new LinearLayout(parent.getContext());
        container.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        container.setGravity(Gravity.CENTER);
        container.setOrientation(LinearLayout.VERTICAL);
        
        Button button = new Button(parent.getContext());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
            (int)(280 * density),
            (int)(55 * density)
        );
        buttonParams.setMargins(0, 0, 0, (int)(12 * density));
        button.setLayoutParams(buttonParams);
        
        button.setTextColor(Color.parseColor("#C5A059"));
        button.setTypeface(null, Typeface.BOLD);
        button.setBackgroundResource(R.drawable.bg_glass_capsule);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        
        int downloadIconResId = resources.getIdentifier("icon_download_round", "drawable", packageName);
        if (downloadIconResId != 0) {
            Drawable downloadIcon = resources.getDrawable(downloadIconResId);
            downloadIcon.setBounds(0, 0, (int)(24 * density), (int)(24 * density));
            downloadIcon.setTint(Color.parseColor("#C5A059"));
            
            if ("en".equals(currentLang)) {
                button.setCompoundDrawables(downloadIcon, null, null, null);
            } else {
                button.setCompoundDrawables(null, null, downloadIcon, null);
            }
            button.setCompoundDrawablePadding((int)(8 * density));
        }
        
        container.addView(button);

        return new ViewHolder(container, button);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        ExclusiveSet set = sets.get(position);
        Button button = holder.button;
        
        if ("en".equals(currentLang)) {
            button.setText(set.title_en != null ? set.title_en : set.title_ar);
            button.setTextSize(12);
        } else {
            button.setText(set.title_ar);
            button.setTextSize(15);
        }
    }

    @Override
    public int getItemCount() {
        return sets.size();
    }

    // ============================================================
    // ✅ المستمعات داخل ViewHolder - تُنشأ مرة واحدة فقط
    // ============================================================
    class ViewHolder extends RecyclerView.ViewHolder {
        Button button;

        ViewHolder(View itemView, Button button) {
            super(itemView);
            this.button = button;
            
            // ✅ أنيميشن اللمس - يُنشأ مرة واحدة
            button.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start();
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            v.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                            break;
                    }
                    return false;
                }
            });

            // ✅ النقر - يُنشأ مرة واحدة
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    try {
                        v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                                if (listener != null) {
                                    int position = getAdapterPosition();
                                    if (position != RecyclerView.NO_POSITION && position < sets.size()) {
                                        listener.onSetClick(position, sets.get(position));
                                    }
                                }
                            }
                        }).start();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    // ============================================================
    // نموذج البيانات
    // ============================================================
    public static class ExclusiveSet {
        public String set_id;
        public String title_ar;
        public String title_en;
        public String download_url;
        public long timestamp;
        public boolean is_active;

        public ExclusiveSet() {}

        public ExclusiveSet(String set_id, String title_ar, String title_en, String download_url, long timestamp, boolean is_active) {
            this.set_id = set_id;
            this.title_ar = title_ar;
            this.title_en = title_en;
            this.download_url = download_url;
            this.timestamp = timestamp;
            this.is_active = is_active;
        }
    }
}