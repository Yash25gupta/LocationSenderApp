package com.location.jobservice.client;

import android.Manifest;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "JobService";
    private static final int LOCATION_CODE = 1;
    private static final int JOB_ID = 11;
    private static final int REPEAT_TIME = 1000 * 60 * 15;
    private static final int MAX_REPEAT_TIME = 1000 * 60 * 20;
    private EditText editText;
    private JobScheduler scheduler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        editText = findViewById(R.id.editText);
        scheduler = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);

        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermission();
        }

    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == LOCATION_CODE) {
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                requestPermission();
            }
        }
    }

    public void startLocationJobService(View view) {
        String text = editText.getText().toString().trim();
        Log.d(TAG, "Job Scheduled");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ComponentName componentName = new ComponentName(this, MyJobService.class);
            PersistableBundle bundle = new PersistableBundle();
            bundle.putString("idTag", text);
            JobInfo.Builder builder = new JobInfo.Builder(JOB_ID, componentName)
                    .setExtras(bundle)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setPeriodic(REPEAT_TIME, MAX_REPEAT_TIME);
            } else {
                builder.setPeriodic(REPEAT_TIME);
            }
            scheduler.schedule(builder.build());
        }
    }

    public void cancelLocationJobService(View view) {
        scheduler.cancel(JOB_ID);
        Log.d(TAG, "Job Cancelled");
    }

    public void hideUnHideApp(View view) {
        PackageManager p = getPackageManager();
        ComponentName componentName = new ComponentName(this, com.location.jobservice.client.MainActivity2.class);
        switch (view.getId()) {
            case R.id.hide:
                p.setComponentEnabledSetting(componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
                Log.d(TAG, "App Hidden Successfully");
                break;
            case R.id.unHide:
                p.setComponentEnabledSetting(componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
                Log.d(TAG, "App UnHidden Successfully");
                break;
        }
    }

}
