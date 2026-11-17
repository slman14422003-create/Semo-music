package com.simomusic.player;

import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.SeekBar;

import androidx.recyclerview.widget.RecyclerView;

/**
 * One soft press-scale for everything you can tap. Walks a view tree once and gives every tappable view that has
 * no effect yet the same springy press as the buttons, so no row, chip or icon feels dead.
 */
public final class PressFx {
    private PressFx() {
    }

    public static void deep(View v) {
        if (v == null) return;
        if (v.isClickable() && v.isEnabled() && v.getStateListAnimator() == null
                && !(v instanceof CompoundButton) && !(v instanceof SeekBar) && !(v instanceof EditText)
                && v.getTag(R.id.tag_no_press) == null) {
            Ui.press(v.getContext(), v);
        }
        if (v instanceof ViewGroup && !(v instanceof RecyclerView)) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) deep(g.getChildAt(i));
        }
    }
}
