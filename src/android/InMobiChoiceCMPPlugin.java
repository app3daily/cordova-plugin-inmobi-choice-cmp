/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
*/
package com.app3daily.inmobichoicecmp;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.iabtcf.decoder.TCString;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.inmobi.cmp.ChoiceCmp;
import com.inmobi.cmp.data.model.ChoiceStyle;
import com.inmobi.cmp.data.model.ThemeMode;
import com.inmobi.cmp.ChoiceCmpCallback;
import com.inmobi.cmp.core.model.ACData;
import com.inmobi.cmp.core.model.GDPRData;
import com.inmobi.cmp.core.model.gbc.GoogleBasicConsents;
import com.inmobi.cmp.core.model.mspa.USRegulationData;
import com.inmobi.cmp.model.ChoiceError;
import com.inmobi.cmp.model.DisplayInfo;
import com.inmobi.cmp.model.NonIABData;
import com.inmobi.cmp.model.PingReturn;

import java.util.Map;

public class InMobiChoiceCMPPlugin extends CordovaPlugin implements ChoiceCmpCallback{

    private static final String TAG     = "InMobiChoiceCMPPlugin";

    private boolean isPluginInitialized;

    private Activity getCurrentActivity() { return cordova.getActivity(); }

    public InMobiChoiceCMPPlugin() { }


    private void fireWindowEvent(final String name, final JSONObject params)
    {
        getCurrentActivity().runOnUiThread( () -> webView.loadUrl( "javascript:cordova.fireWindowEvent('" + name + "', " + params.toString() + ");" ) );
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException 
    {
        
        if ( "startChoice".equalsIgnoreCase( action ) )
        {
            String packageId = args.getString( 0 );
            String pCode = args.getString( 1 );
            initialize( packageId, pCode, callbackContext );
        }
        else if ("forceDisplayUI".equals(action)) {
            forceDisplayUI( callbackContext );
        }
        else if ("getConsentData".equals(action)) {
            getConsentDataFromPreference(callbackContext);
        }
        else if ("getDataFromPreference".equals(action)) {
            getDataFromPreference(args, callbackContext);
        }
        else
        {
            // Action not recognized
            return false;
        }
        return true;
    }

    private void initialize(final String packageId, final String pCode, final CallbackContext callbackContext) throws JSONException
    {
        Context context = cordova.getContext();

        // Check if Activity is available
        Activity currentActivity = getCurrentActivity();
        if ( currentActivity == null ) throw new IllegalStateException( "No Activity found" );

//        // Guard against running init logic multiple times
//        if ( isPluginInitialized )
//        {
//
//            callbackContext.success( getConsentData() );
//            return;
//        }

        d( "Initializing InMobiChoiceCMPPlugin Cordova ..." );

        // If package id passed in is empty, check Android Manifest
        String packageIdToUse = packageId;
        if ( TextUtils.isEmpty( packageId ) )
        {
            try
            {
                PackageManager packageManager = context.getPackageManager();
                String packageName = context.getPackageName();

                packageIdToUse = packageName;
            }
            catch ( Throwable th )
            {
                e( "Unable to retrieve Package id from Android Manifest: " + th );
            }

            if ( TextUtils.isEmpty( packageIdToUse ) )
            {
                throw new IllegalStateException( "Unable to initialize InMobiChoiceCMPPlugin SDK - no package id provided and not found in Android Manifest!" );
            }
        }

        if ( TextUtils.isEmpty( pCode ) )
        {
            throw new IllegalStateException( "Unable to initialize InMobiChoiceCMPPlugin SDK - no pCode provided!" );
        }
        // Initialize SDK
        ChoiceCmp.startChoice
        (
                (Application) currentActivity.getApplicationContext(),
                packageId,
                pCode,
                this,
                new ChoiceStyle.Builder().setThemeMode(ThemeMode.AUTO).build()
        );

        isPluginInitialized = true;

        callbackContext.success( getConsentData( ) );
        
    }

    public void getConsentDataFromPreference (final CallbackContext callbackContext) {
        callbackContext.success( getConsentData( ) );
    }

    private JSONObject getConsentData()
    {
        // https://github.com/InteractiveAdvertisingBureau/GDPR-Transparency-and-Consent-Framework/blob/master/TCFv2/IAB%20Tech%20Lab%20-%20CMP%20API%20v2.md#what-is-the-cmp-in-app-internal-structure-for-the-defined-api
        JSONObject message = new JSONObject();
        try {
            String tcString = getSharedPreference("IABTCF_TCString");
//            String tcString = getSharedPreference("IABTCF_TCString");
            TCString tcStringObj = TCString.decode(tcString);
            d("prateek");
            d(tcStringObj.toString());
            // Create ObjectMapper instance
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule((new JavaTimeModule()));
            // Convert TCString object to JSON string

            String jsonString = objectMapper.writeValueAsString(tcStringObj);
            d(jsonString);
            JSONObject tcJsonObject = new JSONObject(jsonString);
            message.put("tcString",tcJsonObject);
        } catch (Exception e) {
            logStackTrace(e);
        }
        return message;
    }

    public void getDataFromPreference (JSONArray keys, final CallbackContext callbackContext) {
        JSONObject result = new JSONObject();
        try {
            for (int i = 0; i < keys.length(); i++) {
                String key = keys.getString(i);
                String value = getSharedPreference(key);
                result.put(key, value);

            }
        } catch (JSONException e) {
            logStackTrace(e);
        }
        callbackContext.success(result);
    }

    private String getSharedPreference(String key) {
        Context context = cordova.getContext();
        String defaultSharedPreferenceName = PreferenceManager.getDefaultSharedPreferencesName(context);
        SharedPreferences sharedPref = context.getSharedPreferences(defaultSharedPreferenceName, Context.MODE_PRIVATE);
        String str1 = sharedPref.getString(key, "");
        return str1;
    }

    private boolean isInitialized()
    {
        return isPluginInitialized;
    }

    public void forceDisplayUI(final CallbackContext callbackContext)
    {
        Activity currentActivity = getCurrentActivity();
        ChoiceCmp.forceDisplayUI(currentActivity);

        callbackContext.success();
    }
    
    @Override
    public void onCCPAConsentGiven(String consentString) {
        i("Event onCCPAConsentGiven : " + consentString);
        String name = "onCCPAConsentGiven";
        try
        {
            JSONObject params = new JSONObject();
            params.put( "consentString", consentString );
            fireWindowEvent( name, params );
        }
        catch ( Throwable ignored ) { }
    }

    @Override
    public void onCmpError(ChoiceError error) {
        i("Event onCmpError :" + error.getMessage());
        String name = "onCmpError";
        try
        {
            JSONObject params = new JSONObject();
            params.put( "errorMessage", error.getMessage() );
            fireWindowEvent( name, params );
        }
        catch ( Throwable ignored ) { }
    }

    @Override
    public void onCmpLoaded(PingReturn info) {
        i("Event onCmpLoaded : ");
        String name = "onCmpLoaded";
        try
        {
            JSONObject params = new JSONObject();
            // todo create json object from pingReturn
            params.put( "info", info.toString() );
            fireWindowEvent( name, params );
        }
        catch ( Throwable ignored ) { }
    }

    @Override
    public void onCMPUIStatusChanged(DisplayInfo displayInfo) {
        i("Event onCMPUIStatusChanged : ");
        String name = "onCMPUIStatusChanged";
        try
        {
            JSONObject params = new JSONObject();
            // todo create json object from return object
            params.put( "info", displayInfo.toString() );
            fireWindowEvent( name, params );
        }
        catch ( Throwable ignored ) { }
    }

    @Override
    public void onGoogleBasicConsentChange(GoogleBasicConsents consents) {
        i("Event onGoogleBasicConsentChange : ");
        String name = "onGoogleBasicConsentChange";
        try
        {
            JSONObject params = new JSONObject();
            // todo create json object from return object
            params.put( "info", consents.toString() );
            fireWindowEvent( name, params );
        }
        catch ( Throwable ignored ) { }
    }

    @Override
    public void onGoogleVendorConsentGiven(ACData acData) {
        i("Event onGoogleVendorConsentGiven : ");
        String name = "onGoogleVendorConsentGiven";
        try
        {
            JSONObject params = new JSONObject();
            // todo create json object from return object
            params.put( "info", acData.toString() );
            fireWindowEvent( name, params );
        }
        catch ( Throwable ignored ) { }
    }

    @Override
    public void onIABVendorConsentGiven(GDPRData gdprData) {
        i("Event onIABVendorConsentGiven : ");
        String name = "onIABVendorConsentGiven";
        try
        {
            JSONObject params = new JSONObject();
            // todo create json object from return object
            params.put( "info", gdprData.toString() );
            fireWindowEvent( name, params );
        }
        catch ( Throwable ignored ) { }
    }

    @Override
    public void onNonIABVendorConsentGiven(NonIABData nonIABData) {
        i("Event onNonIABVendorConsentGiven : ");
        String name = "onNonIABVendorConsentGiven";
        try
        {
            JSONObject params = new JSONObject();
            // todo create json object from return object
            params.put( "info", nonIABData.toString() );
            fireWindowEvent( name, params );
        }
        catch ( Throwable ignored ) { }
    }

    @Override
    public void onReceiveUSRegulationsConsent(USRegulationData usRegulationData) {
        i("Event onReceiveUSRegulationsConsent : ");
        String name = "onReceiveUSRegulationsConsent";
        try
        {
            JSONObject params = new JSONObject();
            // todo create json object from return object
            params.put( "info", usRegulationData.toString() );
            fireWindowEvent( name, params );
        }
        catch ( Throwable ignored ) { }
    }

    @Override
    public void onUserMovedToOtherState() {
        i("Event onUserMovedToOtherState : ");
        String name = "onUserMovedToOtherState";
        try
        {
            JSONObject params = new JSONObject();
            fireWindowEvent( name, params );
        }
        catch ( Throwable ignored ) { }
    }

    private void logStackTrace(Exception e)
    {
        e( Log.getStackTraceString( e ) );
    }

    public static void i(final String message)
    {
        final String fullMessage = "[" + TAG + "] " + message;
        Log.i( TAG, fullMessage );
    }

    public static void d(final String message)
    {
        final String fullMessage = "[" + TAG + "] " + message;
        Log.d( TAG, fullMessage );
    }

    public static void e(final String message)
    {
        final String fullMessage = "[" + TAG + "] " + message;
        Log.e( TAG, fullMessage );
    }
}

