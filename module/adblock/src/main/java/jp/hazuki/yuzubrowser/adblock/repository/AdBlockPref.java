package jp.hazuki.yuzubrowser.adblock.repository;

import android.content.Context;
import android.content.SharedPreferences;
import java.lang.String;

public final class AdBlockPref extends PrefsSchema {
    public static final String TABLE_NAME = "abp";

    private static AdBlockPref singleton;

    public AdBlockPref(Context context) {
        init(context, TABLE_NAME);
    }

    public AdBlockPref(SharedPreferences prefs) {
        init(prefs);
    }

    public static AdBlockPref get(Context context) {
        if (singleton != null) return singleton;
        synchronized (AdBlockPref.class) { if (singleton == null) singleton = new AdBlockPref(context); };
        return singleton;
    }

    public long getAbpNextUpdateTime(long defValue) {
        return getLong("abpNextUpdateTime", defValue);
    }

    public long getAbpNextUpdateTime() {
        return getLong("abpNextUpdateTime", -1L);
    }

    public void setAbpNextUpdateTime(long abpNextUpdateTime) {
        putLong("abpNextUpdateTime", abpNextUpdateTime);
    }

    public void putAbpNextUpdateTime(long abpNextUpdateTime) {
        putLong("abpNextUpdateTime", abpNextUpdateTime);
    }

    public boolean hasAbpNextUpdateTime() {
        return has("abpNextUpdateTime");
    }

    public void removeAbpNextUpdateTime() {
        remove("abpNextUpdateTime");
    }

    public long getAbpLastUpdateTime(long defValue) {
        return getLong("abpLastUpdateTime", defValue);
    }

    public long getAbpLastUpdateTime() {
        return getLong("abpLastUpdateTime", -1L);
    }

    public void setAbpLastUpdateTime(long abpLastUpdateTime) {
        putLong("abpLastUpdateTime", abpLastUpdateTime);
    }

    public void putAbpLastUpdateTime(long abpLastUpdateTime) {
        putLong("abpLastUpdateTime", abpLastUpdateTime);
    }

    public boolean hasAbpLastUpdateTime() {
        return has("abpLastUpdateTime");
    }

    public void removeAbpLastUpdateTime() {
        remove("abpLastUpdateTime");
    }
}
