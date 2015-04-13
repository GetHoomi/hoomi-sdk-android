/*
 * Copyright (c) 2015. Hoomi, Inc.
 */

package co.hoomi.yesyoo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import bolts.Capture;
import bolts.Continuation;
import bolts.Task;
import co.hoomi.HoomiAccessToken;
import co.hoomi.HoomiClient;
import co.hoomi.response.AppData;
import co.hoomi.response.TokenInformation;


public class DataListActivity extends Activity {
  private String hackerNewsAlias;
  private String hackerNewsKarma;
  private ListView listView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_data_list);
    listView = (ListView) findViewById(R.id.listView);
    loggedIn();
  }


  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.menu_data_list, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Handle action bar item clicks here. The action bar will
    // automatically handle clicks on the Home/Up button, so long
    // as you specify a parent activity in AndroidManifest.xml.
    int id = item.getItemId();

    //noinspection SimplifiableIfStatement
    if (id == R.id.action_logout) {
      HoomiClient.getCurrentClient().logOut();
      Intent firstPageIntent = new Intent(this, MainActivity.class);
      firstPageIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
          Intent.FLAG_ACTIVITY_NEW_TASK |
          Intent.FLAG_ACTIVITY_CLEAR_TASK);
      startActivity(firstPageIntent);
      return true;
    }

    return super.onOptionsItemSelected(item);
  }

  public String getHackerNewsAlias() {
    return hackerNewsAlias;
  }

  public void setHackerNewsAlias(String alias) {
    hackerNewsAlias = alias;
    updateTitle();
  }

  public String getHackerNewsKarma() {
    return hackerNewsKarma;
  }

  public void setHackerNewsKarma(String karma) {
    hackerNewsKarma = karma;
    updateTitle();
  }

  private void updateTitle() {
    String title = getHackerNewsAlias();
    if (getHackerNewsKarma() != null) {
      title += " (" + getHackerNewsKarma() + ")";
    }
    this.setTitle(title);
  }

  private void loggedIn() {
    HoomiAccessToken token = HoomiClient.getCurrentClient().getCurrentToken();
    HoomiClient.getCurrentClient().getTokenInformationAsync(token);
    final ProgressDialog dlg = new ProgressDialog(DataListActivity.this);
    final Capture<AppData> appData = new Capture<AppData>();
    dlg.setMessage("Please wait...");
    dlg.setCancelable(false);
    dlg.show();
    HoomiClient.getCurrentClient()
        .getAppDataAsync()
        .onSuccess(new Continuation<AppData, Void>() {
          @Override
          public Void then(Task<AppData> task) throws Exception {
            String alias = task.getResult().getData().optString("hackerNewsAlias", null);
            appData.set(task.getResult());
            setHackerNewsAlias(alias);
            return null;
          }
        }, Task.UI_THREAD_EXECUTOR)
        .continueWithTask(new Continuation<Void, Task<Void>>() {
          @Override
          public Task<Void> then(Task<Void> task) throws Exception {
            if (getHackerNewsAlias() == null) {
              return promptForAliasAsync(appData.get());
            }
            return task;
          }
        }, Task.UI_THREAD_EXECUTOR)
        .onSuccessTask(new Continuation<Void, Task<Void>>() {
          @Override
          public Task<Void> then(Task<Void> task) throws Exception {
            return Task.whenAll(Arrays.asList(
                updateKarmaAsync(getHackerNewsAlias()),
                updateStoriesAsync()));
          }
        })
        .continueWith(new Continuation<Void, Void>() {
          @Override
          public Void then(Task<Void> task) throws Exception {
            dlg.dismiss();
            return null;
          }
        });
  }

  private String getDescription(JSONObject item) throws JSONException {
    return String.format("%s points by %s at %s",
        item.getString("score"),
        item.getString("by"),
        new Date(item.getLong("time") * 1000));
  }

  private Task<Void> updateStoriesAsync() {
    return HackerNewsApi.getTopStoriesAsync()
        .onSuccess(new Continuation<List<JSONObject>, Void>() {
          @Override
          public Void then(Task<List<JSONObject>> task) throws Exception {
            ArrayAdapter<JSONObject> adapter =
                new ArrayAdapter<JSONObject>(DataListActivity.this, 0, task.getResult()) {
                  @Override
                  public View getView(int position, View convertView, ViewGroup parent) {
                    try {
                      LinearLayout layout = new LinearLayout(getContext());
                      layout.setOrientation(LinearLayout.VERTICAL);
                      layout.setPadding(16, 0, 16, 0);
                      TextView title = new TextView(getContext());
                      title.setText((position + 1) + ". " + getItem(position).getString("title"));
                      title.setTextAppearance(getContext(),
                          android.R.style.TextAppearance_DeviceDefault_SearchResult_Title);
                      title.setMaxLines(1);
                      title.setEllipsize(TextUtils.TruncateAt.END);
                      title.setPadding(0, 15, 0, 10);
                      layout.addView(title);
                      TextView description = new TextView(getContext());
                      description.setText(getDescription(getItem(position)));
                      description.setTextAppearance(getContext(),
                          android.R.style.TextAppearance_DeviceDefault_SearchResult_Subtitle);
                      description.setTextColor(Color.rgb(130, 130, 130));
                      description.setMaxLines(1);
                      description.setEllipsize(TextUtils.TruncateAt.END);
                      description.setPadding(50, 0, 0, 15);
                      layout.addView(description);
                      return layout;
                    } catch (Exception e) {
                      throw new RuntimeException(e);
                    }
                  }
                };
            listView.setAdapter(adapter);
            return null;
          }
        }, Task.UI_THREAD_EXECUTOR);
  }

  private Task<Void> updateKarmaAsync(String userId) {
    return HackerNewsApi.getUserAsync(userId).onSuccess(new Continuation<JSONObject, Void>() {
      @Override
      public Void then(Task<JSONObject> task) throws Exception {
        setHackerNewsKarma(task.getResult().optString("karma", null));
        return null;
      }
    }, Task.UI_THREAD_EXECUTOR);
  }

  private Task<Void> promptForAliasAsync(final AppData appData) {
    final Task<String>.TaskCompletionSource tcs = Task.create();
    final EditText textBox = new EditText(this);
    final Capture<AlertDialog> alert = new Capture<AlertDialog>();
    alert.set(new AlertDialog.Builder(this)
        .setMessage("Please enter your HN user name")
        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            alert.get().dismiss();
            tcs.setResult(textBox.getText().toString());
          }
        })
        .setView(textBox)
        .setCancelable(false)
        .create());
    alert.get().show();
    return tcs.getTask()
        .onSuccessTask(new Continuation<String, Task<Void>>() {
          @Override
          public Task<Void> then(Task<String> task) throws Exception {
            appData.getData().put("hackerNewsAlias", task.getResult());
            setHackerNewsAlias(task.getResult());
            return HoomiClient.getCurrentClient()
                .setAppDataAsync(appData.getData(), appData.getETag())
                .makeVoid();
          }
        });
  }
}
