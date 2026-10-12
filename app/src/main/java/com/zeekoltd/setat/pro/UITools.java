package com.zeekoltd.setat.pro;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class UITools {

    public static void setupGlassButton(View view, float density) {
        if (view == null) return;
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(density * 15);
        gd.setColor(Color.parseColor("#4D555555"));
        gd.setStroke(2, Color.parseColor("#80FFFFFF"));
        view.setBackground(gd);
        if (view instanceof TextView) {
            ((TextView) view).setShadowLayer(5, 1, 1, Color.BLACK);
        }
    }

    public static EditText createDevField(Context context, String hint, float density) {
        EditText et = new EditText(context);
        et.setHint(hint);
        et.setTextColor(Color.WHITE);
        et.setHintTextColor(Color.GRAY);
        et.setTextSize(14);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int)(40 * density));
        params.bottomMargin = (int)(8 * density);
        et.setLayoutParams(params);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(8 * density);
        bg.setStroke((int)(1 * density), Color.parseColor("#C5A059"));
        et.setBackground(bg);
        et.setPadding((int)(10 * density), 0, (int)(10 * density), 0);
        return et;
    }

    public static TextView createDevButton(Context context, String text, String colorHex, float density) {
        TextView btn = new TextView(context);
        btn.setText(text);
        btn.setGravity(Gravity.CENTER);
        btn.setTextSize(13);
        btn.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams pc = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int)(38 * density));
        pc.setMargins(0, (int)(4 * density), 0, (int)(4 * density));
        btn.setLayoutParams(pc);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(8 * density);
        gd.setColor(Color.parseColor(colorHex));
        btn.setBackground(gd);
        return btn;
    }
}
