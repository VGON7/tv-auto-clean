package com.tvautoclean.guard;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * Tiny JSON-lines log. It is rewritten in place, capped, and filtered to today's
 * records. No database or third-party library is needed.
 */
public final class CleanLogStore {
    private static final String TAG = "TvCleanLog";
    private static final String FILE_NAME = "clean_log.jsonl";
    private static final int MAX_RECORDS = 300;

    private CleanLogStore() {
    }

    public static synchronized void add(Context context, CleanRecord record) {
        long start = startOfToday();
        List<CleanRecord> records = new ArrayList<CleanRecord>();
        for (CleanRecord existing : readAll(context)) {
            if (existing.time >= start) {
                records.add(existing);
            }
        }
        records.add(0, record);
        if (records.size() > MAX_RECORDS) {
            records = new ArrayList<CleanRecord>(records.subList(0, MAX_RECORDS));
        }
        writeAll(context, records);
    }

    /** Newest first, only entries from the current local calendar day. */
    public static synchronized List<CleanRecord> readToday(Context context) {
        long start = startOfToday();
        List<CleanRecord> result = new ArrayList<CleanRecord>();
        for (CleanRecord record : readAll(context)) {
            if (record.time >= start) {
                result.add(record);
            }
        }
        return result;
    }

    /** Removes yesterday's records from disk. Called by the service periodically. */
    public static synchronized void prune(Context context) {
        long start = startOfToday();
        List<CleanRecord> all = readAll(context);
        List<CleanRecord> today = new ArrayList<CleanRecord>();
        for (CleanRecord record : all) {
            if (record.time >= start) {
                today.add(record);
            }
        }
        if (today.size() != all.size()) {
            writeAll(context, today);
        }
    }

    private static List<CleanRecord> readAll(Context context) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.exists()) {
            return new ArrayList<CleanRecord>();
        }
        List<CleanRecord> records = new ArrayList<CleanRecord>();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() == 0) {
                    continue;
                }
                try {
                    JSONObject obj = new JSONObject(line);
                    long time = obj.optLong("ts", 0L);
                    long before = obj.optLong("before", 0L);
                    long after = obj.optLong("after", 0L);
                    int count = obj.optInt("count", 0);
                    JSONArray array = obj.optJSONArray("apps");
                    List<String> apps = new ArrayList<String>();
                    if (array != null) {
                        for (int i = 0; i < array.length(); i++) {
                            String name = array.optString(i, "");
                            if (name.length() > 0) {
                                apps.add(name);
                            }
                        }
                    }
                    if (time > 0L) {
                        records.add(new CleanRecord(time, before, after, count, apps));
                    }
                } catch (Throwable ignored) {
                    // A damaged line should not hide the rest of the log.
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Unable to read clean log", t);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable ignored) {
                }
            }
        }
        Collections.sort(records, new java.util.Comparator<CleanRecord>() {
            @Override
            public int compare(CleanRecord a, CleanRecord b) {
                return Long.compare(b.time, a.time);
            }
        });
        return records;
    }

    private static void writeAll(Context context, List<CleanRecord> records) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        File temp = new File(context.getFilesDir(), FILE_NAME + ".tmp");
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(temp), "UTF-8"));
            for (CleanRecord record : records) {
                JSONObject obj = new JSONObject();
                obj.put("ts", record.time);
                obj.put("before", record.beforeMb);
                obj.put("after", record.afterMb);
                obj.put("count", record.count);
                JSONArray array = new JSONArray();
                for (String app : record.apps) {
                    array.put(app == null ? "" : app);
                }
                obj.put("apps", array);
                writer.write(obj.toString());
                writer.newLine();
            }
            writer.flush();
            writer.close();
            writer = null;
            if (file.exists() && !file.delete()) {
                Log.w(TAG, "Unable to replace old log file");
            }
            if (!temp.renameTo(file)) {
                Log.w(TAG, "Unable to rename new log file");
            }
        } catch (Throwable t) {
            Log.w(TAG, "Unable to write clean log", t);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public static long startOfToday() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
}
