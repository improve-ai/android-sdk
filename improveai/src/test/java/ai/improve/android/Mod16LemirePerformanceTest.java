/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package ai.improve.android;

import ai.improve.android.hasher.GuavaMmh3Hasher;
import org.apache.commons.codec.digest.MurmurHash3;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Random;
import java.util.logging.Logger;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class Mod16LemirePerformanceTest {


    public Mod16LemirePerformanceTest() {
    }

    private long unsignedMmh3Hash(String value, int modelSeed) {
        try {
            long hash = new GuavaMmh3Hasher(modelSeed).hashBytes(value.getBytes("UTF-8")).asInt();
            return hash & 0x0ffffffffl; //convert to unsigned
        } catch (UnsupportedEncodingException ex) {
            return 0;
        }
    }

    @Test
    public void testMmh3() {
        int NUM_SAMPLES = 10000000;
        int SAMPLE_SIZE = 8;
        String[] samples = new String[NUM_SAMPLES];
        for( int i = 0; i < NUM_SAMPLES; ++i) {
            samples[i] = RandomStringUtils.randomAscii(SAMPLE_SIZE);
        }
        int modelSeed = RandomUtils.nextInt();

        // Force initialisation to remove possible abberations
        unsignedMmh3Hash(samples[0], modelSeed);

        long millis = System.currentTimeMillis();

        for( int i = 0; i < NUM_SAMPLES; ++i) {
            long hash32 = unsignedMmh3Hash(samples[i], modelSeed);
        }

        System.out.println(String.format("Mmh3 hash time:          %d ms", (System.currentTimeMillis() - millis)));

        millis = System.currentTimeMillis();

        for( int i = 0; i < NUM_SAMPLES; ++i) {
            long hash32 = unsignedMmh3Hash(samples[i], modelSeed);
        }

        System.out.println(String.format("Mmh3 hash time #2:       %d ms", (System.currentTimeMillis() - millis)));

        millis = System.currentTimeMillis();

        for( int i = 0; i < NUM_SAMPLES; ++i) {
            long hash32 = unsignedMmh3Hash(samples[i], modelSeed);
            long hash32mod16 = (int)(hash32 % 65535);
        }

        System.out.println(String.format("Mmh3 hash time + mod16:  %d ms", (System.currentTimeMillis() - millis)));

        millis = System.currentTimeMillis();

        for( int i = 0; i < NUM_SAMPLES; ++i) {
            long hash32 = unsignedMmh3Hash(samples[i], modelSeed);
            long hash32lemire = (hash32 * 65535) >> 32;
        }

        System.out.println(String.format("Mmh3 hash time + Lemire: %d ms", (System.currentTimeMillis() - millis)));

    }
}
