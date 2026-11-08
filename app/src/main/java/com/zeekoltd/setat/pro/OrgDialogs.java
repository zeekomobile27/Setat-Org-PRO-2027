package com.zeekoltd.setat.pro;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class OrgDialogs {

    public static Dialog createGlassDialog(Activity activity) {
        Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.70f);
        }
        View baseView = LayoutInflater.from(activity).inflate(R.layout.dialog_base_org, null);
        dialog.setContentView(baseView);

        TextView tvTitle = baseView.findViewById(R.id.tvDialogTitle);
        if (tvTitle != null) {
            tvTitle.setVisibility(View.VISIBLE);
            tvTitle.setTextColor(Color.parseColor("#C5A059"));
        }
        return dialog;
    }

    public static EditText createDevField(Activity activity, String hint) {
        float d = activity.getResources().getDisplayMetrics().density;
        EditText et = new EditText(activity);
        et.setHint(hint);
        et.setTextColor(Color.WHITE);
        et.setHintTextColor(Color.GRAY);
        et.setTextSize(14);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (40 * d));
        params.bottomMargin = (int) (8 * d);
        et.setLayoutParams(params);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(8 * d);
        bg.setStroke((int) (1 * d), Color.parseColor("#C5A059"));
        et.setBackground(bg);
        et.setPadding((int) (10 * d), 0, (int) (10 * d), 0);
        return et;
    }

    public static TextView createDevButton(Activity activity, String text, String colorHex) {
        float d = activity.getResources().getDisplayMetrics().density;
        TextView btn = new TextView(activity);
        btn.setText(text);
        btn.setGravity(Gravity.CENTER);
        btn.setTextSize(13);
        btn.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams pc = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (38 * d));
        pc.setMargins(0, (int) (4 * d), 0, (int) (4 * d));
        btn.setLayoutParams(pc);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(8 * d);
        gd.setColor(Color.parseColor(colorHex));
        btn.setBackground(gd);
        return btn;
    }

    // 🚀 نافذة المعلومات الجديدة (ممررة، ثنائية اللغة، 80% من الشاشة)
    public static void showAppInfoDialog(OrgPianoActivity activity) {
        final Dialog dialog = createGlassDialog(activity);
        
        Window window = dialog.getWindow();
        if (window != null) {
            int width = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.80f);
            window.setLayout(width, WindowManager.LayoutParams.MATCH_PARENT);
        }

        LinearLayout dynamicContainer = dialog.findViewById(R.id.dialogDynamicContainer);
        
        // 🚀 تعديل الترويسة لإضافة زر (X) للإغلاق بجوار العنوان
        TextView tvTitle = dialog.findViewById(R.id.tvDialogTitle);
        ViewGroup titleParent = (ViewGroup) tvTitle.getParent();
        tvTitle.setVisibility(View.GONE);
        
        LinearLayout headerLayout = new LinearLayout(activity);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(Gravity.CENTER_VERTICAL);
        headerLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        
        TextView newTitle = new TextView(activity);
        newTitle.setText(activity.tr("معلومات", "Information"));
        newTitle.setTextColor(Color.parseColor("#C5A059"));
        newTitle.setTextSize(18);
        newTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        newTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        
        TextView closeIcon = new TextView(activity);
        closeIcon.setText("✕");
        closeIcon.setTextColor(Color.parseColor("#555555"));
        closeIcon.setTextSize(20);
        closeIcon.setTypeface(null, android.graphics.Typeface.BOLD);
        closeIcon.setPadding(10, 10, 10, 10);
        closeIcon.setOnClickListener(v -> dialog.dismiss());
        
        headerLayout.addView(newTitle);
        headerLayout.addView(closeIcon);
        titleParent.addView(headerLayout, 0);

        ScrollView scrollView = new ScrollView(activity);
        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scrollParams.setMargins(0, (int)(10 * density), 0, (int)(10 * density));
        scrollView.setLayoutParams(scrollParams);
        
        TextView tvInfo = new TextView(activity);
        String arText = "السيتات التي تعمل في هذا الاورج هي السيتات التي يتم تحميلها هنا فقط وليست السيتات التي يتم تحميلها من بقيه اقسام التطبيق ، السيتات التي تعمل في هذا الاورج تعمل بصيغه zip فقط يتم صناعتها من قبل المطور والعازف ZEEKO ORG ، او عازفين اخرين مستقبلا يمكنهم صناعه سيتات خاصه لهذا الاورج .";
        String enText = "The sets that work in this keyboard are exclusively those downloaded from here, not from other app sections. The compatible sets use the zip format only, created by the developer and musician ZEEKO ORG, or other musicians in the future who can create custom sets for this keyboard.";
        
        tvInfo.setText(activity.tr(arText, enText));
        tvInfo.setTextColor(Color.WHITE);
        tvInfo.setTextSize(14);
        tvInfo.setLineSpacing(0, 1.3f);
        tvInfo.setGravity(Gravity.CENTER);
        tvInfo.setPadding((int)(10 * density), (int)(10 * density), (int)(10 * density), (int)(10 * density));
        
        scrollView.addView(tvInfo);
        dynamicContainer.addView(scrollView);

        dialog.show();
    }

    public static void showTempoPopup(OrgPianoActivity activity) {
        final Dialog dialog = createGlassDialog(activity);
        LinearLayout dynamicContainer = dialog.findViewById(R.id.dialogDynamicContainer);
        TextView tvTitle = dialog.findViewById(R.id.tvDialogTitle);
        tvTitle.setText(activity.tr("سرعة الإيقاع (Tempo)", "Rhythm Tempo"));

        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout btnRow = new LinearLayout(activity);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setWeightSum(3f);
        btnRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView btnMinus = new TextView(activity);
        btnMinus.setText("-");
        btnMinus.setGravity(Gravity.CENTER);
        btnMinus.setTextSize(20);
        btnMinus.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, (int) (40 * density), 1.0f);
        p1.setMargins((int) (2 * density), (int) (10 * density), (int) (2 * density), (int) (10 * density));
        btnMinus.setLayoutParams(p1);
        GradientDrawable gdBtn = new GradientDrawable();
        gdBtn.setShape(GradientDrawable.RECTANGLE);
        gdBtn.setCornerRadius(8 * density);
        gdBtn.setColor(Color.parseColor("#4D555555"));
        gdBtn.setStroke((int) (1 * density), Color.parseColor("#80FFFFFF"));
        btnMinus.setBackground(gdBtn);

        TextView tvTempoVal = new TextView(activity);
        tvTempoVal.setText(Math.round(activity.currentTempo * 100) + "%");
        tvTempoVal.setGravity(Gravity.CENTER);
        tvTempoVal.setTextSize(16);
        tvTempoVal.setTextColor(Color.parseColor("#C5A059"));
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, (int) (40 * density), 1.0f);
        p2.setMargins((int) (2 * density), (int) (10 * density), (int) (2 * density), (int) (10 * density));
        tvTempoVal.setLayoutParams(p2);

        TextView btnPlus = new TextView(activity);
        btnPlus.setText("+");
        btnPlus.setGravity(Gravity.CENTER);
        btnPlus.setTextSize(20);
        btnPlus.setTextColor(Color.WHITE);
        btnPlus.setLayoutParams(p1);
        btnPlus.setBackground(gdBtn);

        btnMinus.setOnClickListener(v -> {
            if (activity.currentTempo > 0.50f) {
                activity.currentTempo = Math.round((activity.currentTempo - 0.01f) * 100.0f) / 100.0f;
                tvTempoVal.setText(Math.round(activity.currentTempo * 100) + "%");
                if (activity.audioEngine != null) activity.audioEngine.setRhythmTempo(activity.currentTempo);
                activity.updateDashboardTempo();
            }
        });

        btnPlus.setOnClickListener(v -> {
            if (activity.currentTempo < 2.0f) {
                activity.currentTempo = Math.round((activity.currentTempo + 0.01f) * 100.0f) / 100.0f;
                tvTempoVal.setText(Math.round(activity.currentTempo * 100) + "%");
                if (activity.audioEngine != null) activity.audioEngine.setRhythmTempo(activity.currentTempo);
                activity.updateDashboardTempo();
            }
        });

        btnRow.addView(btnMinus);
        btnRow.addView(tvTempoVal);
        btnRow.addView(btnPlus);
        dynamicContainer.addView(btnRow);

        TextView btnClose = createDevButton(activity, activity.tr("إغلاق", "Close"), "#4D555555");
        btnClose.setOnClickListener(v -> dialog.dismiss());
        dynamicContainer.addView(btnClose);

        dialog.show();
    }

    public static void showVolumePopup(OrgPianoActivity activity) {
        final Dialog dialog = createGlassDialog(activity);
        LinearLayout dynamicContainer = dialog.findViewById(R.id.dialogDynamicContainer);
        TextView tvTitle = dialog.findViewById(R.id.tvDialogTitle);
        tvTitle.setText(activity.tr("التحكم بمستوى الصوت", "Volume Control"));

        float density = activity.getResources().getDisplayMetrics().density;

        TextView tvInstLabel = new TextView(activity);
        tvInstLabel.setText(activity.tr("مستوى صوت الآلة: ", "Instrument Volume: ") + Math.round(activity.currentKeysVolume * 100) + "%");
        tvInstLabel.setGravity(Gravity.CENTER);
        tvInstLabel.setTextSize(14);
        tvInstLabel.setTextColor(Color.parseColor("#C5A059"));
        LinearLayout.LayoutParams pLabelInst = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pLabelInst.setMargins(0, (int) (10 * density), 0, 0);
        tvInstLabel.setLayoutParams(pLabelInst);
        dynamicContainer.addView(tvInstLabel);

        SeekBar sbInst = new SeekBar(activity);
        sbInst.setMax(150);
        sbInst.setProgress(Math.round(activity.currentKeysVolume * 100));
        LinearLayout.LayoutParams pSeek = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pSeek.setMargins((int) (15 * density), (int) (5 * density), (int) (15 * density), (int) (15 * density));
        sbInst.setLayoutParams(pSeek);
        
        try {
            sbInst.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#C5A059")));
            sbInst.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#1DB954")));
        } catch (Exception ignored) {}

        sbInst.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                activity.currentKeysVolume = progress / 100.0f;
                tvInstLabel.setText(activity.tr("مستوى صوت الآلة: ", "Instrument Volume: ") + progress + "%");
                if (activity.audioEngine != null) activity.audioEngine.setKeysVolume(activity.currentKeysVolume);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        dynamicContainer.addView(sbInst);

        TextView tvRhythmLabel = new TextView(activity);
        tvRhythmLabel.setText(activity.tr("مستوى صوت الإيقاع: ", "Rhythm Volume: ") + Math.round(activity.currentRhythmVolume * 100) + "%");
        tvRhythmLabel.setGravity(Gravity.CENTER);
        tvRhythmLabel.setTextSize(14);
        tvRhythmLabel.setTextColor(Color.parseColor("#FFCCCC"));
        tvRhythmLabel.setLayoutParams(pLabelInst);
        dynamicContainer.addView(tvRhythmLabel);

        SeekBar sbRhythm = new SeekBar(activity);
        sbRhythm.setMax(150);
        sbRhythm.setProgress(Math.round(activity.currentRhythmVolume * 100));
        sbRhythm.setLayoutParams(pSeek);
        
        try {
            sbRhythm.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FFCCCC")));
            sbRhythm.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#E53935")));
        } catch (Exception ignored) {}

        sbRhythm.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                activity.currentRhythmVolume = progress / 100.0f;
                tvRhythmLabel.setText(activity.tr("مستوى صوت الإيقاع: ", "Rhythm Volume: ") + progress + "%");
                if (activity.audioEngine != null) activity.audioEngine.setRhythmVolume(activity.currentRhythmVolume);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        dynamicContainer.addView(sbRhythm);

        TextView btnClose = createDevButton(activity, activity.tr("إغلاق", "Close"), "#4D555555");
        btnClose.setOnClickListener(v -> dialog.dismiss());
        dynamicContainer.addView(btnClose);

        dialog.show();
    }

    public static void showTransposePopup(OrgPianoActivity activity) {
        final Dialog dialog = createGlassDialog(activity);
        LinearLayout dynamicContainer = dialog.findViewById(R.id.dialogDynamicContainer);
        TextView tvTitle = dialog.findViewById(R.id.tvDialogTitle);
        tvTitle.setText(activity.tr("الترانسبوس (Transpose)", "Transpose"));

        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout btnRow = new LinearLayout(activity);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setWeightSum(3f);
        btnRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView btnMinus = new TextView(activity);
        btnMinus.setText("-");
        btnMinus.setGravity(Gravity.CENTER);
        btnMinus.setTextSize(20);
        btnMinus.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, (int) (40 * density), 1.0f);
        p1.setMargins((int) (2 * density), (int) (10 * density), (int) (2 * density), (int) (10 * density));
        btnMinus.setLayoutParams(p1);
        GradientDrawable gdBtn = new GradientDrawable();
        gdBtn.setShape(GradientDrawable.RECTANGLE);
        gdBtn.setCornerRadius(8 * density);
        gdBtn.setColor(Color.parseColor("#4D555555"));
        gdBtn.setStroke((int) (1 * density), Color.parseColor("#80FFFFFF"));
        btnMinus.setBackground(gdBtn);

        TextView tvVal = new TextView(activity);
        tvVal.setText((activity.currentTranspose > 0 ? "+" : "") + activity.currentTranspose);
        tvVal.setGravity(Gravity.CENTER);
        tvVal.setTextSize(16);
        tvVal.setTextColor(Color.parseColor("#C5A059"));
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, (int) (40 * density), 1.0f);
        p2.setMargins((int) (2 * density), (int) (10 * density), (int) (2 * density), (int) (10 * density));
        tvVal.setLayoutParams(p2);

        TextView btnPlus = new TextView(activity);
        btnPlus.setText("+");
        btnPlus.setGravity(Gravity.CENTER);
        btnPlus.setTextSize(20);
        btnPlus.setTextColor(Color.WHITE);
        btnPlus.setLayoutParams(p1);
        btnPlus.setBackground(gdBtn);

        btnMinus.setOnClickListener(v -> {
            if (activity.currentTranspose > -12) {
                activity.currentTranspose--;
                tvVal.setText((activity.currentTranspose > 0 ? "+" : "") + activity.currentTranspose);
                activity.updateDashboardTrans();
            }
        });

        btnPlus.setOnClickListener(v -> {
            if (activity.currentTranspose < 12) {
                activity.currentTranspose++;
                tvVal.setText((activity.currentTranspose > 0 ? "+" : "") + activity.currentTranspose);
                activity.updateDashboardTrans();
            }
        });

        btnRow.addView(btnMinus);
        btnRow.addView(tvVal);
        btnRow.addView(btnPlus);
        dynamicContainer.addView(btnRow);

        TextView btnClose = createDevButton(activity, activity.tr("إغلاق", "Close"), "#4D555555");
        btnClose.setOnClickListener(v -> dialog.dismiss());
        dynamicContainer.addView(btnClose);

        dialog.show();
    }

    public static void showOctavePopup(OrgPianoActivity activity) {
        final Dialog dialog = createGlassDialog(activity);
        LinearLayout dynamicContainer = dialog.findViewById(R.id.dialogDynamicContainer);
        TextView tvTitle = dialog.findViewById(R.id.tvDialogTitle);
        tvTitle.setText(activity.tr("طبقة الأوكتاف (Octave)", "Octave Shift"));

        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout btnRow = new LinearLayout(activity);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setWeightSum(3f);
        btnRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView btnMinus = new TextView(activity);
        btnMinus.setText("-");
        btnMinus.setGravity(Gravity.CENTER);
        btnMinus.setTextSize(20);
        btnMinus.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, (int) (40 * density), 1.0f);
        p1.setMargins((int) (2 * density), (int) (10 * density), (int) (2 * density), (int) (10 * density));
        btnMinus.setLayoutParams(p1);
        GradientDrawable gdBtn = new GradientDrawable();
        gdBtn.setShape(GradientDrawable.RECTANGLE);
        gdBtn.setCornerRadius(8 * density);
        gdBtn.setColor(Color.parseColor("#4D555555"));
        gdBtn.setStroke((int) (1 * density), Color.parseColor("#80FFFFFF"));
        btnMinus.setBackground(gdBtn);

        TextView tvVal = new TextView(activity);
        tvVal.setText((activity.currentOctave > 0 ? "+" : "") + activity.currentOctave);
        tvVal.setGravity(Gravity.CENTER);
        tvVal.setTextSize(16);
        tvVal.setTextColor(Color.parseColor("#C5A059"));
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, (int) (40 * density), 1.0f);
        p2.setMargins((int) (2 * density), (int) (10 * density), (int) (2 * density), (int) (10 * density));
        tvVal.setLayoutParams(p2);

        TextView btnPlus = new TextView(activity);
        btnPlus.setText("+");
        btnPlus.setGravity(Gravity.CENTER);
        btnPlus.setTextSize(20);
        btnPlus.setTextColor(Color.WHITE);
        btnPlus.setLayoutParams(p1);
        btnPlus.setBackground(gdBtn);

        btnMinus.setOnClickListener(v -> {
            if (activity.currentOctave > -3) {
                activity.currentOctave--;
                tvVal.setText((activity.currentOctave > 0 ? "+" : "") + activity.currentOctave);
                activity.updateDashboardTrans();
            }
        });

        btnPlus.setOnClickListener(v -> {
            if (activity.currentOctave < 3) {
                activity.currentOctave++;
                tvVal.setText((activity.currentOctave > 0 ? "+" : "") + activity.currentOctave);
                activity.updateDashboardTrans();
            }
        });

        btnRow.addView(btnMinus);
        btnRow.addView(tvVal);
        btnRow.addView(btnPlus);
        dynamicContainer.addView(btnRow);

        TextView btnClose = createDevButton(activity, activity.tr("إغلاق", "Close"), "#4D555555");
        btnClose.setOnClickListener(v -> dialog.dismiss());
        dynamicContainer.addView(btnClose);

        dialog.show();
    }

    public static void showTuningPopup(OrgPianoActivity activity, int noteIndex, TextView noteBtn) {
        final Dialog dialog = createGlassDialog(activity);
        LinearLayout dynamicContainer = dialog.findViewById(R.id.dialogDynamicContainer);
        TextView tvTitle = dialog.findViewById(R.id.tvDialogTitle);
        tvTitle.setText(activity.tr("تشريق نوتة ( " + activity.notesNames[noteIndex] + " )", "Tune Note ( " + activity.notesNames[noteIndex] + " )"));

        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout btnRow = new LinearLayout(activity);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setWeightSum(5f);
        btnRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        String optNormal = activity.tr("طبيعي", "Normal");
        String[] optionsAr = {optNormal, "-50", "-100", "+50", "+100"};
        float[] rates = {1.0f, 0.9715f, 0.9438f, 1.0293f, 1.0594f};

        for (int i = 0; i < optionsAr.length; i++) {
            final int optIndex = i;
            TextView btnOpt = new TextView(activity);
            btnOpt.setText(optionsAr[i]);
            btnOpt.setGravity(Gravity.CENTER);
            btnOpt.setTextSize(13);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, (int) (40 * density), 1.0f);
            p.setMargins((int) (2 * density), (int) (10 * density), (int) (2 * density), (int) (10 * density));
            btnOpt.setLayoutParams(p);

            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.RECTANGLE);
            gd.setCornerRadius(8 * density);

            if (Math.abs(activity.notePitchRates[noteIndex] - rates[i]) < 0.001f) {
                gd.setColor(Color.parseColor("#C5A059"));
                btnOpt.setTextColor(Color.BLACK);
                btnOpt.setTypeface(null, android.graphics.Typeface.NORMAL);
            } else {
                gd.setColor(Color.parseColor("#4D555555"));
                gd.setStroke((int) (1 * density), Color.parseColor("#80FFFFFF"));
                btnOpt.setTextColor(Color.WHITE);
                btnOpt.setTypeface(null, android.graphics.Typeface.NORMAL);
            }
            btnOpt.setBackground(gd);

            btnOpt.setOnClickListener(v -> {
                activity.notePitchRates[noteIndex] = rates[optIndex];
                activity.detunedVisuals[noteIndex] = (optIndex != 0);
                if (activity.pianoView != null) activity.pianoView.setDetunedNotes(activity.detunedVisuals);
                activity.updateNoteBtnVisual(noteBtn, activity.detunedVisuals[noteIndex]);
                dialog.dismiss();
                activity.hideSystemUI();
            });
            btnRow.addView(btnOpt);
        }
        dynamicContainer.addView(btnRow);
        dialog.show();
    }

    public static void showSelectionPopup(OrgPianoActivity activity, boolean isSoundMode) {
        final Dialog dialog = createGlassDialog(activity);
        LinearLayout dynamicContainer = dialog.findViewById(R.id.dialogDynamicContainer);
        TextView tvTitle = dialog.findViewById(R.id.tvDialogTitle);
        tvTitle.setText(isSoundMode ? activity.tr("قائمة الأصوات", "Sounds List") : activity.tr("قائمة الإيقاعات", "Rhythms List"));

        float density = activity.getResources().getDisplayMetrics().density;

        String activeSound = activity.detectSoundPrefix();
        String activeRhythm = activity.detectRhythmPrefix();

        String option1Name = isSoundMode ? activity.getDisplayNameFromJson("sound1", activeSound) : activity.getDisplayNameFromJson("rhythm1", activeRhythm);
        String option2Name = isSoundMode ? activity.getDisplayNameFromJson("sound2", "Sound 2") : activity.getDisplayNameFromJson("rhythm2", "Rhythm 2");
        int activeSelection = isSoundMode ? activity.currentSoundSelection : activity.currentRhythmSelection;

        LinearLayout btnRow = new LinearLayout(activity);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setWeightSum(2f);
        btnRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView btnOpt1 = new TextView(activity);
        btnOpt1.setText(option1Name);
        btnOpt1.setGravity(Gravity.CENTER);
        btnOpt1.setTextSize(15);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, (int) (40 * density), 1.0f);
        p.setMargins((int) (4 * density), (int) (10 * density), (int) (4 * density), (int) (10 * density));
        btnOpt1.setLayoutParams(p);

        GradientDrawable gd1 = new GradientDrawable();
        gd1.setShape(GradientDrawable.RECTANGLE);
        gd1.setCornerRadius(8 * density);
        if (activeSelection == 1) {
            gd1.setColor(Color.parseColor("#C5A059"));
            btnOpt1.setTextColor(Color.BLACK);
        } else {
            gd1.setColor(Color.parseColor("#4D555555"));
            gd1.setStroke((int) (1 * density), Color.parseColor("#80FFFFFF"));
            btnOpt1.setTextColor(Color.WHITE);
        }
        btnOpt1.setBackground(gd1);

        TextView btnOpt2 = new TextView(activity);
        btnOpt2.setText(option2Name);
        btnOpt2.setGravity(Gravity.CENTER);
        btnOpt2.setTextSize(15);
        btnOpt2.setLayoutParams(p);

        GradientDrawable gd2 = new GradientDrawable();
        gd2.setShape(GradientDrawable.RECTANGLE);
        gd2.setCornerRadius(8 * density);
        if (activeSelection == 2) {
            gd2.setColor(Color.parseColor("#C5A059"));
            btnOpt2.setTextColor(Color.BLACK);
        } else {
            gd2.setColor(Color.parseColor("#4D555555"));
            gd2.setStroke((int) (1 * density), Color.parseColor("#80FFFFFF"));
            btnOpt2.setTextColor(Color.WHITE);
        }
        btnOpt2.setBackground(gd2);

        btnOpt1.setOnClickListener(v -> {
            dialog.dismiss();
            if (isSoundMode) {
                activity.currentSoundSelection = 1;
                if (activity.engineReady) {
                    activity.audioEngine.switchSoundBank(0);
                    activity.updateDashboardNames();
                }
            } else {
                activity.currentRhythmSelection = 1;
                if (activity.engineReady) {
                    activity.audioEngine.switchRhythmBank(0);
                    activity.updateDashboardNames();
                }
            }
        });

        btnOpt2.setOnClickListener(v -> {
            dialog.dismiss();
            if (isSoundMode) {
                activity.currentSoundSelection = 2;
                if (activity.engineReady) {
                    activity.audioEngine.switchSoundBank(1);
                    activity.updateDashboardNames();
                }
            } else {
                activity.currentRhythmSelection = 2;
                if (activity.engineReady) {
                    activity.audioEngine.switchRhythmBank(1);
                    activity.updateDashboardNames();
                }
            }
        });

        btnRow.addView(btnOpt1);
        btnRow.addView(btnOpt2);
        dynamicContainer.addView(btnRow);

        TextView btnClose = createDevButton(activity, activity.tr("إغلاق", "Close"), "#4D555555");
        btnClose.setOnClickListener(v -> dialog.dismiss());
        dynamicContainer.addView(btnClose);

        dialog.show();
    }

    public static void showCloudSetsDialog(OrgPianoActivity activity) {
        final Dialog dialog = createGlassDialog(activity);
        
        Window window = dialog.getWindow();
        if (window != null) {
            int width = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.60f);
            window.setLayout(width, WindowManager.LayoutParams.MATCH_PARENT);
        }

        LinearLayout dynamicContainer = dialog.findViewById(R.id.dialogDynamicContainer);
        TextView tvTitle = dialog.findViewById(R.id.tvDialogTitle);
        tvTitle.setText(activity.tr("متجر السيتات الحصرية", "Exclusive Sets Store"));

        ScrollView scrollView = new ScrollView(activity);
        LinearLayout listContainer = new LinearLayout(activity);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(listContainer);

        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (220 * density));
        scrollView.setLayoutParams(scrollParams);
        dynamicContainer.addView(scrollView);

        TextView tvLoading = new TextView(activity);
        tvLoading.setText(activity.tr("جاري جلب السيتات...", "Fetching Sets..."));
        tvLoading.setTextColor(Color.WHITE);
        tvLoading.setGravity(Gravity.CENTER);
        listContainer.addView(tvLoading);

        DatabaseReference setsRef = FirebaseDatabase.getInstance().getReference("GoldenOrg_Sets");
        setsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                listContainer.removeAllViews();
                for (DataSnapshot child : snapshot.getChildren()) {
                    String title = child.child("en".equals(activity.currentLang) ? "title_en" : "title_ar").getValue(String.class);
                    String desc = child.child("en".equals(activity.currentLang) ? "description_en" : "description_ar").getValue(String.class);
                    String url = child.child("download_url").getValue(String.class);

                    if (title == null || title.isEmpty()) title = child.child("title_ar").getValue(String.class);
                    if (desc == null || desc.isEmpty()) desc = child.child("description_ar").getValue(String.class);

                    LinearLayout itemCard = new LinearLayout(activity);
                    itemCard.setOrientation(LinearLayout.VERTICAL);
                    itemCard.setGravity(Gravity.CENTER);
                    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    cp.setMargins(0, 0, 0, (int) (12 * density));
                    itemCard.setLayoutParams(cp);

                    GradientDrawable bg = new GradientDrawable();
                    bg.setShape(GradientDrawable.RECTANGLE);
                    bg.setCornerRadius(8 * density);
                    bg.setColor(Color.parseColor("#4D000000"));
                    bg.setStroke(2, Color.parseColor("#C5A059"));
                    itemCard.setBackground(bg);
                    itemCard.setPadding((int) (12 * density), (int) (12 * density), (int) (12 * density), (int) (12 * density));

                    TextView tvSetTitle = new TextView(activity);
                    tvSetTitle.setText(title);
                    tvSetTitle.setTextColor(Color.WHITE);
                    tvSetTitle.setTextSize(14);
                    tvSetTitle.setTypeface(null, android.graphics.Typeface.BOLD);
                    tvSetTitle.setGravity(Gravity.CENTER);
                    itemCard.addView(tvSetTitle);

                    if (desc != null && !desc.isEmpty()) {
                        TextView tvSetDesc = new TextView(activity);
                        tvSetDesc.setText(desc);
                        tvSetDesc.setTextColor(Color.parseColor("#CCCCCC"));
                        tvSetDesc.setTextSize(11);
                        tvSetDesc.setGravity(Gravity.CENTER);
                        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        descParams.setMargins(0, (int) (4 * density), 0, (int) (12 * density));
                        tvSetDesc.setLayoutParams(descParams);
                        itemCard.addView(tvSetDesc);
                    } else {
                        tvSetTitle.setPadding(0, 0, 0, (int) (12 * density));
                    }

                    TextView btnDownload = new TextView(activity);
                    btnDownload.setText(activity.tr("تحميل وتركيب", "Download & Install"));
                    btnDownload.setTextColor(Color.BLACK);
                    btnDownload.setTextSize(12);
                    btnDownload.setTypeface(null, android.graphics.Typeface.BOLD);
                    btnDownload.setGravity(Gravity.CENTER);
                    btnDownload.setPadding(0, (int) (8 * density), 0, (int) (8 * density));
                    LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    btnDownload.setLayoutParams(btnParams);
                    
                    GradientDrawable bgBtn = new GradientDrawable();
                    bgBtn.setShape(GradientDrawable.RECTANGLE);
                    bgBtn.setCornerRadius(6 * density);
                    bgBtn.setColor(Color.parseColor("#1DB954"));
                    btnDownload.setBackground(bgBtn);

                    final String finalUrl = url;
                    final String finalTitle = title;
                    btnDownload.setOnClickListener(v -> {
                        dialog.dismiss();
                        activity.requestRewardedSetDownload(finalUrl, finalTitle);
                    });
                    itemCard.addView(btnDownload);

                    listContainer.addView(itemCard);
                }
                if (listContainer.getChildCount() == 0) {
                    tvLoading.setText(activity.tr("لا توجد سيتات حالياً.", "No sets available currently."));
                    listContainer.addView(tvLoading);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                tvLoading.setText(activity.tr("خطأ في الاتصال!", "Connection Error!"));
            }
        });

        TextView btnClose = createDevButton(activity, activity.tr("إغلاق المتجر", "Close Store"), "#555555");
        btnClose.setOnClickListener(v -> dialog.dismiss());
        dynamicContainer.addView(btnClose);

        dialog.show();
    }

    public static void showAdminPasswordDialog(OrgPianoActivity activity) {
        final Dialog dialog = createGlassDialog(activity);
        LinearLayout dynamicContainer = dialog.findViewById(R.id.dialogDynamicContainer);
        TextView tvTitle = dialog.findViewById(R.id.tvDialogTitle);
        tvTitle.setText(activity.tr("لوحة المطور - تأكيد الهوية", "Developer Panel - Auth"));

        EditText etPassword = createDevField(activity, activity.tr("أدخل كلمة المرور السرية", "Enter secret password"));
        dynamicContainer.addView(etPassword);

        TextView btnLogin = createDevButton(activity, activity.tr("تسجيل الدخول", "Login"), "#1DB954");
        btnLogin.setOnClickListener(v -> {
            String enteredPass = etPassword.getText().toString().trim();
            if (enteredPass.isEmpty()) return;

            tvTitle.setText(activity.tr("جاري التحقق...", "Verifying..."));
            DatabaseReference passRef = FirebaseDatabase.getInstance().getReference("GoldenOrg_Config/admin_password");
            passRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    String serverPass = snapshot.getValue(String.class);
                    if (enteredPass.equals(serverPass)) {
                        dialog.dismiss();
                        showDeveloperPanel(activity);
                    } else {
                        tvTitle.setText(activity.tr("كلمة المرور خاطئة!", "Incorrect password!"));
                        tvTitle.setTextColor(Color.RED);
                    }
                }
                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(activity, activity.tr("خطأ في الاتصال", "Connection Error"), Toast.LENGTH_SHORT).show();
                }
            });
        });
        dynamicContainer.addView(btnLogin);

        TextView btnClose = createDevButton(activity, activity.tr("إلغاء", "Cancel"), "#555555");
        btnClose.setOnClickListener(v -> dialog.dismiss());
        dynamicContainer.addView(btnClose);

        dialog.show();
    }

    public static void showDeveloperPanel(OrgPianoActivity activity) {
        final Dialog dialog = createGlassDialog(activity);
        LinearLayout dynamicContainer = dialog.findViewById(R.id.dialogDynamicContainer);
        TextView tvTitle = dialog.findViewById(R.id.tvDialogTitle);
        tvTitle.setText(activity.tr("نشر سيت جديد (Golden Org 2027)", "Publish New Set (Golden Org 2027)"));

        ScrollView scrollView = new ScrollView(activity);
        LinearLayout innerLayout = new LinearLayout(activity);
        innerLayout.setOrientation(LinearLayout.VERTICAL);

        EditText etTitleAr = createDevField(activity, activity.tr("عنوان السيت (عربي)", "Set Title (Arabic)"));
        EditText etTitleEn = createDevField(activity, activity.tr("عنوان السيت (إنجليزي)", "Set Title (English)"));
        EditText etDescAr = createDevField(activity, activity.tr("وصف السيت (عربي)", "Set Description (Arabic)"));
        EditText etDescEn = createDevField(activity, activity.tr("وصف السيت (إنجليزي)", "Set Description (English)"));
        EditText etUrl = createDevField(activity, activity.tr("رابط التحميل المباشر (.zip)", "Direct Download URL (.zip)"));

        innerLayout.addView(etTitleAr);
        innerLayout.addView(etTitleEn);
        innerLayout.addView(etDescAr);
        innerLayout.addView(etDescEn);
        innerLayout.addView(etUrl);

        TextView btnPublish = createDevButton(activity, activity.tr("نشر السيت للجميع", "Publish Set for All"), "#C5A059");
        btnPublish.setOnClickListener(v -> {
            String titleAr = etTitleAr.getText().toString().trim();
            String titleEn = etTitleEn.getText().toString().trim();
            String descAr = etDescAr.getText().toString().trim();
            String descEn = etDescEn.getText().toString().trim();
            String url = etUrl.getText().toString().trim();

            if (titleAr.isEmpty() || url.isEmpty()) {
                Toast.makeText(activity, activity.tr("يرجى تعبئة العناوين والرابط", "Please fill titles and url"), Toast.LENGTH_SHORT).show();
                return;
            }

            DatabaseReference setsRef = FirebaseDatabase.getInstance().getReference("GoldenOrg_Sets");
            String key = setsRef.push().getKey();

            java.util.HashMap<String, Object> newSet = new java.util.HashMap<>();
            newSet.put("title_ar", titleAr);
            newSet.put("title_en", titleEn);
            newSet.put("description_ar", descAr);
            newSet.put("description_en", descEn);
            newSet.put("download_url", url);
            newSet.put("timestamp", System.currentTimeMillis());

            setsRef.child(key).setValue(newSet).addOnSuccessListener(a -> {
                Toast.makeText(activity, activity.tr("تم نشر السيت بنجاح!", "Set Published Successfully!"), Toast.LENGTH_LONG).show();
                dialog.dismiss();
            });
        });
        innerLayout.addView(btnPublish);

        TextView btnClose = createDevButton(activity, activity.tr("إغلاق", "Close"), "#555555");
        btnClose.setOnClickListener(v -> dialog.dismiss());
        innerLayout.addView(btnClose);
        
        scrollView.addView(innerLayout);
        dynamicContainer.addView(scrollView);

        dialog.show();
    }
}
