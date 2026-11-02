package com.simomusic.player;

import java.util.ArrayList;
import java.util.List;

/**
 * Matches what a speech engine "heard" in the song against the known lyric lines and
 * returns anchors (line index -> time) that LyricsRepo.build() spreads over the rest.
 * Tolerant by design: singing recognition is noisy, so only some words need to match.
 */
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

    private static List<String> tokens(String s) {
        List<String> out = new ArrayList<>();
        String f = Norm.fold(s)
                .replace('\u0623', '\u0627').replace('\u0625', '\u0627').replace('\u0622', '\u0627');
        for (String p : f.split("[^\\p{L}\\p{N}]+")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    private static int lev(String a, String b, int cap) {
        if (Math.abs(a.length() - b.length()) > cap) return cap + 1;
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int c = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + c);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    private static boolean same(String a, String b) {
        if (a.equals(b)) return true;
        int min = Math.min(a.length(), b.length());
        if (min < 4) return false;
        return lev(a, b, 2) <= (min >= 7 ? 2 : 1);
    }

    /** Returns null when the match is too weak to trust. */
    public static LyricsRepo.Anchors align(String[] lines, List<Word> heard) {
        if (lines == null || heard == null) return null;
        List<String> lt = new ArrayList<>();
        List<Integer> ll = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            for (String w : tokens(lines[i])) {
                lt.add(w);
                ll.add(i);
            }
        }
        List<String> ht = new ArrayList<>();
        List<Long> hs = new ArrayList<>();
        for (Word w : heard) {
            for (String t : tokens(w.text)) {
                ht.add(t);
                hs.add(w.startMs);
            }
        }
        int n = lt.size(), m = ht.size();
        if (n < 8 || m < 5 || (long) n * m > 4_000_000L) return null;

        short[][] dp = new short[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                dp[i][j] = same(lt.get(i), ht.get(j)) ? (short) (dp[i + 1][j + 1] + 1)
                        : (dp[i + 1][j] >= dp[i][j + 1] ? dp[i + 1][j] : dp[i][j + 1]);
            }
        }
        if (dp[0][0] < 5 || dp[0][0] * 5 < n) return null;

        long[] first = new long[lines.length];
        java.util.Arrays.fill(first, -1);
        int i = 0, j = 0;
        while (i < n && j < m) {
            if (same(lt.get(i), ht.get(j)) && dp[i][j] == dp[i + 1][j + 1] + 1) {
                int li = ll.get(i);
                if (first[li] < 0) first[li] = hs.get(j);
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                i++;
            } else {
                j++;
            }
        }
        List<int[]> idx = new ArrayList<>();
        List<Long> pos = new ArrayList<>();
        long last = -1;
        for (int li = 0; li < first.length; li++) {
            if (first[li] >= 0 && first[li] > last) {
                idx.add(new int[]{li});
                pos.add(first[li]);
                last = first[li];
            }
        }
        if (idx.size() < 3) return null;
        int[] ix = new int[idx.size()];
        long[] ps = new long[idx.size()];
        for (int k = 0; k < ix.length; k++) {
            ix[k] = idx.get(k)[0];
            ps[k] = pos.get(k);
        }
        return new LyricsRepo.Anchors(ix, ps, 1.0);
    }
}
