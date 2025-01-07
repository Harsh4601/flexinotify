package com.flexinotify;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityEvent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.Toast;

public class AppRestrictionAccessibilityService extends AccessibilityService {

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";

            // Check if the current app is restricted
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            boolean isRestricted = prefs.getBoolean(packageName, false);

            if (isRestricted) {
                // Display a toast message and navigate away from the app
                Toast.makeText(this, "App usage is restricted while driving.", Toast.LENGTH_SHORT).show();
                performGlobalAction(GLOBAL_ACTION_HOME); // Redirect to the home screen
            }
        }
    }

    @Override
    public void onInterrupt() {
        // This method is required by the AccessibilityService, but we don't need to implement anything here.
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_VISUAL;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        info.notificationTimeout = 100;
        setServiceInfo(info);
    }
}
