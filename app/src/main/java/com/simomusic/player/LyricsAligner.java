package com.simomusic.player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class LyricsAligner {
    private LyricsAligner() {
    }

    public static final class Word {
        public final String text;
        public final long startMs;

        public Word(String text, long startMs) {
            this.text = text;
            this.startMs = startMs;
        }
    }

    private static String clean(String s) {
        String f = Norm.fold(s)
                .replace("'", "").replace("\u2019", "").replace("\u02BC", "")
                .replace('\u0623', '\u0627').replace('\u0625', '\u0627').replace('\u0622', '\u0627').replace('\u0671', '\u0627')
                .replace('\u0624', '\u0648').replace('\u0626', '\u064A').replace('\u06CC', '\u064A').replace('\u06A9', '\u0643');
        return f;
    }

    private static String stem(String t) {
        if (t.length() > 4 && t.startsWith("\u0627\u0644")) return t.substring(2);
        return t;
    }

    private static List<String> tokens(String s) {
        List<String> out = new ArrayList<>();
        for (String p : clean(s).split("[^\\p{L}\\p{N}]+")) {
            if (!p.isEmpty()) out.add(stem(p));
        }
        return out;
    }

    private static int lev(String a, String b, int cap) {
        if (Math.abs(a.length() - b.length()) > cap) return cap + 1;
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            int rowMin = cur[0];
            for (int j = 1; j <= b.length(); j++) {
                int c = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + c);
                if (cur[j] < rowMin) rowMin = cur[j];
            }
            if (rowMin > cap) return cap + 1;
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    private static boolean same(String a, String b) {
        if (a.equals(b)) return a.length() >= 2 || !a.isEmpty() && Character.isDigit(a.charAt(0));
        int min = Math.min(a.length(), b.length());
        if (min < 4) return false;
        return lev(a, b, 2) <= (min >= 7 ? 2 : 1);
    }

    private static int weight(String t) {
        int n = t.length();
        return n >= 5 ? 3 : n >= 3 ? 2 : 1;
    }

    public static LyricsRepo.Anchors align(String[] lines, List<Word> heard) {
        if (lines == null || heard == null) return null;
        List<String> lt = new ArrayList<>();
        List<Integer> ll = new ArrayList<>();
        List<Integer> lc = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            int chars = 0;
            for (String w : tokens(lines[i])) {
                lt.add(w);
                ll.add(i);
                lc.add(chars);
                chars += w.length() + 1;
            }
        }
        List<String> ht = new ArrayList<>();
        List<Long> hs = new ArrayList<>();
        long prevStart = Long.MIN_VALUE;
        String prevTok = "";
        for (Word w : heard) {
            for (String t : tokens(w.text)) {
                if (w.startMs == prevStart && t.equals(prevTok)) continue;
                prevStart = w.startMs;
                prevTok = t;
                ht.add(t);
                hs.add(w.startMs);
            }
        }
        int n = lt.size(), m = ht.size();
        if (n < 8 || m < 5 || (long) n * m > 6_000_000L) return null;

        int totalW = 0;
        for (String t : lt) totalW += weight(t);

        short[][] dp = new short[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            String a = lt.get(i);
            int wa = weight(a);
            for (int j = m - 1; j >= 0; j--) {
                int best = Math.max(dp[i + 1][j], dp[i][j + 1]);
                if (same(a, ht.get(j))) {
                    int v = dp[i + 1][j + 1] + wa;
                    if (v > best) best = v;
                }
                dp[i][j] = (short) best;
            }
        }
        if (dp[0][0] < 8 || dp[0][0] * 5 < totalW) return null;

        List<int[]> hit = new ArrayList<>();
        List<Long> hitT = new ArrayList<>();
        int i = 0, j = 0;
        while (i < n && j < m) {
            int wa = weight(lt.get(i));
            if (same(lt.get(i), ht.get(j)) && dp[i][j] == dp[i + 1][j + 1] + wa) {
                hit.add(new int[]{ll.get(i), lc.get(i)});
                hitT.add(hs.get(j));
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                i++;
            } else {
                j++;
            }
        }
        if (hit.size() < 4) return null;

        List<Double> rates = new ArrayList<>();
        for (int k = 1; k < hit.size(); k++) {
            if (hit.get(k)[0] != hit.get(k - 1)[0]) continue;
            int dc = hit.get(k)[1] - hit.get(k - 1)[1];
            long dt = hitT.get(k) - hitT.get(k - 1);
            if (dc >= 3 && dt > 0) {
                double r = (double) dt / dc;
                if (r >= 25 && r <= 220) rates.add(r);
            }
        }
        double msPerChar = 70;
        if (rates.size() >= 3) {
            java.util.Collections.sort(rates);
            msPerChar = rates.get(rates.size() / 2);
        }

        long[] start = new long[lines.length];
        Arrays.fill(start, -1);
        for (int k = 0; k < hit.size(); k++) {
            int li = hit.get(k)[0];
            if (start[li] >= 0) continue;
            long est = hitT.get(k) - Math.min(4000L, Math.round(hit.get(k)[1] * msPerChar));
            start[li] = Math.max(0, est);
        }

        List<Integer> cand = new ArrayList<>();
        for (int li = 0; li < start.length; li++) if (start[li] >= 0) cand.add(li);
        int k = cand.size();
        if (k < 3) return null;
        int[] len = new int[k], from = new int[k];
        int bestEnd = 0;
        for (int a = 0; a < k; a++) {
            len[a] = 1;
            from[a] = -1;
            for (int b = 0; b < a; b++) {
                if (start[cand.get(a)] >= start[cand.get(b)] + 250 && len[b] + 1 > len[a]) {
                    len[a] = len[b] + 1;
                    from[a] = b;
                }
            }
            if (len[a] >= len[bestEnd]) bestEnd = a;
        }
        if (len[bestEnd] < 3 || len[bestEnd] * 8 < lines.length) return null;
        int[] ix = new int[len[bestEnd]];
        long[] ps = new long[len[bestEnd]];
        int c = len[bestEnd] - 1;
        for (int a = bestEnd; a >= 0; a = from[a]) {
            ix[c] = cand.get(a);
            ps[c] = start[cand.get(a)];
            c--;
        }
        return new LyricsRepo.Anchors(ix, ps, 1.0);
    }
}
