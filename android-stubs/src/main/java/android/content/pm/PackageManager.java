package android.content.pm;

import android.content.Intent;

@android.annotation.Implemented
public class PackageManager {
    public Intent getLaunchIntentForPackage(String packageName) {
        return null;
    }

    public String[] getPackagesForUid(int uid) {
        return new String[0];
    }

    public String[] getPackagesForUid(String uid) {
        return new String[0];
    }
}
