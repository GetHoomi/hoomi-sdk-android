/*
 * Copyright (c) 2015. Hoomi, Inc. All Rights Reserved
 */

package co.hoomi;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.GooglePlayServicesAvailabilityException;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import bolts.Continuation;
import bolts.Task;
import co.hoomi.response.AppData;
import co.hoomi.response.TokenInformation;

/**
 * The main entry point for working with Hoomi.
 */
public class HoomiClient {
  private static final String BASE_API_URL = "https://api.hoomi.co/";
  private static final String BASE_DIALOG_URL = "https://dialog.hoomi.co/";
  private static final String BASE_APP_URL = "hoomi://hoomi/";

  class ApiResponse {
    private final JSONObject jsonData;
    private final Map<String, List<String>> headers;

    public ApiResponse(JSONObject jsonData, Map<String, List<String>> headers) {
      this.jsonData = jsonData;
      this.headers = headers;
    }

    public JSONObject getJsonData() {
      return jsonData;
    }

    public Map<String, List<String>> getHeaders() {
      return headers;
    }
  }

  private static HoomiClient currentClient;

  private Context context;
  private String applicationId;
  private HoomiAccessToken currentToken;
  private Task<JSONObject> clientIdTask;
  private String webGoogleClientId;

  /**
   * Creates a HoomiClient with the given application ID from Hoomi.
   *
   * @param context       an Android context
   * @param applicationId the application ID that this client will use
   */
  public HoomiClient(Context context, String applicationId) {
    this.context = context.getApplicationContext();
    this.applicationId = applicationId;
    if (currentClient == null) {
      currentClient = this;
    }
    provisionClientIdAsync();
  }

  /**
   * Gets the Web Google Client ID used for client authentication.
   *
   * @return the client ID
   */
  public String getWebGoogleClientId() {
    return webGoogleClientId;
  }

  /**
   * Sets the Web Google Client ID used for client authentication.
   *
   * @param webGoogleClientId the client ID
   */
  public void setWebGoogleClientId(String webGoogleClientId) {
    this.webGoogleClientId = webGoogleClientId;
  }

  private Task<JSONObject> provisionClientIdAsync() {
    if (clientIdTask == null) {
      try {
        String cachedIdJson = getSharedPreferences().getString("cachedClientId", null);
        if (cachedIdJson != null) {
          JSONObject cachedId = new JSONObject(cachedIdJson);
          clientIdTask = Task.forResult(cachedId);
        }
      } catch (JSONException e) {
        // Ignore this error -- we'll just try to provision a fresh one.
      }
      if (clientIdTask == null) {
        clientIdTask = Task.forResult(null);
      }
    }
    clientIdTask = clientIdTask.continueWithTask(new Continuation<JSONObject, Task<JSONObject>>() {
      @Override
      public Task<JSONObject> then(Task<JSONObject> task) throws Exception {
        JSONObject current = task.getResult();
        if (current != null && current.optLong("expires", 0) > new Date().getTime()) {
          if (getWebGoogleClientId() != null && !current.has("client_secret")) {
            // We should try again to authenticate the client
          } else {
            return task;
          }
        }
        Continuation<ApiResponse, JSONObject> continuation = new Continuation<ApiResponse, JSONObject>() {
          @Override
          public JSONObject then(Task<ApiResponse> task) throws Exception {
            JSONObject copy = new JSONObject(task.getResult().getJsonData().toString());
            long expiresIn = task.getResult().getJsonData().optLong("expires_in", 60 * 60);
            // Set the expiry back by an hour to be conservative.
            expiresIn -= 60 * 60;
            copy.put("expires", new Date().getTime() + 1000 * expiresIn);
            getSharedPreferences().edit().putString("cachedClientId", copy.toString()).apply();
            return copy;
          }
        };
        if (getWebGoogleClientId() != null) {
          try {
            // If there's a google account on the device, attempt client authentication
            AccountManager accountManager = AccountManager.get(context);
            Account[] accounts = accountManager.getAccountsByType("com.google");
            if (accounts != null && accounts.length > 0) {
              String jwt = GoogleAuthUtil.getToken(context,
                  accounts[0].name,
                  "audience:server:client_id:" + getWebGoogleClientId());
              return requestAsync("1/authz/provision_android_client",
                  "POST",
                  null,
                  buildJSONObject("application_id", applicationId, "token", jwt))
                  .onSuccess(continuation);
            }
          } catch (GooglePlayServicesAvailabilityException e) {
            // Ignore these exceptions if Google Play Services are not available on the device
          }
        }
        return requestAsync("1/authz/provision_client",
            "POST",
            null,
            buildJSONObject("application_id", applicationId))
            .onSuccess(continuation);
      }
    }, Task.BACKGROUND_EXECUTOR);
    return clientIdTask;
  }

  private String serializeScopes(List<String> scopes) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < scopes.size(); i++) {
      b.append(scopes.get(i));
      if (i != scopes.size() - 1) {
        b.append(' ');
      }
    }
    return b.toString();
  }

  private void addLoginParameters(Uri.Builder builder,
                                  String clientId,
                                  String state,
                                  Uri redirectUri,
                                  List<String> scopes) {
    builder.appendPath("login")
        .appendPath("auth")
        .appendQueryParameter("platform", "android")
        .appendQueryParameter("client_id", clientId)
        .appendQueryParameter("response_type", "code")
        .appendQueryParameter("state", state)
        .appendQueryParameter("redirect_uri", redirectUri.toString());
    if (scopes != null) {
      builder.appendQueryParameter("scope", serializeScopes(scopes));
    }
  }

  /**
   * Begins the process of authorizing with Hoomi using the given redirect URL and scopes.
   *
   * @param redirectUri the redirect URL to use to return to your app
   * @param scopes      the set of scopes to request access to
   * @return a HoomiAccessToken (asynchronously)
   */
  public Task<HoomiAccessToken> authorizeAsync(final Uri redirectUri, final List<String> scopes) {
    final Task<HoomiAccessToken>.TaskCompletionSource tcs = Task.create();
    provisionClientIdAsync().onSuccess(new Continuation<JSONObject, Void>() {
      @Override
      public Void then(Task<JSONObject> task) throws Exception {
        String state = UUID.randomUUID().toString();
        String clientId = task.getResult().getString("client_id");
        String clientSecret = task.getResult().optString("client_secret");
        Uri.Builder toOpenWeb = Uri.parse(BASE_DIALOG_URL).buildUpon();
        Uri.Builder toOpenNative = Uri.parse(BASE_APP_URL).buildUpon();
        addLoginParameters(toOpenWeb, clientId, state, redirectUri, scopes);
        addLoginParameters(toOpenNative, clientId, state, redirectUri, scopes);
        HoomiLoginActivity.registerLoginRequest(context,
            state,
            redirectUri.toString(),
            clientId,
            clientSecret,
            tcs);
        Intent webAuthorizeIntent = new Intent(Intent.ACTION_VIEW, toOpenWeb.build());
        Intent appAuthorizeIntent = new Intent(Intent.ACTION_VIEW, toOpenNative.build());
        Intent authorizeIntent;
        appAuthorizeIntent.setPackage("co.hoomi");
        if (context.getPackageManager().resolveActivity(appAuthorizeIntent, 0) != null) {
          authorizeIntent = appAuthorizeIntent;
        } else {
          authorizeIntent = webAuthorizeIntent;
        }
        authorizeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(authorizeIntent);
        return null;
      }
    });
    return tcs.getTask();
  }

  private Date parseIso8601Date(String date) throws ParseException {
    SimpleDateFormat decimalDf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSSSZ", Locale.getDefault());
    SimpleDateFormat nonDecimalDf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
    try {
      return decimalDf.parse(date);
    } catch (ParseException e) {
      return nonDecimalDf.parse(date);
    }
  }

  /**
   * Gets token information for the given Hoomi access token.
   *
   * @param token the token to fetch information for
   * @return TokenInformation (asynchronously)
   */
  public Task<TokenInformation> getTokenInformationAsync(HoomiAccessToken token) {
    return requestAsync("1/token/current", "GET", token, null)
        .onSuccess(new Continuation<ApiResponse, TokenInformation>() {
          @Override
          public TokenInformation then(Task<ApiResponse> task) throws Exception {
            String tokenString = task.getResult().getJsonData().getString("token");
            String applicationId = task.getResult().getJsonData().getString("application_id");
            Date issued = parseIso8601Date(task.getResult().getJsonData().getString("issued"));
            Date expires = parseIso8601Date(task.getResult().getJsonData().getString("expires"));
            boolean issuedToAuthenticatedClient = task.getResult().getJsonData()
                .optBoolean("issued_to_authenticated_client", false);
            String userId = task.getResult().getJsonData().optString("user_id");
            JSONArray scopesArray = task.getResult().getJsonData().getJSONArray("scopes");
            ArrayList<String> scopes = new ArrayList<String>();
            for (int i = 0; i < scopesArray.length(); i++) {
              scopes.add(scopesArray.getString(i));
            }
            HoomiAccessToken token = new HoomiAccessToken(tokenString, scopes, expires);
            HoomiAccessToken currentToken = getCurrentToken();
            // If this is already the current token, we'd might as well store
            // the latest data locally.
            if (currentToken != null &&
                applicationId.equals(HoomiClient.this.applicationId) &&
                token.getTokenString().equals(currentToken.getTokenString())) {
              setCurrentToken(token);
            }
            return new TokenInformation(token,
                applicationId,
                issued,
                userId,
                issuedToAuthenticatedClient);
          }
        });
  }

  /**
   * Gets the app data for the current user (the current token must have the
   * user:app:data:read scope).
   *
   * @return AppData (asynchronously)
   */
  public Task<AppData> getAppDataAsync() {
    return getAppDataAsync(getCurrentToken());
  }

  /**
   * Gets the app data for a user.
   *
   * @param token the access token (which must have the user:app:data:read scope) for the user
   * @return AppData (asynchronously)
   */
  public Task<AppData> getAppDataAsync(HoomiAccessToken token) {
    return requestAsync("1/user/current/app/data", "GET", token, null)
        .onSuccess(new Continuation<ApiResponse, AppData>() {
          @Override
          public AppData then(Task<ApiResponse> task) throws Exception {
            return new AppData(task.getResult().getJsonData().getJSONObject("data"),
                task.getResult().getHeaders().get("ETag").get(0));
          }
        });
  }

  /**
   * Sets the app data for the current user (the current token must have the
   * user:app:data:write scope).
   *
   * @param json the new data to associate with the user
   * @return the new AppData (asynchronously)
   */
  public Task<AppData> setAppDataAsync(JSONObject json) {
    return setAppDataAsync(getCurrentToken(), json, "*");
  }

  /**
   * Sets the app data for the current user (the current token must have the
   * user:app:data:write scope)
   *
   * @param json the new data to associate with the user
   * @param eTag an ETag to be used for optimistic concurrency control.  Set to "*"
   *             to ignore the ETag.
   * @return the new AppData (asynchronously)
   */
  public Task<AppData> setAppDataAsync(JSONObject json, String eTag) {
    return setAppDataAsync(getCurrentToken(), json, eTag);
  }

  /**
   * Sets the app data for the user with the given token.
   *
   * @param token the access token (which must have the user:app:data:write scope) for the user
   * @param json  the new data to associate with the user
   * @return the new AppData (asynchronously)
   */
  public Task<AppData> setAppDataAsync(HoomiAccessToken token, JSONObject json) {
    return setAppDataAsync(token, json, "*");
  }

  /**
   * Sets the app data for the user with the given token.
   *
   * @param token the access token (which must have the user:app:data:write scope) for the user
   * @param json  the new data to associate with the user
   * @param eTag  an ETag to be used for optimistic concurrency control.  Set to "*"
   *              to ignore the ETag.
   * @return thew new AppData (asynchronously)
   */
  public Task<AppData> setAppDataAsync(HoomiAccessToken token, final JSONObject json, String eTag) {
    Map<String, List<String>> headers = new HashMap<String, List<String>>();
    headers.put("If-Match", new ArrayList<String>());
    headers.get("If-Match").add(eTag);
    return requestAsync("1/user/current/app/data", "PUT", token, json, false, headers)
        .onSuccess(new Continuation<ApiResponse, AppData>() {
          @Override
          public AppData then(Task<ApiResponse> task) throws Exception {
            return new AppData(json,
                task.getResult().getHeaders().get("ETag").get(0));
          }
        });
  }

  private SharedPreferences getSharedPreferences() {
    return context.getSharedPreferences("co.hoomi.HoomiClient|" + applicationId,
        Context.MODE_PRIVATE);
  }

  /**
   * Gets the current access token for this Hoomi client. This value is automatically
   * set after authorization completes and is cached locally between runs of the
   * application.
   *
   * @return the current HoomiAccessToken
   */
  public HoomiAccessToken getCurrentToken() {
    if (currentToken == null) {
      SharedPreferences prefs = getSharedPreferences();
      currentToken = HoomiAccessToken.deserialize(prefs.getString("currentToken", null));
    }
    return currentToken;
  }

  /**
   * Sets the current access token for this Hoomi client. This value is automatically
   * set after authorization completes and is cached locally between runs of the
   * application.
   *
   * @param token the new current token
   */
  public void setCurrentToken(HoomiAccessToken token) {
    SharedPreferences prefs = getSharedPreferences();
    if (token != null) {
      prefs.edit().putString("currentToken", token.serialize()).apply();
    } else {
      prefs.edit().remove("currentToken").apply();
    }
    currentToken = token;
  }

  /**
   * Clears the current access token.
   */
  public void logOut() {
    setCurrentToken(null);
  }

  /**
   * Gets the current HoomiClient. This value is automatically initialized
   * with the first client you create.
   *
   * @return the current HoomiClient
   */
  public static HoomiClient getCurrentClient() {
    return currentClient;
  }

  /**
   * Sets the current HoomiClient. This value is automatically initialized
   * with the first client you create.
   *
   * @param client the new current HoomiClient
   */
  public static void setCurrentClient(HoomiClient client) {
    currentClient = client;
  }

  static JSONObject buildJSONObject(Object... parameters) {
    JSONObject result = new JSONObject();
    try {
      for (int i = 0; i < parameters.length; i += 2) {
        result.put(parameters[i].toString(), parameters[i + 1]);
      }
    } catch (JSONException e) {
      // This can't actually happen.
      throw new RuntimeException(e);
    }
    return result;
  }

  Task<ApiResponse> requestAsync(final String path,
                                 final String method,
                                 final HoomiAccessToken token,
                                 final JSONObject parameters) {
    return requestAsync(path, method, token, parameters, false);
  }

  Task<ApiResponse> requestAsync(final String path,
                                 final String method,
                                 final HoomiAccessToken token,
                                 final JSONObject parameters,
                                 final boolean useFormEncoding) {
    return requestAsync(path, method, token, parameters, useFormEncoding, null);
  }

  Task<ApiResponse> requestAsync(final String path,
                                 final String method,
                                 final HoomiAccessToken token,
                                 final JSONObject parameters,
                                 final boolean useFormEncoding,
                                 final Map<String, List<String>> extraHeaders) {
    return Task.callInBackground(new Callable<ApiResponse>() {
      @Override
      public ApiResponse call() throws Exception {
        Uri.Builder builder = Uri.parse(BASE_API_URL + path)
            .buildUpon();
        if (method.equals("GET") && parameters != null) {
          Iterator<String> keys = parameters.keys();
          while (keys.hasNext()) {
            String key = keys.next();
            builder.appendQueryParameter(key, parameters.get(key).toString());
          }
        }
        URL url = new URL(builder.build().toString());
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);

        if (extraHeaders != null) {
          for (String key : extraHeaders.keySet()) {
            for (String value : extraHeaders.get(key)) {
              connection.addRequestProperty(key, value);
            }
          }
        }

        if (token != null) {
          connection.setRequestProperty("Authorization", "Bearer " + token.getTokenString());
        }

        if (!method.equals("GET") && parameters != null) {
          String body;
          if (!useFormEncoding) {
            connection.setRequestProperty("Content-Type", "application/json");
            body = parameters.toString();
          } else {
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            StringBuilder sb = new StringBuilder();
            Iterator<String> keys = parameters.keys();
            while (keys.hasNext()) {
              String key = keys.next();
              sb.append(URLEncoder.encode(key, "UTF-8"));
              sb.append("=");
              sb.append(URLEncoder.encode(parameters.get(key).toString(), "UTF-8"));
              if (keys.hasNext()) {
                sb.append("&");
              }
            }
            body = sb.toString();
          }
          OutputStream output = connection.getOutputStream();
          OutputStreamWriter writer = new OutputStreamWriter(output);
          writer.write(body);
          writer.close();
        }

        InputStream input = connection.getInputStream();
        String result = IOUtils.toString(input);

        if (connection.getResponseCode() < 200 || connection.getResponseCode() > 399) {
          throw new HoomiException("HTTP Error: " + connection.getResponseCode() +
              " " + connection.getResponseMessage());
        }
        return new ApiResponse(new JSONObject(result), connection.getHeaderFields());
      }
    });
  }
}
