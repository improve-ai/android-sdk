package ai.improve.android;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

import ai.improve.DecisionModel;
import ai.improve.log.IMPLog;

import static org.junit.Assert.*;
import static ai.improve.android.AppGivensProvider.APP_Given_Key_Since_Last_Session_Start;
import static ai.improve.android.AppGivensProvider.SP_Key_Session_Start_Time;
import static ai.improve.android.Constants.Improve_SP_File_Name;

@RunWith(AndroidJUnit4.class)
public class AppGivensProviderTest {

    private Context context;

    @Before
    public void setUp() {
        IMPLog.setLogLevel(IMPLog.LOG_LEVEL_ALL);
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void testOverlappingGivensKey() {
        Map<String, Object> userGivens = new HashMap();
        userGivens.put(AppGivensProvider.APP_Given_Key_Language, "hi");

        Map givens = new AppGivensProvider(context).givensForModel(new DecisionModel("hello"), userGivens);
        assertNotNull(givens);
        assertEquals("hi", givens.get(AppGivensProvider.APP_Given_Key_Language));
    }

    @Test
    public void testNullGivens() {
        Map<String, Object> userGivens = null;
        Map combinedGivens = new AppGivensProvider(context).givensForModel(new DecisionModel("hello"), userGivens);
        assertNotNull(combinedGivens);
        assertTrue(combinedGivens.size() > 0);
    }

    @Test
    public void test_exclude_0_since_last_session_start() {
        // remove session start time from SharedPreference
        SharedPreferences sp = context.getSharedPreferences(Improve_SP_File_Name, Context.MODE_PRIVATE);
        sp.edit().remove(SP_Key_Session_Start_Time).apply();

        Map<String, Object> userGivens = null;
        Map combinedGivens = new AppGivensProvider(context).givensForModel(new DecisionModel("hello"), userGivens);
        assertNotNull(combinedGivens);
        assertTrue(combinedGivens.size() > 0);
        assertFalse(combinedGivens.containsKey(APP_Given_Key_Since_Last_Session_Start));
    }
}
