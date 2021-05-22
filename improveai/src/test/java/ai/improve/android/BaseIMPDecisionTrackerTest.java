package ai.improve.android;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class BaseIMPDecisionTrackerTest {
    public static final String Tag = "IMPDecisionModelTest";

    public class IMPDecisionTracker extends BaseIMPDecisionTracker {

        public IMPDecisionTracker(String trackURL, HistoryIdProvider historyIdProvider) {
            super(trackURL, historyIdProvider);
        }

        public IMPDecisionTracker(String trackURL, String apiKey, HistoryIdProvider historyIdProvider) {
            super(trackURL, apiKey, historyIdProvider);
        }

        private void test() {
            System.out.println("xxxx");
        }
    }

    public class HistoryIdProviderImp implements HistoryIdProvider {
        @Override
        public String getHistoryId() {
            return "android_test_history_id";
        }
    }

    @Test
    public void testShouldTrackRunnersUp_0_variantsCount() {
        int variantCount = 0;
        int loop = 1000000;
        int shouldTrackCount = 0;

        IMPDecisionTracker tracker = new IMPDecisionTracker("", new HistoryIdProviderImp());
        tracker.setMaxRunnersUp(50);
        tracker.shouldtrackRunnersUp(variantCount);

        for(int i = 0; i < loop; ++i) {
            if(tracker.shouldtrackRunnersUp(variantCount)) {
                shouldTrackCount++;
            }
        }
        assertEquals(shouldTrackCount, 0);
    }

    @Test
    public void testShouldTrackRunnersUp_1_variantsCount() {
        int variantCount = 1;
        int loop = 1000000;
        int shouldTrackCount = 0;

        IMPDecisionTracker tracker = new IMPDecisionTracker("", new HistoryIdProviderImp());
        tracker.setMaxRunnersUp(50);
        tracker.shouldtrackRunnersUp(variantCount);

        for(int i = 0; i < loop; ++i) {
            if(tracker.shouldtrackRunnersUp(variantCount)) {
                shouldTrackCount++;
            }
        }
        assertEquals(shouldTrackCount, 0);
    }

    @Test
    public void testShouldTrackRunnersUp_10_variantsCount() {
        int variantCount = 10;
        int loop = 10000000;
        int shouldTrackCount = 0;

        IMPDecisionTracker tracker = new IMPDecisionTracker("", new HistoryIdProviderImp());
        tracker.setMaxRunnersUp(50);
        tracker.shouldtrackRunnersUp(variantCount);

        for(int i = 0; i < loop; ++i) {
            if(tracker.shouldtrackRunnersUp(variantCount)) {
                shouldTrackCount++;
            }
        }

        int expectedCount = (int)(1.0/Math.min(variantCount-1, tracker.getMaxRunnersUp()) * loop);
        double diff = Math.abs((expectedCount-shouldTrackCount)/(double)expectedCount);
        assertTrue(diff < 0.01);
        System.out.println("expected=" + expectedCount + ", real=" + shouldTrackCount
                + ", diff=" + (expectedCount-shouldTrackCount)/(double)expectedCount);
    }

    @Test
    public void testShouldTrackRunnersUp_100_variantsCount() {
        int variantCount = 100;
        int loop = 10000000;
        int shouldTrackCount = 0;

        IMPDecisionTracker tracker = new IMPDecisionTracker("", new HistoryIdProviderImp());
        tracker.setMaxRunnersUp(50);
        tracker.shouldtrackRunnersUp(variantCount);

        for(int i = 0; i < loop; ++i) {
            if(tracker.shouldtrackRunnersUp(variantCount)) {
                shouldTrackCount++;
            }
        }

        int expectedCount = (int)(1.0/Math.min(variantCount-1, tracker.getMaxRunnersUp()) * loop);
        double diff = Math.abs((expectedCount-shouldTrackCount)/(double)expectedCount);
        System.out.println("expected=" + expectedCount + ", real=" + shouldTrackCount
                + ", diff=" + (expectedCount-shouldTrackCount)/(double)expectedCount);
        assertTrue(diff < 0.01);
    }

    @Test
    public void testShouldTrackRunnersUp_0_maxRunnersUp() {
        int variantCount = 10;
        int loop = 1000000;
        int shouldTrackCount = 0;

        IMPDecisionTracker tracker = new IMPDecisionTracker("", new HistoryIdProviderImp());
        tracker.setMaxRunnersUp(0);
        tracker.shouldtrackRunnersUp(variantCount);

        for(int i = 0; i < loop; ++i) {
            if(tracker.shouldtrackRunnersUp(variantCount)) {
                shouldTrackCount++;
            }
        }
        assertEquals(shouldTrackCount, 0);
    }

    // If there are no runners up, then sample is a random sample from
    // variants with just best excluded.
    @Test
    public void testSampleVariant_0_RunnersUp() throws Exception {
        List<Object> variants = new ArrayList<>();
        variants.add("Hello, World!");
        variants.add("hello, world!");
        variants.add("hello");
        variants.add("hi");
        variants.add("Hello World!");

        IMPDecisionTracker tracker = new IMPDecisionTracker("", new HistoryIdProviderImp());
        tracker.setMaxRunnersUp(0);
        Method method = getDeclaredMethod(tracker, "sampleVariant", List.class, int.class);
        method.setAccessible(true);

        Method topRunnersUpMethod = getDeclaredMethod(tracker, "topRunnersUp", List.class);
        topRunnersUpMethod.setAccessible(true);
        int runnersUpCount = ((List)topRunnersUpMethod.invoke(tracker, variants)).size();
        System.out.println("runnersUpCount=" + runnersUpCount);

        Map<String, Integer> countMap = new HashMap<>();
        int loop = 10000000;
        for(int i = 0; i < loop; ++i) {
            String variant = (String) method.invoke(tracker, variants, runnersUpCount);
            if(countMap.containsKey(variant)) {
                countMap.put(variant, countMap.get(variant) + 1);
            } else {
                countMap.put(variant, 1);
            }
        }

        int expectedCount = loop / (variants.size()-1-runnersUpCount);
        for(int i = 1+runnersUpCount; i < variants.size(); ++i){
            assertTrue(countMap.containsKey(variants.get(i)));
            int diff = Math.abs(countMap.get(variants.get(i)) - expectedCount);
            System.out.println("expected=" + expectedCount + ", real=" + countMap.get(variants.get(i))
                    + ", diff=" + diff);
            assertTrue(diff < (expectedCount * 0.01));
        }
    }

    // If there are runners up, then sample is a random sample from
    // variants with best and runners up excluded.
    @Test
    public void testSampleVariant_2_RunnersUp() throws Exception {
        List<Object> variants = new ArrayList<>();
        variants.add("Hello, World!");
        variants.add("hello, world!");
        variants.add("hello");
        variants.add("hi");
        variants.add("Hello World!");

        IMPDecisionTracker tracker = new IMPDecisionTracker("", new HistoryIdProviderImp());
        tracker.setMaxRunnersUp(2);
        Method method = getDeclaredMethod(tracker, "sampleVariant", List.class, int.class);
        method.setAccessible(true);

        Method topRunnersUpMethod = getDeclaredMethod(tracker, "topRunnersUp", List.class);
        topRunnersUpMethod.setAccessible(true);
        int runnersUpCount = ((List)topRunnersUpMethod.invoke(tracker, variants)).size();
        System.out.println("runnersUpCount=" + runnersUpCount);

        Map<String, Integer> countMap = new HashMap<>();
        int loop = 10000000;
        for(int i = 0; i < loop; ++i) {
            String variant = (String) method.invoke(tracker, variants, runnersUpCount);
            if(countMap.containsKey(variant)) {
                countMap.put(variant, countMap.get(variant) + 1);
            } else {
                countMap.put(variant, 1);
            }
        }

        int expectedCount = loop / (variants.size()-1-runnersUpCount);
        for(int i = 1+runnersUpCount; i < variants.size(); ++i){
            assertTrue(countMap.containsKey(variants.get(i)));
            int diff = Math.abs(countMap.get(variants.get(i)) - expectedCount);
            System.out.println("expected=" + expectedCount + ", real=" + countMap.get(variants.get(i))
                    + ", diff=" + diff);
            assertTrue(diff < (expectedCount * 0.01));
        }
    }

    // If there is only one variant, which is the best, then there is no sample.
    @Test
    public void testSampleVariant_1_variant() throws Exception {
        List<Object> variants = new ArrayList<>();
        variants.add("Hello, World!");

        IMPDecisionTracker tracker = new IMPDecisionTracker("", new HistoryIdProviderImp());
        tracker.setMaxRunnersUp(50);
        Method method = getDeclaredMethod(tracker, "sampleVariant", List.class, int.class);
        method.setAccessible(true);

        Method topRunnersUpMethod = getDeclaredMethod(tracker, "topRunnersUp", List.class);
        topRunnersUpMethod.setAccessible(true);
        int runnersUpCount = ((List)topRunnersUpMethod.invoke(tracker, variants)).size();
        System.out.println("runnersUpCount=" + runnersUpCount);

        int loop = 10000000;
        for(int i = 0; i < loop; ++i) {
            String variant = (String) method.invoke(tracker, variants, runnersUpCount);
            assertNull(variant);
        }
    }

    // If there are no remaining variants after best and runners up, then there is no sample.
    @Test
    public void testSampleVariant_0_remaining_variants() throws Exception {
        List<Object> variants = new ArrayList<>();
        variants.add("Hello, World!");
        variants.add("hello, world!");
        variants.add("hello");
        variants.add("hi");
        variants.add("Hello World!");

        IMPDecisionTracker tracker = new IMPDecisionTracker("", new HistoryIdProviderImp());
        tracker.setMaxRunnersUp(50);
        Method method = getDeclaredMethod(tracker, "sampleVariant", List.class, int.class);
        method.setAccessible(true);

        Method topRunnersUpMethod = getDeclaredMethod(tracker, "topRunnersUp", List.class);
        topRunnersUpMethod.setAccessible(true);
        int runnersUpCount = ((List)topRunnersUpMethod.invoke(tracker, variants)).size();
        System.out.println("runnersUpCount=" + runnersUpCount);

        int loop = 10000000;
        for(int i = 0; i < loop; ++i) {
            String variant = (String) method.invoke(tracker, variants, runnersUpCount);
            assertNull(variant);
        }
    }

    @Test
    public void testSetBestVariantNil() throws Exception {
        IMPDecisionTracker tracker = new IMPDecisionTracker("", new HistoryIdProviderImp());
        tracker.setMaxRunnersUp(50);
        Method method = getDeclaredMethod(tracker, "setBestVariant", Object.class, Map.class);
        method.setAccessible(true);

        Map<String, Object> body = new HashMap();
        method.invoke(tracker, null, body);
        // body looks like this
        // {
        //     "count" : 1,
        //     "variant" : null
        // }
        assertNull(body.get("variant"));
        assertEquals(1, body.get("count"));
    }

    @Test
    public void testSetBestVariantNonNil() throws Exception {
        IMPDecisionTracker tracker = new IMPDecisionTracker("", new HistoryIdProviderImp());
        tracker.setMaxRunnersUp(50);
        Method method = getDeclaredMethod(tracker, "setBestVariant", Object.class, Map.class);
        method.setAccessible(true);

        Map<String, Object> body = new HashMap();
        method.invoke(tracker, "hello", body);

        assertEquals("hello", body.get("variant"));
    }

    private Method getDeclaredMethod(Object object, String methodName, Class<?> ... parameterTypes){
        Method method;
        for(Class<?> clazz = object.getClass(); clazz != Object.class; clazz = clazz.getSuperclass()) {
            try {
                method = clazz.getDeclaredMethod(methodName, parameterTypes) ;
                return method ;
            } catch (Exception e) {
            }
        }
        return null;
    }
}