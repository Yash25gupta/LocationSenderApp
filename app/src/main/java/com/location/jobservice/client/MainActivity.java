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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MyLocationService";
    private static final int LOCATION_CODE = 11;
    private static final int JOB_ID = 1259;
    private static final int REPEAT_TIME = 1000 * 60 * 15;
    private static final int MAX_REPEAT_TIME = 1000 * 60 * 20;
    private EditText editText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "app Opened");
        editText = findViewById(R.id.editText);

        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            runCode();
        } else {
            requestPermission();
        }

    }

    private void runCode() {
        //startLocationJobService(findViewById(R.id.scheduleJob));
        //finish();
        Log.d(TAG, "app Closed");
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == LOCATION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                runCode();
            } else {
                requestPermission();
            }
        }
    }

    public void startLocationJobService(View view) {
        Log.d(TAG, "Job Scheduled");
        Toast.makeText(getApplicationContext(), "Job Scheduled", Toast.LENGTH_SHORT).show();
        String text = editText.getText().toString().trim();

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
            JobScheduler scheduler = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
            Objects.requireNonNull(scheduler).schedule(builder.build());
        }
    }

    public void cancelLocationJobService(View view) {
        JobScheduler scheduler = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
        Objects.requireNonNull(scheduler).cancel(JOB_ID);
        Toast.makeText(getApplicationContext(), "Job Cancelled", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Job Cancelled");
    }

    public void hideUnHideApp(View view) {
        PackageManager p = getPackageManager();
        ComponentName componentName = new ComponentName(this, com.location.jobservice.client.MainActivity2.class);
        switch (view.getId()){
            case R.id.hide:
                p.setComponentEnabledSetting(componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
                break;
            case R.id.unHide:
                p.setComponentEnabledSetting(componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
                break;
        }
        Toast.makeText(getApplicationContext(), "App hidden successfully", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "App hidden successfully");
    }

}
