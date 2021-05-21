package ai.improve.sample;

import androidx.appcompat.app.AppCompatActivity;

import android.net.http.HttpResponseCache;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import ai.improve.android.IMPDecisionModel;
import ai.improve.android.IMPDecisionTracker;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    public static final String Tag = "MainActivity";

    private TextView mGreetingTV;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mGreetingTV = findViewById(R.id.greeting_tv);
        findViewById(R.id.root_view).setOnClickListener(this);

        enableHttpResponseCache();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if(id == R.id.root_view) {
            chooseFrom();
            track();

            new Thread() {
                @Override
                public void run() {
                    testHttpUrlConnection();
                }
            }.start();
//            testHttpUrlConnection();
        }
    }

    private void chooseFrom() {
        List<Object> variants = new ArrayList<>();
        variants.add("Hello, World!");
        variants.add("hello, world!");
        variants.add("hello");
        variants.add("hi");

        IMPDecisionModel model = new IMPDecisionModel("orange");
        String greeting = (String) model.chooseFrom(variants).get();
        Log.d(Tag, "greeting=" + greeting);

        if(!TextUtils.isEmpty(greeting)) {
            mGreetingTV.setText(greeting);
            mGreetingTV.setTextColor(getColor(R.color.black));
        } else {
            mGreetingTV.setText("greeting is null or empty");
            mGreetingTV.setTextColor(getColor(R.color.red));
        }

//        IMPDecisionModel.load()
    }

    private void track() {
        List<Object> variants = new ArrayList<>();
        variants.add("Hello, World!");
        variants.add("hello, world!");
        variants.add("hello");
        variants.add("hi");

        IMPDecisionTracker tracker = new IMPDecisionTracker(getApplicationContext(), "");

        Object variant = new IMPDecisionModel("orange").track(tracker).chooseFrom(variants).get();
        Log.d(Tag, "variant = " + variant);
    }

    private void testHttpUrlConnection() {
        HttpURLConnection urlConnection = null;
        try {
            URL url = new URL("http://10.254.115.144:8080/dummy_v6.xgb");
            urlConnection = (HttpURLConnection)url.openConnection();
            urlConnection.setReadTimeout(15000);
            InputStream inputStream = new BufferedInputStream(urlConnection.getInputStream());

            int totalBytes = 0;
            int bytesRead = 0;
            byte[] buffer = new byte[1024];
//            DataInputStream dis = new DataInputStream(inputStream);
            while(-1 != (bytesRead = inputStream.read(buffer))) {
                totalBytes += bytesRead;
            }
            Log.d(Tag, "totalBytesRead: " + totalBytes);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    private void enableHttpResponseCache() {
        try {
            File httpCacheDir = new File(getCacheDir(), "http");
            long httpCacheSize = 10 * 1024 * 1024; // 10 MiB
            HttpResponseCache.install(httpCacheDir, httpCacheSize);
        } catch (IOException e) {
            Log.i(Tag, "HTTP response cache installation failed:" + e);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        HttpResponseCache cache = HttpResponseCache.getInstalled();
        if (cache != null) {
            cache.flush();
            Log.i(Tag, "Cache flushed");
        }
    }
}