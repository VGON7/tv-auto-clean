package com.tvautoclean.guard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One cleaning event shown on the home screen. */
public final class CleanRecord {
    public final long time;
    public final long beforeMb;
    public final long afterMb;
    public final int count;
    public final List<String> apps;

    public CleanRecord(long time, long beforeMb, long afterMb, int count, List<String> apps) {
        this.time = time;
        this.beforeMb = beforeMb;
        this.afterMb = afterMb;
        this.count = count;
        this.apps = apps == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(apps));
    }

    public int savedMb() {
        long delta = afterMb - beforeMb;
        if (delta <= 0) {
            return 0;
        }
        if (delta > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) delta;
    }
}
