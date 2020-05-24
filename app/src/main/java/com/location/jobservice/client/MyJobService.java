package com.location.jobservice.client;

import android.annotation.SuppressLint;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.os.Vibrator;
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
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.SetOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class MyJobService extends JobService {
    private static final int RUN_TIME = 1000 * 60 * 2;  // 5 minutes
    private static final int DELAY = 1000 * 5;  // 5 seconds
    private static final int MAX = 80;  // History limit
    private static final String TAG = "LocSenderJobService";
    private static final String collection = "Locations";
    private static final String LAT = "Lat", LNG = "Lng";
    private static final String hField = "History", cField = "current", sField = "sendData";
    private static String idTag = "";
    private boolean isSending = true;
    private double curLatitude, curLongitude;

    private Map<String, Object> latLngMap = new HashMap<>();
    private Map<String, Object> currentMap = new HashMap<>();
    private Map<String, Object> historyMap = new HashMap<>();
    private List<Map<String, Object>> mapList;

    private MyAsyncTask myAsyncTask;
    private JobParameters parameters;
    private FusedLocationProviderClient fusedClient;
    private DocumentReference docReference;

    @Override
    public boolean onStartJob(JobParameters params) {
        FirebaseFirestore fStore = FirebaseFirestore.getInstance();
        this.parameters = params;
        PersistableBundle bundle = params.getExtras();
        String text = bundle.getString("idTag");
        if (text != null && !text.equals("")) {
            idTag = " " + text;
        }
        String deviceName = getDeviceName();
        fusedClient = LocationServices.getFusedLocationProviderClient(this);
        docReference = fStore.collection(collection).document(deviceName);
        myAsyncTask = new MyAsyncTask();
        myAsyncTask.execute();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.d(TAG, "onStopJob: Job Cancelled");
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
                    publishProgress(i);
                    sendCurrentLocation();
                    SystemClock.sleep(DELAY);
                }
            }
            return "Job Finished";
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            Log.d(TAG, "onProgressUpdate: LoopCount: " + values[0] +
                    " Latitude: " + curLatitude + " Longitude: " + curLongitude);
            showToast("Latitude: " + curLatitude + "\nLongitude: " + curLongitude);
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                vibrator.vibrate(100);
            }
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            Log.d(TAG, "onPostExecute: Message: " + s);
            jobFinished(parameters, true);
        }
    }

    private void checkDocExist() {
        docReference.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (task.isSuccessful()){
                    DocumentSnapshot document = task.getResult();
                    if (Objects.requireNonNull(document).exists()){
                        docReference.addSnapshotListener(new EventListener<DocumentSnapshot>() {
                            @Override
                            public void onEvent(@Nullable DocumentSnapshot documentSnapshot, @Nullable FirebaseFirestoreException e) {
                                if (documentSnapshot != null){
                                    isSending = documentSnapshot.getBoolean(sField);
                                }
                            }
                        });
                    } else {
                        Log.d(TAG, "Document does not exist. Creating doc...");
                        // Add History field to fireStore
                        Map<String, Object> temp1 = new HashMap<>();
                        temp1.put(LAT, 11.11);
                        temp1.put(LNG, 11.11);
                        Map<String, Object> temp2 = new HashMap<>();
                        temp2.put(LAT, 11.11);
                        temp2.put(LNG, 11.11);
                        historyMap.put(hField, Arrays.asList(temp1, temp2));
                        docReference.set(historyMap, SetOptions.merge());
                        // Add current field
                        currentMap.put(cField, temp1);
                        docReference.set(currentMap, SetOptions.merge());
                        // Add Status to fireStore
                        Map<String, Object> status = new HashMap<>();
                        status.put(sField, true);
                        docReference.set(status, SetOptions.merge());
                        Log.d(TAG, "Document created");
                    }
                }
            }
        });
    }

    private void getLocation() {
        fusedClient.getLastLocation().addOnCompleteListener(new OnCompleteListener<Location>() {
            @Override
            public void onComplete(@NonNull Task<Location> task) {
                // Initialize Location
                Location location = task.getResult();
                if (location != null) {
                    try {
                        // Initialize geoCoder
                        Geocoder geocoder = new Geocoder(MyJobService.this, Locale.getDefault());
                        // Initialize address list
                        List<Address> addresses = geocoder.getFromLocation(
                                location.getLatitude(), location.getLongitude(), 1
                        );
                        // Get Latitude and Longitude
                        curLatitude = addresses.get(0).getLatitude();
                        curLongitude = addresses.get(0).getLongitude();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void sendCurrentLocation() {
        latLngMap.put(LAT, curLatitude);
        latLngMap.put(LNG, curLongitude);
        currentMap.put(cField, latLngMap);
        docReference.set(currentMap, SetOptions.merge());

        docReference.addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot documentSnapshot, @Nullable FirebaseFirestoreException e) {
                if (documentSnapshot != null) {
                    mapList = (List<Map<String, Object>>) documentSnapshot.get(hField);
                }
            }
        });

        if (mapList == null) {
            List<Map<String, Object>> temp = new ArrayList<>();
            temp.add(latLngMap);
            mapList = temp;
        }

        if (!mapList.get(mapList.size() - 1).equals(latLngMap)) {
            mapList.add(latLngMap);
            if (mapList.size() > MAX) {
                mapList.remove(0);
            }
            historyMap.put(hField, mapList);
            docReference.set(historyMap, SetOptions.merge());
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

    private void showToast(final String msg) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MyJobService.this.getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

}
