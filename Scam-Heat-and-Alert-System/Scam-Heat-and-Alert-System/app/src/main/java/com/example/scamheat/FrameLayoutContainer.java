package com.example.scamheat;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

public class FrameLayoutContainer extends FrameLayout {

    public FrameLayoutContainer(Context context) {
        super(context);
    }

    @Override
    public void addView(View child) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT);
        super.addView(child, params);
    }

    public void addCenteredView(View child) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
                Gravity.CENTER);
        super.addView(child, params);
    }
}
