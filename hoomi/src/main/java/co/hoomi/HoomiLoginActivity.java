/*
 * Copyright (c) 2015. Hoomi, Inc. All Rights Reserved
 */

package co.hoomi;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import bolts.Continuation;
import bolts.Task;

/**
 * A custom activity that picks up completion of Hoomi authorization requests.
 *
 * You must declare this activity (along with the appropriate scheme/data path)
 * in your AndroidManifest.xml so that it can intercept login attempts.
 */
public class HoomiLoginActivity extends Activity {
  private static final Map<String, Task<HoomiAccessToken>.TaskCompletionSource> pendingTasks =
      new HashMap<String, Task<HoomiAccessToken>.TaskCompletionSource>();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Intent intent = getIntent();
    Uri data = intent.getData();
    String code = data.getQueryParameter("code");
    String state = data.getQueryParameter("state");
    String error = data.getQueryParameter("error");
    String errorDescription = data.getQueryParameter("error_description");
    String errorUri = data.getQueryParameter("error_uri");
    completeLogin(state, code, error, errorDescription, errorUri);
    finish();
  }

  static void registerLoginRequest(Context context,
                                   String state,
                                   String redirectUri,
                                   String clientId,
                                   String clientSecret,
                                   Task<HoomiAccessToken>.TaskCompletionSource tcs) {
    synchronized (pendingTasks) {
      pendingTasks.put(state, tcs);
    }
    SharedPreferences prefs = context.getSharedPreferences("co.hoomi.HoomiLoginActivity",
        Context.MODE_PRIVATE);
    prefs.edit()
        .putString("state: " + state,
            HoomiClient.buildJSONObject("clientId", clientId,
                "clientSecret", clientSecret,
                "redirectUri", redirectUri).toString())
        .apply();
  }

  private void completeLogin(String state,
                             String code,
                             String error,
                             String errorDescription,
                             String errorUri) {
    SharedPreferences prefs = this.getSharedPreferences("co.hoomi.HoomiLoginActivity",
        Context.MODE_PRIVATE);
    if (!prefs.contains("state: " + state)) {
      return;
    }
    String clientId = null;
    String clientSecret = null;
    String redirectUri = null;
    try {
      JSONObject obj = new JSONObject(prefs.getString("state: " + state, null));
      clientId = obj.getString("clientId");
      clientSecret = obj.optString("clientSecret");
      redirectUri = obj.getString("redirectUri");
      prefs.edit().remove("state: " + state).apply();
    } catch (JSONException e) {
      // This shouldn't happen.
      throw new RuntimeException(e);
    }
    final Task<HoomiAccessToken>.TaskCompletionSource tcs;
    synchronized (pendingTasks) {
      tcs = pendingTasks.remove(state);
    }
    if (error != null) {
      if (tcs != null) {
        String errorMessage = error;
        if (errorDescription != null) {
          errorMessage += " - " + errorDescription;
        }
        if (errorUri != null) {
          errorMessage += " (" + errorUri + ")";
        }
        tcs.setError(new HoomiException(errorMessage));
      }
      return;
    }
    JSONObject parameters = HoomiClient.buildJSONObject("client_id", clientId,
        "grant_type", "authorization_code",
        "code", code,
        "redirect_uri", redirectUri);
    if (clientSecret != null) {
      try {
        parameters.put("client_secret", clientSecret);
      } catch (JSONException e) {
        // This can't happen.
      }
    }
    HoomiClient.getCurrentClient().requestAsync("1/authz/token",
        "POST",
        null,
        parameters,
        true).continueWith(new Continuation<HoomiClient.ApiResponse, Void>() {
      @Override
      public Void then(Task<HoomiClient.ApiResponse> task) throws Exception {
        if (tcs != null && task.isFaulted()) {
          tcs.trySetError(task.getError());
        }
        HoomiAccessToken token = new HoomiAccessToken(task.getResult()
            .getJsonData()
            .getString("access_token"),
            Arrays.asList(task.getResult().getJsonData().getString("scope").split(" ")),
            new Date(new Date().getTime() +
                task.getResult().getJsonData().getLong("expires_in") * 1000));
        HoomiClient.getCurrentClient().setCurrentToken(token);
        if (tcs != null) {
          tcs.trySetResult(token);
        }
        return null;
      }
    });
  }
}
