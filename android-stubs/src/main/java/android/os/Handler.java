package android.os;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Server-side stub: there is no Android looper here. Plugins schedule
 * UI/player monitor loops through this class — WitAnime's PlayerAccess
 * re-posted itself ~4x/second forever, spawning thousands of threads and
 * flooding the log. A sliding per-caller window bounds any such loop while
 * one-shot delayed deliveries still run.
 */
@android.annotation.Stub
public class Handler {
    private static final int WINDOW_MAX = 32;
    private static final long WINDOW_MS = 600_000L;
    private static final ConcurrentHashMap<String, ConcurrentLinkedDeque<Long>> POSTS = new ConcurrentHashMap<>();

    public Handler() {}

    public Handler(Looper looper) {}

    public boolean post(Runnable r) {
        return postDelayed(r, 0);
    }

    public boolean postDelayed(Runnable r, long delayMillis) {
        if (allow(caller())) {
            new Thread(() -> {
                try { if (delayMillis > 0) Thread.sleep(delayMillis); } catch (InterruptedException ignored) {}
                try { r.run(); } catch (Throwable ignored) {}
            }).start();
        }
        return true;
    }

    public final void removeCallbacks(Runnable r) {}

    public final void removeCallbacksAndMessages(Object token) {}

    private static String caller() {
        for (StackTraceElement e : new Throwable().getStackTrace()) {
            if (!"android.os.Handler".equals(e.getClassName())) return e.getClassName();
        }
        return "unknown";
    }

    private static boolean allow(String who) {
        long now = System.currentTimeMillis();
        ConcurrentLinkedDeque<Long> q = POSTS.computeIfAbsent(who, k -> new ConcurrentLinkedDeque<>());
        synchronized (q) {
            Long head;
            while ((head = q.peekFirst()) != null && now - head > WINDOW_MS) q.pollFirst();
            if (q.size() >= WINDOW_MAX) return false;
            q.addLast(now);
            return true;
        }
    }
}
