package com.simomusic.player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LrcParser {
    private LrcParser() {
    }

    private static final Pattern TAG = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]");
    private static final Pattern OFFSET = Pattern.compile("(?i)^\\[offset:\\s*([+-]?\\d+)\\]");
    private static final Pattern WORD = Pattern.compile("<\\d{1,3}:\\d{1,2}(?:[.:]\\d{1,3})?>");

    public static final class Result {
        public final long[] times;
        public final String[] lines;

        Result(long[] times, String[] lines) {
            this.times = times;
            this.lines = lines;
        }
    }

    private static final class Row {
        final long t;
        final String s;

        Row(long t, String s) {
            this.t = t;
            this.s = s;
        }
    }

    public static Result parse(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        long offset = 0;
        List<Row> rows = new ArrayList<>();
        for (String line : raw.split("\\r?\\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            Matcher om = OFFSET.matcher(line);
            if (om.find()) {
                try {
                    offset = Long.parseLong(om.group(1));
                } catch (NumberFormatException ignored) {
                }
                continue;
            }
            List<Long> ts = new ArrayList<>();
            int pos = 0;
            Matcher m = TAG.matcher(line);
            while (pos < line.length()) {
                while (pos < line.length() && line.charAt(pos) == ' ') pos++;
                if (!m.find(pos) || m.start() != pos) break;
                ts.add(stamp(m));
                pos = m.end();
            }
            if (ts.isEmpty()) continue;
            String text = WORD.matcher(line.substring(pos)).replaceAll("").trim();
            for (long t : ts) rows.add(new Row(Math.max(0, t - offset), text));
        }
        if (rows.isEmpty()) return null;
        Collections.sort(rows, new Comparator<Row>() {
            @Override
            public int compare(Row a, Row b) {
                return Long.compare(a.t, b.t);
            }
        });
        List<Row> out = new ArrayList<>(rows.size());
        for (Row r : rows) {
            if (r.s.isEmpty()) {
                if (out.isEmpty() || out.get(out.size() - 1).s.isEmpty()) continue;
            }
            out.add(r);
        }
        while (!out.isEmpty() && out.get(out.size() - 1).s.isEmpty()) out.remove(out.size() - 1);
        if (out.isEmpty()) return null;
        long[] times = new long[out.size()];
        String[] lines = new String[out.size()];
        for (int i = 0; i < out.size(); i++) {
            times[i] = out.get(i).t;
            lines[i] = out.get(i).s;
        }
        return new Result(times, lines);
    }

    private static long stamp(Matcher m) {
        long min = Long.parseLong(m.group(1));
        long sec = Long.parseLong(m.group(2));
        long ms = 0;
        String f = m.group(3);
        if (f != null) {
            long v = Long.parseLong(f);
            ms = f.length() == 1 ? v * 100 : f.length() == 2 ? v * 10 : v;
        }
        return min * 60000L + sec * 1000L + ms;
    }

    public static String[] plain(String raw) {
        if (raw == null) return new String[0];
        List<String> out = new ArrayList<>();
        for (String line : raw.split("\\r?\\n")) {
            String s = line.trim();
            if (s.isEmpty() && (out.isEmpty() || out.get(out.size() - 1).isEmpty())) continue;
            out.add(s);
        }
        while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) out.remove(out.size() - 1);
        return out.toArray(new String[0]);
    }
}
