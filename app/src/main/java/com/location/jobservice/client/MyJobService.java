package com.location.jobservice.client;

import android.annotation.SuppressLint;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.SetOptions;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class MyJobService extends JobService {
    private static final String TAG = "JobService";
    private static final int RUN_TIME = 1000 * 60;  // 5 minutes
    private static final int DELAY = 1000 * 5;  // 10 seconds
    private static final String collection = "Locations";
    private static final String LAT = "Lat", LNG = "Lng";
    private static final String hField = "History", cField = "current", lField = "lastRun";
    private static final String sField = "sendData", rField = "currentRunning";
    private static String idTag = "";
    private boolean isSending = true;
    private double curLat, curLng;
    private double lstLat = 27.9, lstLng = 78.1;

    private Map<String, Object> temp = new HashMap<>();
    private Map<String, Object> latLngMap = new HashMap<>();
    private List<Map<String, Object>> mapList = new ArrayList<>();
    private Calendar cal;
    private SimpleDateFormat sdf;

    private MyAsyncTask myAsyncTask;
    private JobParameters parameters;
    private FusedLocationProviderClient fusedClient;
    private DocumentReference docReference;

    @SuppressLint("SimpleDateFormat")
    @Override
    public boolean onStartJob(JobParameters params) {
        Log.d(TAG, "Job Started");
        this.parameters = params;
        FirebaseFirestore fStore = FirebaseFirestore.getInstance();
        PersistableBundle bundle = params.getExtras();
        String text = bundle.getString("idTag");
        if (text != null && !text.equals("")) idTag = " " + text;
        String deviceName = getDeviceName();
        docReference = fStore.collection(collection).document(deviceName);
        fusedClient = LocationServices.getFusedLocationProviderClient(this);
        cal = Calendar.getInstance();
        sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        temp.put(LAT, lstLat);
        temp.put(LNG, lstLng);

        myAsyncTask = new MyAsyncTask();
        myAsyncTask.execute();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.d(TAG, "Job Stopped");
        if (myAsyncTask != null) {
            if (!myAsyncTask.isCancelled()) {
                myAsyncTask.cancel(true);
            }
        }
        return false;
    }

    @SuppressLint("StaticFieldLeak")
    private class MyAsyncTask extends AsyncTask<Void, Integer, String> {

        @Override
        protected String doInBackground(Void... voids) {
            checkDocExist();
            SystemClock.sleep(DELAY);
            if (isSending) {
                int timesLoop = RUN_TIME / DELAY;
                for (int i = 0; i < timesLoop; i++) {
                    getLocation();
                    Log.d(TAG, "loop: " + i + " Lng: " + curLng + " Lat: " + curLat);
                    //showToast("Lat: " + curLat + "\nLng: " + curLng);
                    sendCurrentLocation();
                    if (isCancelled()) break;
                    SystemClock.sleep(DELAY);
                }
                copyLastMapToHistoryMap();
            }
            return "Job Finished";
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            Log.d(TAG, s);
            jobFinished(parameters, true);
        }
    }

    private void checkDocExist() {
        docReference.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (task.isSuccessful()) {
                    DocumentSnapshot document = task.getResult();
                    if (document != null && document.exists()) {
                        docReference.addSnapshotListener(new EventListener<DocumentSnapshot>() {
                            @Override
                            public void onEvent(@Nullable DocumentSnapshot documentSnapshot, @Nullable FirebaseFirestoreException e) {
                                if (documentSnapshot != null) {
                                    //noinspection ConstantConditions
                                    isSending = documentSnapshot.getBoolean(sField);
                                }
                            }
                        });
                    } else {
                        Map<String, Object> data = new HashMap<>();
                        data.put(sField, true);
                        data.put(rField, true);
                        data.put(cField, temp);
                        data.put(lField, Arrays.asList(temp, temp));
                        Map<String, Object> hMap = new HashMap<>();
                        hMap.put("00:00:00", Arrays.asList(temp, temp));
                        data.put(hField, hMap);
                        docReference.set(data);
                    }
                }
            }
        });
        docReference.update(rField, true);
    }

    private void getLocation() {
        fusedClient.getLastLocation().addOnCompleteListener(new OnCompleteListener<Location>() {
            @SuppressLint("DefaultLocale")
            @Override
            public void onComplete(@NonNull Task<Location> task) {
                Location location = task.getResult();
                if (location != null) {
                    try {
                        Geocoder geocoder = new Geocoder(MyJobService.this, Locale.getDefault());
                        List<Address> addresses = geocoder.getFromLocation(
                                location.getLatitude(), location.getLongitude(), 1);
                        curLat = Double.parseDouble(String.format("%.7f", addresses.get(0).getLatitude()));
                        curLng = Double.parseDouble(String.format("%.7f", addresses.get(0).getLongitude()));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void sendCurrentLocation() {
        if (curLat != lstLat && curLng != lstLng && curLat != 0.0) {
            lstLat = curLat;
            lstLng = curLng;
            // Create current LatLng Map
            latLngMap.put(LAT, curLat);
            latLngMap.put(LNG, curLng);
            // Update Document
            docReference.update(
                    cField, latLngMap,
                    lField, FieldValue.arrayUnion(latLngMap)
            );
        }
    }

    private void copyLastMapToHistoryMap() {
        docReference.addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot documentSnapshot, @Nullable FirebaseFirestoreException e) {
                if (documentSnapshot != null && documentSnapshot.exists()){
                    mapList = (List<Map<String, Object>>) documentSnapshot.get(lField);
                }
            }
        });
        SystemClock.sleep(DELAY);
        if (mapList.size() > 3) {  // 10
            String newField = hField + "." + sdf.format(cal.getTime());
            docReference.update(
                    newField, mapList,
                    rField, false,
                    lField, Arrays.asList(temp, temp)
            );
            Log.d(TAG, "HField updated");
        }
    }

    private static String getDeviceName() {
        String manufacture = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.startsWith(manufacture)) {
            return model + idTag;
        }
        return manufacture + " " + model + idTag;
    }

    private void showToast(final String msg){
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MyJobService.this.getApplicationContext(),msg ,Toast.LENGTH_SHORT).show();
            }
        });
    }

}
