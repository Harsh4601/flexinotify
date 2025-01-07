package com.flexinotify;

import android.content.Context;

import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

public class CalendarUtils {

    public static Calendar getCalendarService(Context context, String accountName) throws GeneralSecurityException, IOException {
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                        context, Collections.singleton(CalendarScopes.CALENDAR))
                .setBackOff(new ExponentialBackOff())
                .setSelectedAccountName(accountName);

        return new Calendar.Builder(
                com.google.api.client.googleapis.javanet.GoogleNetHttpTransport.newTrustedTransport(),
                com.google.api.client.json.gson.GsonFactory.getDefaultInstance(),
                credential)
                .setApplicationName("Activity Recognition Calendar")
                .build();
    }
}
