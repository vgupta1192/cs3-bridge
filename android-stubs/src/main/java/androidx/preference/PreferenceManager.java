package androidx.preference;
import android.content.Context;
import android.content.SharedPreferences;
@android.annotation.Implemented
public class PreferenceManager {
    private Context context;
    public PreferenceManager(Context context) { this.context = context; }

    public static SharedPreferences getDefaultSharedPreferences(Context context) {
        return context.getSharedPreferences("default_settings", 0);
    }
    public PreferenceScreen createPreferenceScreen(Context context) {
        return new PreferenceScreen(context, null);
    }
}
