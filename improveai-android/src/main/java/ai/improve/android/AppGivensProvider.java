package ai.improve.android;

import static ai.improve.android.AppGivensProviderUtils.SecondsPerDay;
import static ai.improve.android.Constants.Improve_SP_File_Name;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import ai.improve.DecisionModel;
import ai.improve.constants.BuildProperties;
import ai.improve.provider.GivensProvider;

public class AppGivensProvider implements GivensProvider {
    private static final String Tag = "AppGivensProvider";

    private static final String APP_Givens_Key_Country = "$country";
    protected static final String APP_Givens_Key_Language = "$lang";
    private static final String APP_Givens_Key_Timezone_Offset = "$tz";
    private static final String APP_Givens_Key_Carrier = "$carrier";
    private static final String APP_Givens_Key_Device = "$device";
    private static final String APP_Givens_Key_Device_Version = "$devicev";
    private static final String APP_Givens_Key_OS = "$os";
    private static final String APP_Givens_Key_OS_Version = "$osv";
    private static final String APP_Givens_Key_Screen_Pixels = "$pixels";
    private static final String APP_Givens_Key_App = "$app";
    private static final String APP_Givens_Key_App_Version = "$appv";
    private static final String APP_Givens_Key_Improve_Version = "$sdkv";
    private static final String APP_Givens_Key_Week_Day = "$weekday";
    private static final String APP_Givens_Key_Since_Midnight = "$time";
    private static final String APP_Givens_Key_Since_Session_Start = "$runtime";
    private static final String APP_Givens_Key_Since_Born = "$day";
    private static final String APP_Givens_Key_Decision_Count = "$d";
    private static final String App_Givens_Key_Rewards = "$r";
    private static final String App_Givens_Key_Rewards_Per_Decision = "$r/d";
    private static final String App_Givens_Key_Decisions_Per_Day = "$d/day";

    // SharedPreference key
    public static final String SP_Key_Born_Time = "born_time";
    public static final String SP_Key_Decision_Count = "decision_count-%s";
    public static final String SP_Key_Model_Reward = "rewards-%s";

    private Context appContext;

    public AppGivensProvider(Context context) {
        appContext = context.getApplicationContext();
    }

    public static void setBornTime(Context context) {
        SharedPreferences sp = context.getSharedPreferences(Improve_SP_File_Name, Context.MODE_PRIVATE);
        long bornTime = sp.getLong(SP_Key_Born_Time, 0);
        if(bornTime == 0) {
            sp.edit().putLong(SP_Key_Born_Time, System.currentTimeMillis()).apply();
        }
    }

    @Override
    public Map<String, Object> givensForModel(DecisionModel decisionModel, Map<String, Object> givens) {
        Map<String, Object> appGivens = new HashMap<>();

        appGivens.put(APP_Givens_Key_Country, getCountry());
        appGivens.put(APP_Givens_Key_Language, getLanguage());
        appGivens.put(APP_Givens_Key_Timezone_Offset, getGMTTimezoneOffset());
        appGivens.put(APP_Givens_Key_Carrier, getCarrier());
        appGivens.put(APP_Givens_Key_OS, getOS());
        appGivens.put(APP_Givens_Key_OS_Version, getOsVersion());
        appGivens.put(APP_Givens_Key_Device, getDevice());
        appGivens.put(APP_Givens_Key_Device_Version, getDeviceVersion());
        appGivens.put(APP_Givens_Key_App, getApp());
        appGivens.put(APP_Givens_Key_App_Version, getAppVersion(appContext));
        appGivens.put(APP_Givens_Key_Improve_Version, getImproveVersion());
        appGivens.put(APP_Givens_Key_Screen_Pixels, getScreenPixels(appContext));
        appGivens.put(APP_Givens_Key_Week_Day, getDayOfWeek());
        appGivens.put(APP_Givens_Key_Since_Midnight, getSinceMidnight());
        appGivens.put(APP_Givens_Key_Since_Session_Start, getSinceSessionStart());
        appGivens.put(APP_Givens_Key_Since_Born, getSinceBorn());
        appGivens.put(APP_Givens_Key_Decision_Count, getDecisionCount(decisionModel.getModelName()));
        appGivens.put(App_Givens_Key_Rewards, AppGivensProviderUtils.roundedRewardOfModel(decisionModel.getModelName()));
        appGivens.put(App_Givens_Key_Rewards_Per_Decision, AppGivensProviderUtils.rewardsPerDecision(decisionModel.getModelName()));
        appGivens.put(App_Givens_Key_Decisions_Per_Day, AppGivensProviderUtils.decisionsPerDay(decisionModel.getModelName()));

        // increment decision count value by 1
        SharedPreferences sp = appContext.getSharedPreferences(Improve_SP_File_Name, Context.MODE_PRIVATE);
        String decisionCountKey = AppGivensProviderUtils.getDecisionCountKeyOfModel(decisionModel.getModelName());
        int curDecisionCount = sp.getInt(decisionCountKey, 0);
        sp.edit().putInt(decisionCountKey, curDecisionCount+1).apply();

        // If keys in givens overlap with keys in AppGivensProvider, keys in givens win
        if(givens != null) {
            appGivens.putAll(givens);
        }

        return appGivens;
    }

    private String getCountry() {
        String country = getCountryFromNetwork();
        if (!TextUtils.isEmpty(country)) {
            return country;
        }
        return getCountryFromLocale();
    }

    private String getCountryFromNetwork() {
        try {
            TelephonyManager manager = (TelephonyManager) appContext
                    .getSystemService(Context.TELEPHONY_SERVICE);
            if (manager.getPhoneType() != TelephonyManager.PHONE_TYPE_CDMA) {
                String country = manager.getNetworkCountryIso();
                if (country != null) {
                    return country.toUpperCase(Locale.US);
                }
            }
        } catch (Exception e) {
            // Failed to get country from network
        }
        return "";
    }

    private String getCountryFromLocale() {
        return Locale.getDefault().getCountry().toUpperCase(Locale.US);
    }

    private String getLanguage() {
        return Locale.getDefault().getLanguage();
    }

    private int getGMTTimezoneOffset() {
        Calendar mCalendar = new GregorianCalendar();
        TimeZone mTimeZone = mCalendar.getTimeZone();
        return mTimeZone.getRawOffset() / 3600000;
    }

    private String getCarrier() {
        try {
            TelephonyManager manager = (TelephonyManager) appContext.getSystemService(Context.TELEPHONY_SERVICE);
            return manager.getNetworkOperatorName();
        } catch (Exception e) {
            // Failed to get network operator name from network
        }
        return "";
    }

    private String getOS() {
        return "Android";
    }

    private double getOsVersion() {
        return AppGivensProviderUtils.versionToInt(Build.VERSION.RELEASE);
    }

    private String getDevice() {
        if(!TextUtils.isEmpty(Build.MODEL) && Build.MODEL.contains(Build.MANUFACTURER)) {
            // There are devices that have manufacture included in the model field.
            return Build.MODEL;
        } else {
            return Build.MANUFACTURER + " " + Build.MODEL;
        }
    }

    private double getDeviceVersion() {
        return AppGivensProviderUtils.parseDeviceVersion(Build.MODEL);
    }

    public String getApp() {
        try {
            PackageManager packageManager = appContext.getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(appContext.getPackageName(), 0);
            int labelRes = packageInfo.applicationInfo.labelRes;
            return appContext.getResources().getString(labelRes);
        } catch (Exception e) {
//            e.printStackTrace();
        }
        return "";
    }

    /**
     * @return A string used as the version number shown to users.
     * The versionName has no purpose other than to be displayed to users.
     * It could be anything like: 1.0, 1.0.0, 1.0.0 alpha, alpha 1.0.0.
     * */
    private double getAppVersion(Context context) {
        PackageInfo packageInfo;
        try {
            packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return AppGivensProviderUtils.versionToInt(packageInfo.versionName);
        } catch (Exception e) {
        }
        return 0;
    }

    private double getImproveVersion() {
        String version = BuildProperties.getSDKVersion();
        return AppGivensProviderUtils.versionToInt(version);
    }

    private int getScreenPixels(Context context) {
        WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = manager.getDefaultDisplay();
        DisplayMetrics displayMetrics = new DisplayMetrics();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            display.getRealMetrics(displayMetrics);
        } else {
            display.getMetrics(displayMetrics);
        }
        return displayMetrics.widthPixels * displayMetrics.heightPixels;
    }

    /**
     * keep 5 fractional digits
     * */
    private double getDayOfWeek() {
        // I have not found any framework method that can do the conversion more elegantly.
        Map weekDayMap = new HashMap() {
            {
                put(Calendar.MONDAY, 1);
                put(Calendar.TUESDAY, 2);
                put(Calendar.WEDNESDAY, 3);
                put(Calendar.THURSDAY, 4);
                put(Calendar.FRIDAY, 5);
                put(Calendar.SATURDAY, 6);
                put(Calendar.SUNDAY, 7);
            }
        };
        Calendar calendar = Calendar.getInstance();
        Date date = calendar.getTime();
        int seconds = date.getHours() * 3600 + date.getMinutes() * 60 + date.getSeconds();
        double weekday = (int)weekDayMap.get(calendar.get(Calendar.DAY_OF_WEEK)) + seconds / SecondsPerDay;
        return Math.round(weekday * 100000) / 100000.0;
    }

    /**
     * @return fractional days
     * */
    private double getSinceMidnight() {
        Date date = new Date();
        double seconds = date.getHours() * 3600 + date.getMinutes() * 60 +
                date.getSeconds() + (date.getTime() % 1000)/1000.0;
        return Math.round(seconds / SecondsPerDay * 1000000) / 1000000.0;
    }

    /**
     * Session start time is the moment when the first AppGivensProvider instance is created.
     * */
    private double getSinceSessionStart() {
        double t = AppGivensProviderUtils.getSinceSessionStart();
        return Math.round(t * 1000000) / 1000000.0;
    }

    private double getSinceBorn() {
        double t = AppGivensProviderUtils.getSinceBorn(appContext);
        return Math.round(t * 1000000) / 1000000.0;
    }

    // 0 is returned for the first session
    private int getDecisionCount(String modelName) {
        return AppGivensProviderUtils.getDecisionCount(appContext, modelName);
    }
}