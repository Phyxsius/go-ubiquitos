/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.sunshine.app.wearable;

import android.app.IntentService;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;
import com.example.android.sunshine.app.sync.SunshineSyncAdapter;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

/**
 * Created by andy on 11/11/15.
 */
public class WeatherWearService extends IntentService implements
    GoogleApiClient.ConnectionCallbacks,
    GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG                     = "WeatherWearService";
    public static final  String PATH_WEATHER_INFO       = "/SunshineWatchFace/WeatherInfo";

    GoogleApiClient mGoogleApiClient;

    private static final String[] FORECAST_COLUMNS = {
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_SHORT_DESC,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP
    };

    // these indices must match the projection
    private static final int INDEX_WEATHER_ID = 0;
    private static final int INDEX_MAX_TEMP = 2;
    private static final int INDEX_MIN_TEMP = 3;

    public WeatherWearService() {
        super("WeatherWearService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        boolean dataIsUpdated = (intent != null &&
                SunshineSyncAdapter.ACTION_DATA_UPDATED.equals(intent.getAction()));

        Log.d(TAG, "HandleIntent: " + intent.getAction());

        if (dataIsUpdated ) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();

            mGoogleApiClient.connect();
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        String message = null;
        //Requires a new thread to avoid blocking the UI

        Log.d(TAG, "Sending message to Wear");
        // Create a DataMap object and send it to the data layer
        // Get today's data from the ContentProvider


        String location = Utility.getPreferredLocation(this);
        Uri weatherForLocationUri = WeatherContract.WeatherEntry.buildWeatherLocationWithStartDate(
                location, System.currentTimeMillis());
        Cursor data = getContentResolver().query(weatherForLocationUri, FORECAST_COLUMNS, null,
                null, WeatherContract.WeatherEntry.COLUMN_DATE + " ASC");
        if (data == null) {
            return;
        }
        if (!data.moveToFirst()) {
            data.close();
            return;
        }

        // Extract the weather data from the Cursor
        int weatherId = data.getInt(INDEX_WEATHER_ID);
        double maxTemp = data.getDouble(INDEX_MAX_TEMP);
        double minTemp = data.getDouble(INDEX_MIN_TEMP);
        String formattedMaxTemperature = Utility.formatTemperature(this, maxTemp);
        String formattedMinTemperature = Utility.formatTemperature(this, minTemp);
        data.close();

        DataMap dataMap = new DataMap();

        dataMap.putString("maxTemp", formattedMaxTemperature);
        dataMap.putString("minTemp", formattedMinTemperature);
        dataMap.putString("weatherId", "" + weatherId);
        message=formattedMaxTemperature+";"+formattedMinTemperature+";"+weatherId;

        //Requires a new thread to avoid blocking the UI
        new SendMessageToDataLayerThread(PATH_WEATHER_INFO, message).start();
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.w("action","connection suspended message "+i);
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.w("action","connection suspended failed "+connectionResult.toString());
    }

    @Override
    public void onDestroy() {
        if (null != mGoogleApiClient && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
        super.onDestroy();
    }

    class SendMessageToDataLayerThread extends Thread {
        String path;
        String message;

        // Constructor to send a message to the data layer
        SendMessageToDataLayerThread(String p, String msg) {
            path = p;
            message = msg;
        }

        public void run() {
            NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();
            if (nodes != null && nodes.getNodes() != null) {
                for (Node node : nodes.getNodes()) {
                    MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage(mGoogleApiClient, node.getId(), path, message.getBytes()).await();
                    if (result.getStatus().isSuccess()) {
                        Log.v("myTag", "Message: {" + message + "} sent to: " + node.getDisplayName());
                    } else {
                        // Log an error
                        Log.v("myTag", "ERROR: failed to send Message");
                    }
                }
            }else{
                Log.v("myTag message", "ERROR: no nodes connected");
            }
        }
    }
}
