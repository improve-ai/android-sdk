/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package ai.improve.android;

import ai.improve.android.spi.DecisionMaker;
import ai.improve.android.spi.DefaultDecisionTracker;
import android.app.Application;
import android.net.http.HttpResponseCache;
import com.google.common.io.CharStreams;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.MockServerRule;
import org.mockserver.verify.VerificationTimes;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.*;
import java.util.Collections;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@RunWith(RobolectricTestRunner.class)
public class HttpCachingTest {

    @Rule
    public MockServerRule mockServerRule = new MockServerRule(this, 8888);


    private static final Logger jul = Logger.getLogger(HttpCachingTest.class.getName());

    private MockServerClient client;
    private Application application;

    public HttpCachingTest() {

    }

    @BeforeClass
    public static void beforeClass() {
        System.setProperty("java.util.logging.config.file", ClassLoader.getSystemResource("logging.properties").getPath());
    }

    @Before
    public void initTest() throws Exception {
        application = RuntimeEnvironment.application;

        client.reset();
        client.when(
                request()
                        .withMethod("GET")
                        .withPath("/download"))
                .respond(
                        response()
                                .withStatusCode(200)
                                .withBody(compress("RESULT: OK"))
                );
    }

    private static byte[] compress(final String str) throws IOException {
        if ((str == null) || (str.length() == 0)) {
            return null;
        }
        ByteArrayOutputStream obj = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(obj);
        gzip.write(str.getBytes("UTF-8"));
        gzip.flush();
        gzip.close();
        return obj.toByteArray();
    }

    @Test
    public void testDownloadCaching() throws Exception {
        HttpUtil util =  HttpUtil.withUrl("http://localhost:8888/download");
        InputStream is = util.stream();
        InputStreamReader r = new InputStreamReader(is);
        System.out.println(CharStreams.toString(r));

        client.verify(request().withPath("/download"));

        InputStream is2 = HttpUtil.withUrl("http://localhost:8888/download").stream();
        InputStreamReader r2 = new InputStreamReader(is);
        System.out.println(CharStreams.toString(r2));
        client.verify(request().withPath("/download"), VerificationTimes.exactly(1));
    }

    @Test
    public void testDownloadCachingSameHttpUtil() throws Exception {


        File httpCacheDir = new File(RuntimeEnvironment.application.getCacheDir(), "http");
        long httpCacheSize = 10 * 1024 * 1024; // 10 MiB
        HttpResponseCache.install(httpCacheDir, httpCacheSize);


        HttpUtil util =  HttpUtil.withUrl("http://localhost:8888/download");
        InputStream is = util.stream();
        InputStreamReader r = new InputStreamReader(is);
        System.out.println(CharStreams.toString(r));

        client.verify(request().withPath("/download"));

        InputStream is2 = util.stream();
        InputStreamReader r2 = new InputStreamReader(is);
        System.out.println(CharStreams.toString(r2));
        client.verify(request().withPath("/download"), VerificationTimes.exactly(1));
    }

}
