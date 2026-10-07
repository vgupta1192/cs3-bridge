package android.provider;

import android.content.ContentResolver;

/** Desktop stub of android.provider.Settings: nothing exists headless. */
public class Settings {
    public static class Secure {
        public static String getString(ContentResolver cr, String name) {
            return null;
        }

        public static int getInt(ContentResolver cr, String name) {
            return 0;
        }
    }

    public static class Global {
        public static String getString(ContentResolver cr, String name) {
            return null;
        }
    }

    public static class System {
        public static String getString(ContentResolver cr, String name) {
            return null;
        }
    }
}
