package com.simomusic.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

public final class SectionDecoration extends RecyclerView.ItemDecoration {
    public interface Source {
        List<Track> tracks();

        int mode();

        int offset();
    }

    private final Context ctx;
    private final Source src;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int height, inset;

    public SectionDecoration(Context c, Source s) {
        ctx = c;
        src = s;
        height = Ui.dp(c, 38);
        inset = Ui.dp(c, 28);
        paint.setTypeface(Ui.medium());
        paint.setTextSize(Ui.dp(c, 13));
        paint.setLetterSpacing(0.04f);
    }

    public static boolean grouped(int mode) {
        return mode == 0 || mode == 1 || mode == 4;
    }

    public static String letter(String key) {
        if (key == null || key.isEmpty()) return "#";
        int cp = key.codePointAt(0);
        if (!Character.isLetter(cp)) return "#";
        return new String(Character.toChars(cp)).toUpperCase(Locale.ROOT);
    }

    public static String scrollLabel(Track t, int mode, int index) {
        switch (mode) {
            case 0:
                return letter(t.sortTitle);
            case 1:
                return letter(t.sortArtist);
            case 4:
                return letter(t.sortAlbum);
            default:
                return String.valueOf(index + 1);
        }
    }

    private static String key(Track t, int mode) {
        switch (mode) {
            case 0:
                return letter(t.sortTitle);
            case 1:
                return t.sortArtist;
            default:
                return t.sortAlbum;
        }
    }

    private String head(Track t, int mode) {
        switch (mode) {
            case 0:
                return letter(t.sortTitle);
            case 1:
                return Fmt.artist(ctx, t.artist);
            default:
                return t.album == null || t.album.trim().isEmpty() ? ctx.getString(R.string.unknown_album) : t.album;
        }
    }

    private int index(RecyclerView parent, View v) {
        int pos = parent.getChildAdapterPosition(v);
        if (pos == RecyclerView.NO_POSITION) return -1;
        int i = pos - src.offset();
        List<Track> l = src.tracks();
        return i < 0 || i >= l.size() ? -1 : i;
    }

    private boolean first(int i, int mode) {
        List<Track> l = src.tracks();
        return i == 0 || !key(l.get(i - 1), mode).equals(key(l.get(i), mode));
    }

    @Override
    public void getItemOffsets(@NonNull Rect out, @NonNull View v, @NonNull RecyclerView parent, @NonNull RecyclerView.State s) {
        int mode = src.mode();
        if (!grouped(mode)) return;
        int i = index(parent, v);
        if (i >= 0 && first(i, mode)) out.top = height;
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State s) {
        int mode = src.mode();
        if (!grouped(mode)) return;
        boolean rtl = parent.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        paint.setTextAlign(rtl ? Paint.Align.RIGHT : Paint.Align.LEFT);
        paint.setColor(Ui.color(ctx, R.color.accent_text));
        float x = rtl ? parent.getWidth() - inset : inset;
        Paint.FontMetrics fm = paint.getFontMetrics();
        List<Track> l = src.tracks();
        for (int k = 0; k < parent.getChildCount(); k++) {
            View v = parent.getChildAt(k);
            int i = index(parent, v);
            if (i < 0 || !first(i, mode)) continue;
            paint.setAlpha(Math.round(v.getAlpha() * 255f));
            float cy = v.getTop() + v.getTranslationY() - height / 2f + Ui.dp(ctx, 3);
            c.drawText(head(l.get(i), mode), x, cy - (fm.ascent + fm.descent) / 2f, paint);
        }
    }
}
