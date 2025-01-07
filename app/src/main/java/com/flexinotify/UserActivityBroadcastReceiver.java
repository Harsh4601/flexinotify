package com.flexinotify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

// Broadcast Receiver to handle activity updates
public class UserActivityBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.v("myApp","Activity update started");

        if (ActivityRecognitionResult.hasResult(intent)) {
            ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
            DetectedActivity detectedActivity = result.getMostProbableActivity();

            Intent broadcastIntent = new Intent("USER_ACTIVITY_UPDATE");
            broadcastIntent.putExtra("activity_type", detectedActivity.getType());
            broadcastIntent.putExtra("confidence", detectedActivity.getConfidence());
            context.sendBroadcast(broadcastIntent);
        }
    }
}
