package com.simomusic.player;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

public final class Norm {
    private Norm() {
    }

    public static String fold(String s) {
        if (s == null || s.isEmpty()) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        StringBuilder b = new StringBuilder(n.length());
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (Character.getType(c) == Character.NON_SPACING_MARK || c == '\u0640') continue;
            if (c >= '\u0660' && c <= '\u0669') c = (char) ('0' + (c - '\u0660'));
            else if (c >= '\u06F0' && c <= '\u06F9') c = (char) ('0' + (c - '\u06F0'));
            else if (c == '\u0649') c = '\u064A';
            else if (c == '\u0629') c = '\u0647';
            else if (c == '_' || c == '-' || c == '.') c = ' ';
            b.append(Character.toLowerCase(c));
        }
        return b.toString();
    }

    public static String[] tokens(String q) {
        String f = fold(q).trim();
        if (f.isEmpty()) return new String[0];
        List<String> out = new ArrayList<>();
        for (String p : f.split("\\s+")) if (!p.isEmpty()) out.add(p);
        return out.toArray(new String[0]);
    }

    public static boolean matches(String key, String[] tokens) {
        for (String t : tokens) if (!key.contains(t)) return false;
        return true;
    }

    public static String sortKey(String s) {
        String f = fold(s).trim();
        int i = 0;
        while (i < f.length() && !Character.isLetterOrDigit(f.charAt(i))) i++;
        f = f.substring(i);
        if (f.startsWith("the ")) f = f.substring(4);
        return f;
    }
}
