// SPDX-License-Identifier: GPL-3.0-or-later
package org.schabi.newpipe.auth;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import net.slooopipe.auth.api.AuthDiagnostics;
import net.slooopipe.auth.api.YoutubeSessionProvider;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.player.helper.PlayerHolder;
import org.schabi.newpipe.util.InfoCache;

import java.util.concurrent.atomic.AtomicLong;

public final class AuthRuntime {
    private static YoutubeSessionProvider sessions;
    private static final Object CACHE_GATE = new Object();

    private AuthRuntime() { }

    public static void initializeDiagnostics(final Context context) {
        AuthDiagnostics.install(
                BuildConfig.DEBUG
                        ? (failure, line) ->
                                Log.println(failure ? Log.ERROR : Log.INFO, "SloooPipeAuth", line)
                        : null);
        AuthDiagnostics.event(
                "process.start",
                "pid",
                Process.myPid(),
                "sdk",
                Build.VERSION.SDK_INT,
                "target",
                context.getApplicationInfo().targetSdkVersion,
                "version",
                BuildConfig.VERSION_CODE,
                "login_process",
                isLoginProcess(context),
                "schema",
                1);
        if (BuildConfig.DEBUG) {
            final Thread.UncaughtExceptionHandler previous =
                    Thread.getDefaultUncaughtExceptionHandler();
            if (previous != null) {
                Thread.setDefaultUncaughtExceptionHandler(
                        (thread, error) -> {
                            try {
                                AuthDiagnostics.failure(
                                        "process.uncaught", error,
                                        "crashed_thread", thread.getId());
                            } finally {
                                previous.uncaughtException(thread, error);
                            }
                        });
            }
        }
    }

    public static void initialize(final Context context) {
        AuthDiagnostics.event("runtime.initialize_begin");
        sessions = AuthBinding.create(context);
        AuthBinding.installBackend(context, sessions);
        AuthDiagnostics.event(
                "runtime.initialize_end",
                "state",
                sessions.state(),
                "generation",
                sessions.generation());
        final AtomicLong previous = new AtomicLong(sessions.generation());
        sessions.addListener(
                () -> {
                    final long current = sessions.generation();
                    if (previous.getAndSet(current) != current) {
                        AuthDiagnostics.event(
                                "runtime.invalidate_begin",
                                "generation",
                                current,
                                "state",
                                sessions.state());
                        synchronized (CACHE_GATE) {
                            InfoCache.getInstance().clearCache();
                        }
                        org.schabi.newpipe.util.potoken.PoTokenProviderImpl.INSTANCE
                                .invalidateForSessionChange();
                        // Active streams are stopped on credential changes, not only on next fetch.
                        new Handler(Looper.getMainLooper())
                                .post(
                                        () -> {
                                            PlayerHolder.getInstance().stopService();
                                            org.schabi.newpipe.newplayer.NewPlayerRuntime
                                                    .invalidateNetworkSession();
                                            AuthDiagnostics.event(
                                                    "runtime.player_stopped",
                                                    "generation",
                                                    current);
                                        });
                        AuthDiagnostics.event("runtime.invalidate_end", "generation", current);
                    }
                });
    }

    public static long generation() {
        return sessions == null ? 0 : sessions.generation();
    }

    public static void commitIfCurrent(final long expected, final Runnable commit) {
        synchronized (CACHE_GATE) {
            if (generation() != expected) {
                AuthDiagnostics.event(
                        "runtime.stale_cache_write", "expected", expected, "actual", generation());
                throw new IllegalStateException("AUTH_SESSION_CHANGED");
            }
            commit.run();
        }
    }

    public static YoutubeSessionProvider sessions() {
        return sessions;
    }

    public static boolean enabled() {
        return sessions != null && sessions.state() != YoutubeSessionProvider.State.UNAVAILABLE;
    }

    public static void prepareLoginProcess() {
        // Exactly once in Application.onCreate, before any WebView/CookieManager access.
        if (Build.VERSION.SDK_INT >= 28) {
            android.webkit.WebView.setDataDirectorySuffix("youtube_auth");
        }
        AuthDiagnostics.event(
                "process.login_directory_ready", "isolated", Build.VERSION.SDK_INT >= 28);
    }

    public static boolean isLoginProcess(final Context context) {
        String name = null;
        if (Build.VERSION.SDK_INT >= 28) {
            name = Application.getProcessName();
        } else {
            final ActivityManager manager =
                    (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager != null && manager.getRunningAppProcesses() != null) {
                for (final ActivityManager.RunningAppProcessInfo process
                        : manager.getRunningAppProcesses()) {
                    if (process.pid == Process.myPid()) {
                        name = process.processName;
                        break;
                    }
                }
            }
        }
        return name != null && name.endsWith(":youtube_auth");
    }
}
