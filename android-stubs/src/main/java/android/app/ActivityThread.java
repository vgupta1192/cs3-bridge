package android.app;

/**
 * Desktop stub of the hidden android.app.ActivityThread. Some plugins (WitAnime's
 * PlayerAccess monitor loop) reflectively call currentActivityThread() every second
 * from a Handler.postDelayed runnable; without this class the reflection threw a
 * full stack trace each tick and flooded the docker logs fast enough to rotate
 * away hours of history. Returning null mirrors "no foreground activity", which
 * is the truthful answer for a headless server.
 */
@android.annotation.Stub
public class ActivityThread {
    public static ActivityThread currentActivityThread() {
        return null;
    }
}
