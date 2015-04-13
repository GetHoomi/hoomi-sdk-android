/*
 * Copyright (c) 2015. Hoomi, Inc.
 */

package co.hoomi.yesyoo;

import android.net.Uri;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;

import bolts.Continuation;
import bolts.Task;
import co.hoomi.HoomiException;

public class HackerNewsApi {
  private static final String BASE_API_URL = "https://hacker-news.firebaseio.com/";

  public static Task<JSONObject> getUserAsync(String username) {
    try {
      return requestAsync("v0/user/" + URLEncoder.encode(username, "UTF-8") + ".json", "GET", null);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static Task<JSONObject> getItemAsync(String item) {
    try {
      return requestAsync("v0/item/" + URLEncoder.encode(item, "UTF-8") + ".json", "GET", null);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static Task<List<JSONObject>> getTopStoriesAsync() {
    try {
      final ArrayList<Task<JSONObject>> fetches = new ArrayList<Task<JSONObject>>();
      return requestAsync("v0/topstories.json", "GET", null)
          .onSuccessTask(new Continuation<JSONObject, Task<Void>>() {
            @Override
            public Task<Void> then(Task<JSONObject> task) throws Exception {
              JSONArray results = task.getResult().getJSONArray("results");
              for (int i = 0; i < results.length() && i < 30; i++) {
                fetches.add(getItemAsync("" + results.getLong(i)));
              }
              return Task.whenAll(fetches);
            }
          }).onSuccess(new Continuation<Void, List<JSONObject>>() {
            @Override
            public List<JSONObject> then(Task<Void> _0) throws Exception {
              ArrayList<JSONObject> items = new ArrayList<JSONObject>();
              for (Task<JSONObject> task : fetches) {
                items.add(task.getResult());
              }
              return items;
            }
          });
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  static Task<JSONObject> requestAsync(final String path,
                                       final String method,
                                       final JSONObject parameters) {
    return Task.callInBackground(new Callable<JSONObject>() {
      @Override
      public JSONObject call() throws Exception {
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
        if (!method.equals("GET") && parameters != null) {
          String body;
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
        if (result.startsWith("{")) {
          return new JSONObject(result);
        } else if (result.startsWith("[")) {
          JSONObject obj = new JSONObject();
          JSONArray array = new JSONArray(result);
          obj.put("results", array);
          return obj;
        } else {
          throw new RuntimeException("Bad data: " + result);
        }
      }
    });
  }
}
