package com.flexinotify;
import android.accounts.AccountManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;

import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionRequest;
import com.google.android.gms.location.ActivityTransitionEvent;
import com.google.android.gms.location.ActivityTransitionResult;

import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

import java.util.Date;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private UsageStatsManager usageStatsManager;
    private List<String> allowedDrivingApps;
    private List<String> allowedCyclingApps;
    private List<String> allowedRunningApps;
    private List<String> allowedSleepingApps;
    private List<String> allowedMeetingApps;
    private LocationManager locationManager;
    private boolean isDriving = false;
    private static boolean isCycling = false;
    private static boolean isRunning = false;
    private static boolean isSleeping = false;
    private static boolean isMeeting = false;
    private ActivityRecognitionClient activityRecognitionClient;
    private ArrayAdapter<String> allowedRunningAppsAdapter;
    private ArrayAdapter<String> allowedCyclingAppsAdapter;
    private ArrayAdapter<String> allowedDrivingAppsAdapter;
    private ArrayAdapter<String> allowedSleepingAppsAdapter;
    private ArrayAdapter<String> allowedMeetingAppsAdapter;
    private TextView indoorOutdoorStatus;
    private ListView calendarEventsListView;
    private ArrayAdapter<String> calendarEventsAdapter;
    private List<Event> calendarEvents;
    private TextView calendarEventsTextView;
    private static final int REQUEST_ACCOUNT_PICKER = 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        activityRecognitionClient = ActivityRecognition.getClient(this);
        requestActivityRecognitionUpdates();

        usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        allowedDrivingApps = new ArrayList<>();
        allowedCyclingApps = new ArrayList<>();
        allowedRunningApps = new ArrayList<>();
        allowedSleepingApps = new ArrayList<>();
        allowedMeetingApps = new ArrayList<>();
        calendarEvents = new ArrayList<>();

        if (!hasUsageAccessPermission()) {
            requestUsageAccessPermission();
        }

        requestLocationPermission();
        detectDriving();
//        detectCycling();
//        detectRunning();
        learnUserBehavior();
        restrictApps();

        ListView allowedDrivingAppsListView = findViewById(R.id.allowed_driving_apps_list);
        ListView allowedCyclingAppsListView = findViewById(R.id.allowed_cycling_apps_list);
        ListView allowedRunningAppsListView = findViewById(R.id.allowed_running_apps_list);
        ListView allowedSleepingAppsListView = findViewById(R.id.allowed_sleeping_apps_list);
        ListView allowedMeetingAppsListView = findViewById(R.id.allowed_meeting_apps_list);

        allowedDrivingAppsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, allowedDrivingApps);
        allowedCyclingAppsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, allowedCyclingApps);
        allowedRunningAppsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, allowedRunningApps);
        allowedSleepingAppsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, allowedSleepingApps);
        allowedMeetingAppsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, allowedMeetingApps);


        allowedDrivingAppsListView.setAdapter(allowedDrivingAppsAdapter);
        allowedCyclingAppsListView.setAdapter(allowedCyclingAppsAdapter);
        allowedRunningAppsListView.setAdapter(allowedRunningAppsAdapter);
        allowedSleepingAppsListView.setAdapter(allowedSleepingAppsAdapter);
        allowedMeetingAppsListView.setAdapter(allowedMeetingAppsAdapter);


        Button addAppButton = findViewById(R.id.add_app_button);
        addAppButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddAppDialog();
            }
        });

        Button checkIndoorOutdoorButton = findViewById(R.id.check_indoor_outdoor_button);
        indoorOutdoorStatus = findViewById(R.id.indoor_outdoor_status);
        checkIndoorOutdoorButton.setOnClickListener(v -> checkIndoorOutdoor());


        calendarEventsTextView = findViewById(R.id.tvEvents);

        Button btnSignIn = findViewById(R.id.btnSignIn);
        btnSignIn.setOnClickListener(view -> {
            pickUserAccount();
        });
    }

    private void pickUserAccount() {
        Intent intent = AccountManager.newChooseAccountIntent(null, null,
                new String[]{"com.google"}, null, null, null, null);
        startActivityForResult(intent, REQUEST_ACCOUNT_PICKER);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ACCOUNT_PICKER && resultCode == this.RESULT_OK && data != null) {
            String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
            if (accountName != null) {
                try {
                    fetchCalendarEvents(accountName);
                } catch (GeneralSecurityException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void fetchCalendarEvents(String accountName) throws GeneralSecurityException, IOException {
        // Set up the Calendar API service
        Calendar service = CalendarUtils.getCalendarService(this, accountName);

        // Fetch events asynchronously
        new Thread(() -> {
            try {
                com.google.api.client.util.DateTime now = new com.google.api.client.util.DateTime(System.currentTimeMillis());
                Events events = service.events().list("primary")
                        .setMaxResults(10)
                        .setTimeMin(now)
                        .setTimeMax(getEndOfDay(now))
                        .setOrderBy("startTime")
                        .setSingleEvents(true)
                        .execute();
                calendarEvents = events.getItems();
                // Update UI on the main thread
                this.runOnUiThread(() -> {

                    StringBuilder eventsText = new StringBuilder("Upcoming Events:\n\n");
                    if (events.getItems().size() > 0) {
                        for (Event event : calendarEvents) {
                            eventsText.append("Event: " + event.getSummary()).append("\n");

                        }
                    }else{
                        eventsText.append("No events found").append("\n");
                    }
                    calendarEventsTextView.setText(eventsText.toString());
                });
            }
            catch (UserRecoverableAuthIOException e) {
                startActivityForResult(e.getIntent(), REQUEST_ACCOUNT_PICKER);
            }catch (IOException e) {
                Log.e("TAG", "Error fetching calendar events", e);
            }
        }).start();
    }

    private com.google.api.client.util.DateTime getEndOfDay(com.google.api.client.util.DateTime startOfDay) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(new java.util.Date(startOfDay.getValue()));
        cal.set(java.util.Calendar.HOUR_OF_DAY, 23);
        cal.set(java.util.Calendar.MINUTE, 59);
        cal.set(java.util.Calendar.SECOND, 59);
        return new com.google.api.client.util.DateTime(cal.getTimeInMillis());
    }
    private boolean hasUsageAccessPermission() {
        try {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            long time = System.currentTimeMillis();
            List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 3600, time);
            return stats != null && !stats.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private void requestUsageAccessPermission() {
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        startActivity(intent);
    }

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                detectDriving();
            } else {
                Toast.makeText(this, "Location permission is required to detect driving", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void detectDriving() {
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 10, new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    float speed = location.getSpeed(); // speed in meters/second
                    float speedKmH = (speed * 3600) / 1000; // convert to km/h
                    isDriving = speedKmH > 10; // Assume driving if speed > 10 km/h
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {
                }

                @Override
                public void onProviderEnabled(String provider) {
                }

                @Override
                public void onProviderDisabled(String provider) {
                }
            });
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

//    private void detectCycling() {
//        // Cycling detection handled by Google Activity Recognition API
//        // The isCycling variable will be updated when an activity transition event is received
//    }
//
//    private void detectRunning() {
//        // Running detection handled by Google Activity Recognition API
//        // The isRunning variable will be updated when an activity transition event is received
//    }

    private void checkIndoorOutdoor() {
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 10, new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    float accuracy = location.getAccuracy(); // accuracy in meters
                    if (accuracy < 20) {
//                        indoorOutdoorStatus.setText("Outdoor");
                        locationManager.registerGnssStatusCallback(new GnssStatus.Callback() {

                            @Override
                            public void onStarted() {
                                super.onStarted();
                            }

                            @Override
                            public void onStopped() {
                                super.onStopped();
                            }

                            @Override
                            public void onFirstFix(int ttffMillis) {
                                super.onFirstFix(ttffMillis);
                            }

                            @Override
                            public void onSatelliteStatusChanged(@NonNull GnssStatus status) {
                                super.onSatelliteStatusChanged(status);

                                // Count the number of satellites that have a valid signal-to-noise ratio
                                int visibleSatellites = 0;


                                for (int i = 0; i < status.getSatelliteCount(); i++) {
                                    if (status.getCn0DbHz(i) > 30) {
                                        visibleSatellites++;
                                    }
                                }
                                // Check if there are 4 or more satellites visible
                                if (visibleSatellites > 7) {
                                    indoorOutdoorStatus.setText("Outdoor (Satellite Count)" + visibleSatellites);
                                } else {
                                    indoorOutdoorStatus.setText("Indoor (Satellite Count)" + visibleSatellites);
//                                    checkAndActivateDndMode();
//                                    checkAndActivateBedtimeMode();

                                }
                            }
                        });
                    } else {
                        indoorOutdoorStatus.setText("Indoor");
                    }
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {
                }

                @Override
                public void onProviderEnabled(String provider) {
                }

                @Override
                public void onProviderDisabled(String provider) {
                }
            });
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }


    private void learnUserBehavior() {
        long currentTime = System.currentTimeMillis();
        List<UsageStats> usageStatsList = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY,
                currentTime - 1000 * 3600 * 24, currentTime);

        if (usageStatsList != null) {
            for (UsageStats usageStats : usageStatsList) {
                String packageName = usageStats.getPackageName();
                long totalTimeInForeground = usageStats.getTotalTimeInForeground();
                // Assuming that user uses an app for more than 5 minutes while driving
                if (totalTimeInForeground > 1000 * 60 * 5) {
                    allowedDrivingApps.add(packageName);
                }
            }
        }
    }

    private boolean isSleeping() {

        java.util.Calendar calendar = java.util.Calendar.getInstance();
        int hour = calendar.HOUR_OF_DAY;

        if (hour >= 22 || hour < 6) { // Assuming nighttime is between 10 PM and 6 AM
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager != null && !powerManager.isInteractive()) {
                return true;
                // If it's nighttime and the device is not being used (inactivity detected)
//                setDoNotDisturbMode(true); // Enable DND Mode
//                setDarkTheme(true); // Enable Dark Theme
//                setBrightnessLevel(0.1f); // Set brightness to a minimum level
            }
        }
        return false;
    }

    private boolean isInMeeting() {
        // Assuming that a meeting is determined by checking the Google Calendar events
        if (calendarEvents != null) {
            Date currentTime = new Date();
            for (Event event : calendarEvents) {
                Date eventStart = new Date(event.getStart().getDateTime().getValue());
                Date eventEnd = new Date(event.getEnd().getDateTime().getValue());
                if (currentTime.after(eventStart) && currentTime.before(eventEnd)) {
                    // If there is a current ongoing meeting event, turn on DND mode
                    return true;
//                    setFocusMode(true);
//                    setDoNotDisturbMode(true);
//                    setBrightnessLevel(0.2f);
                }
            }
        }
        return false;
    }

    private void restrictApps() {
//         Comment below if want rest code to execute..
                if (!isDriving && !isCycling && !isRunning && !isSleeping && !isMeeting) {
                    return;
                }

        // This is a simplified way to restrict apps - in a real implementation,
        // you might use DevicePolicyManager or AccessibilityService.
        PackageManager pm = getPackageManager();
        List<String> installedApps = new ArrayList<>();
        List<android.content.pm.ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        for (android.content.pm.ApplicationInfo packageInfo : packages) {

            if ((packageInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0){
                installedApps.add(packageInfo.packageName);
            }

        }

        List<String> restrictedApps = new ArrayList<>(installedApps);
        if (isDriving) {
            List<String> allowedApps = new ArrayList<>();
            allowedApps.add("com.google.android.apps.maps"); // Google Maps
            allowedApps.add("com.spotify.music"); // Spotify
            allowedApps.add("com.google.android.music"); // Google Play Music
            allowedApps.add("com.apple.android.music"); // Apple Music
            allowedApps.add("com.soundcloud.android"); // SoundCloud
            allowedApps.add("com.pandora.android"); // Pandora
            allowedApps.add("com.amazon.mp3"); // Amazon Music
            restrictedApps.removeAll(allowedApps);
            restrictedApps.removeAll(allowedDrivingApps);
        }
        if (isCycling) {
            restrictedApps.removeAll(allowedCyclingApps);
        }
        if (isRunning) {
            restrictedApps.removeAll(allowedRunningApps);
        }

        if (isSleeping()) {
            restrictedApps.addAll(installedApps); // Block all apps during sleeping
        }
        if (isInMeeting()) {
            restrictedApps.addAll(installedApps); // Block all apps during meeting
        }

        saveRestrictedApps(restrictedApps);
        for (String packageName : restrictedApps) {
            // Here, we would ideally restrict the app usage.
            // This part of the implementation requires AccessibilityService or DevicePolicyManager.
            Toast.makeText(this, "Restricting app: " + packageName, Toast.LENGTH_SHORT).show();

            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                StatusBarNotification[] activeNotifications = notificationManager.getActiveNotifications();
                for (StatusBarNotification notification : activeNotifications) {
                    if (notification.getPackageName().equals(packageName)) {
                        notificationManager.cancel(notification.getId());
                    }
                }
            }
        }
    }

    private void saveRestrictedApps(List<String> restrictedApps) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();

        for (String packageName : restrictedApps) {
            editor.putBoolean(packageName, false);
        }
        editor.apply();
    }

    private void showAddAppDialog() {
        final PackageManager pm = getPackageManager();
        List<android.content.pm.ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        final List<String> installedApps = new ArrayList<>();

        for (android.content.pm.ApplicationInfo packageInfo : packages) {

            if ((packageInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0){
                installedApps.add(packageInfo.packageName);
            }
        }


        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select an app to allow");
        builder.setItems(installedApps.toArray(new String[0]), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String selectedApp = installedApps.get(which);
                showAddAppToCategoryDialog(selectedApp);
            }
        });
        builder.show();
    }

    private void showAddAppToCategoryDialog(final String selectedApp) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select category to add app");
        builder.setItems(new String[]{"Driving", "Cycling", "Running", "Meeting", "Sleeping"}, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        if (!allowedDrivingApps.contains(selectedApp)) {
                            allowedDrivingApps.add(selectedApp);
                            allowedDrivingAppsAdapter.notifyDataSetChanged();
                        }
                        break;
                    case 1:
                        if (!allowedCyclingApps.contains(selectedApp)) {
                            allowedCyclingApps.add(selectedApp);
                            allowedCyclingAppsAdapter.notifyDataSetChanged();
                        }
                        break;
                    case 2:
                        if (!allowedRunningApps.contains(selectedApp)) {
                            allowedRunningApps.add(selectedApp);
                            allowedRunningAppsAdapter.notifyDataSetChanged();
                        }
                        break;

                    case 3:
                        if (!allowedSleepingApps.contains(selectedApp)) {
                            allowedSleepingApps.add(selectedApp);
                            allowedSleepingAppsAdapter.notifyDataSetChanged();
                        }
                        break;

                    case 4:
                        if (!allowedMeetingApps.contains(selectedApp)) {
                            allowedMeetingApps.add(selectedApp);
                            allowedMeetingAppsAdapter.notifyDataSetChanged();
                        }
                        break;
                }
            }
        });
        builder.show();
    }

    private void requestActivityRecognitionUpdates() {
        ActivityTransitionRequest request = new ActivityTransitionRequest(createActivityTransitions());

        PendingIntent pendingIntent = PendingIntent.getService(
                this,
                0,
                new Intent(this, ActivityRecognitionService.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        activityRecognitionClient.requestActivityTransitionUpdates(request, pendingIntent)
                .addOnSuccessListener(aVoid -> Toast.makeText(MainActivity.this, "Activity recognition updates requested", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Failed to request activity recognition updates", Toast.LENGTH_SHORT).show());
    }

    private List<ActivityTransition> createActivityTransitions() {
        List<ActivityTransition> transitions = new ArrayList<>();

        transitions.add(new ActivityTransition.Builder()
                .setActivityType(DetectedActivity.ON_BICYCLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build());

        transitions.add(new ActivityTransition.Builder()
                .setActivityType(DetectedActivity.ON_BICYCLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build());

        transitions.add(new ActivityTransition.Builder()
                .setActivityType(DetectedActivity.RUNNING)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build());

        transitions.add(new ActivityTransition.Builder()
                .setActivityType(DetectedActivity.RUNNING)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build());

        return transitions;
    }

    public static class ActivityRecognitionService extends Service {

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            if (ActivityTransitionResult.hasResult(intent)) {
                ActivityTransitionResult result = ActivityTransitionResult.extractResult(intent);
                if (result != null) {
                    for (ActivityTransitionEvent event : result.getTransitionEvents()) {
                        int activityType = event.getActivityType();
                        int transitionType = event.getTransitionType();

                        if (activityType == DetectedActivity.ON_BICYCLE) {
                            if (transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                                MainActivity.isCycling = true;
                            } else if (transitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT) {
                                MainActivity.isCycling = false;
                            }
                        } else if (activityType == DetectedActivity.RUNNING) {
                            if (transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                                MainActivity.isRunning = true;
                            } else if (transitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT) {
                                MainActivity.isRunning = false;
                            }
                        }
                    }
                }
            }
            return START_STICKY;
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }
    }
}
