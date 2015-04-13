/*
 * Copyright (c) 2015. Hoomi, Inc.
 */

package co.hoomi.yesyoo;

import co.hoomi.HoomiClient;

public class Application extends android.app.Application {
  @Override
  public void onCreate() {
    super.onCreate();
    // TODO: Insert your Hoomi application ID here
    HoomiClient.setCurrentClient(new HoomiClient(this, HOOMI_APPLICATION_ID));
    // TODO: Uncomment the line below and add your application's Google Web Client ID to enable client authentication.
    // HoomiClient.getCurrentClient().setWebGoogleClientId(GOOGLE_WEB_CLIENT_ID);
  }
}
