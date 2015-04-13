/*
 * Copyright (c) 2015. Hoomi, Inc.
 */

package co.hoomi.yesyoo;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import java.util.Arrays;

import co.hoomi.HoomiAccessToken;
import co.hoomi.HoomiClient;
import co.hoomi.HoomiLoginButton;


public class MainActivity extends Activity {
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    if (HoomiClient.getCurrentClient().getCurrentToken() != null) {
      goToDataList();
    }

    super.onCreate(savedInstanceState);

    ActionBar bar = getActionBar();
    if (bar != null) {
      bar.hide();
    }
    setContentView(R.layout.activity_main);

    HoomiLoginButton button = (HoomiLoginButton) findViewById(R.id.login_button);
    button.setRedirectUri(Uri.parse("yesyoo://login"));
    button.setScopes(Arrays.asList("user:app:data:read", "user:app:data:write"));
    button.addLogInListener(new HoomiLoginButton.LogInListener() {
      @Override
      public void onLogIn(HoomiAccessToken token) {
        goToDataList();
      }
    });
  }

  private void goToDataList() {
    Intent listPage = new Intent(MainActivity.this, DataListActivity.class);
    listPage.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
        Intent.FLAG_ACTIVITY_NEW_TASK |
        Intent.FLAG_ACTIVITY_CLEAR_TASK);
    startActivity(listPage);
  }
}
