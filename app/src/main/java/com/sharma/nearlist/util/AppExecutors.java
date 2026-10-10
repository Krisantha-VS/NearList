package com.sharma.nearlist.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AppExecutors {
    private static final ExecutorService DISK_IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    public static ExecutorService diskIO() { return DISK_IO; }
    public static void main(Runnable r) { MAIN.post(r); }
}
