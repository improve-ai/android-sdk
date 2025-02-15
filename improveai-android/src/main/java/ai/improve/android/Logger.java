package ai.improve.android;

import android.util.Log;

import ai.improve.log.IMPLog;

public class Logger implements IMPLog.Logger {

    @Override
    public void d(String tag, String message) {
        Log.d(tag, message);
    }

    @Override
    public void w(String tag, String message) {
        Log.w(tag, message);
    }

    @Override
    public void e(String tag, String message) {
        Log.e(tag, message);
    }
}
