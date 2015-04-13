# Hoomi Android SDK

## Table of Contents

* [API Documentation](https://gethoomi.github.io/hoomi-sdk-android/javadoc/)
* [Getting Started](#getting-started)
* [Configuring your Application](#configure)
* ["Continue with Hoomi" button](#button-login)
* [Using the API to log in](#api-login)
* [Client Authentication](#client-authentication)
* [Using an Access Token](#using-token)
* [Personalization using App Data](#app-data)

***

## API Documentation

API documentation for the Hoomi Android SDK can be found [here](https://gethoomi.github.io/hoomi-sdk-android/javadoc/).

<a name="getting-started">
## Getting Started
</a>

To add the Hoomi Android SDK to your Android project, add the following Gradle repository to your `build.gradle`:

```gradle
allprojects {
    repositories {
        maven {
            url "http://dl.bintray.com/hoomi/Hoomi"
        }
    }
}
```

Then in your application's `build.gradle`, you can then add the the following dependency:

```gradle
dependencies {
    compile 'co.hoomi:hoomi-android:0.9.0'
}
```

Once you have added the Hoomi dependency to your project, you should initialize the Hoomi SDK in your `Application.onCreate()`
method, replacing `HOOMI_APPLICATION_ID` with the application ID for your app from the [developer dashboard](https://www.hoomi.co/developer/apps):

```java
package com.example;

import co.hoomi.HoomiClient;

public class Application extends android.app.Application {
  @Override
  public void onCreate() {
    super.onCreate();
    // TODO: Insert your Hoomi application ID here
    HoomiClient.setCurrentClient(new HoomiClient(this, HOOMI_APPLICATION_ID));
  }
}
```

If you don't already have an Application subclass (as shown above), add the class to your project and specify the `android:name`
attribute for the `application` tag in your `AndroidManifest.xml`:

```xml
<manifest
    package="com.example.myApp"
    xmlns:android="http://schemas.android.com/apk/res/android">
    ...
    <application
        android:name="com.example.Application"
        ...>
        ...
    </application>
</manifest>
```

Finally, you'll need to add the following `uses-permission` tags to your `AndroidManifest.xml`:

```xml
<manifest
    package="com.example"
    xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.GET_ACCOUNTS"/>

    ...
</manifest>
```

If you are not using client authentication, you may omit the `GET_ACCOUNTS` permission.

<a name="configure">
## Configuring your Application
</a>

In order to use Hoomi login, you must allow Hoomi to pass data to your app.  Your app can accomplish this by
handling a specially-crafted URL, which allows the browser or Hoomi app to redirect back to your application
after a user authorizes the app.  The Hoomi SDK provides a built-in `Activity` that can be used to handle this
redirect, requiring only a small amount of configuration in your `AndroidManifest.xml` file.

To define a redirect handler for your application, you should choose a custom URL scheme and prefix.  We
recommend using your application's package name as the URL scheme, with "login" as host.  You would add
the handler like this:

```xml
<manifest
    package="com.example.myApp"
    xmlns:android="http://schemas.android.com/apk/res/android">

    ...

    <application ...>
      ...

        <activity android:name="co.hoomi.HoomiLoginActivity">
            <intent-filter>
                <action android:name="android.intent.action.VIEW"/>

                <category android:name="android.intent.category.BROWSABLE"/>
                <category android:name="android.intent.category.DEFAULT"/>

                <data
                    android:host="login"
                    android:scheme="com.example.myApp"/>
            </intent-filter>
        </activity>
    </application>
</manifest>
```

Note that your activity **must** handle the `VIEW` action and the `BROWSABLE` and `DEFAULT` categories,
as in the example above.

Now that you've specified the `HoomiLoginActivity`, your Android app is ready to handle URLs that begin with
(in the example above) `com.example.myApp://login`.

You must also tell Hoomi that this is a valid redirect for your application.  To do this, open the
[developer portal](https://www.hoomi.co/developer/apps), select your app, and open the "Redirects" tab.
You can add an Android redirect by clicking the Android icon at the bottom of the page and giving the redirect
a name.

You should complete the form by providing the URL prefix specified above (e.g. `com.example.myApp://login`) and
the package name for your application (e.g. `com.example.myApp`). Click "Save" and your application will be fully
configured to support an Android redirect target.

<a name="button-login">
## "Continue with Hoomi" button
</a>

You can now easily add Hoomi login to your app using the "Continue with Hoomi" login button, defined by the
`HoomiLoginButton` class.

`HoomiLoginButton` can be used much like a built-in `Button`, but with just a little configuration will
automatically trigger a Hoomi login for your application.  Simply add the button to your layout XML:

```xml
<co.hoomi.HoomiLoginButton
    android:id="@+id/login_button"
    android:layout_width="wrap_content"
    android:layout_height="50dp" />
```

After the `View` containing the button is inflated (e.g. in your `Activity`'s or `Fragment`'s `onCreate()`), you
should configure the button, telling it the redirect URL for your application, which scopes (if any) you would
like to request from the user, and what to do after login completes:

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
  super.onCreate(savedInstanceState);

  HoomiLoginButton button = (HoomiLoginButton) findViewById(R.id.login_button);
  button.setRedirectUri(Uri.parse("com.example.myApp://login"));
  button.setScopes(Arrays.asList("user:app:data:read", "user:app:data:write"));
  button.addLogInListener(new HoomiLoginButton.LogInListener() {
    @Override
    public void onLogIn(HoomiAccessToken token) {
      // The user has logged in
    }
  });
}
```

After logging in,
[`HoomiClient.getCurrentClient().getCurrentToken()`](https://gethoomi.github.io/hoomi-sdk-android/javadoc/co/hoomi/HoomiClient.html#getCurrentToken--)
will return the token issued for the user, and this value will be cached between runs of your application.

<a name="api-login">
## Using the API to log in
</a>

If you would prefer to use your own button (or some other mechanism) to initiate login with Hoomi,
you can directly call the methods on `HoomiClient`.  These methods are asynchronous, and return
[Bolts Tasks](https://github.com/BoltsFramework/Bolts-Android).

When you create your first `HoomiClient` instance, that client automatically becomes the "current
client", so all you need to do initiate Hoomi authorization is to call
[`authorizeAsync()`](https://gethoomi.github.io/hoomi-sdk-android/javadoc/co/hoomi/HoomiClient.html#authorizeAsync-Uri-java.util.List-)
with the redirect URL for your application and the scopes you would like to request:

```java
HoomiClient.getCurrentClient().authorizeAsync(Uri.parse("com.example.myApp://login"),
    Arrays.asList("user:app:data:read", "user:app:data:write"))
    .onSuccess(new Continuation<HoomiAccessToken, Void> {
      @Override
      public Void then(Task<HoomiAccessToken> task) {
        // The user has logged in
      }
    });
```

After logging in,
[`HoomiClient.getCurrentClient().getCurrentToken()`](https://gethoomi.github.io/hoomi-sdk-android/javadoc/co/hoomi/HoomiClient.html#getCurrentToken--)
will return the token issued for the user, and this value will be cached between runs of your application.

<a name="client-authentication">
## Client Authentication
</a>

Android apps on devices with Google Play Services installed can use
[cross-client authentication](https://developers.google.com/accounts/docs/CrossClientAuth) to perform
client authentication with Hoomi. When your clients are authenticated, Hoomi will issue longer-lived tokens
to your application, and this process can be used to ensure that no malicious apps can masquerade as your
application to get a token for a user that would log them into your apps.

To add client authentication to your application, you will first need to create a corresponding project
in the [Google Developers Console](https://console.developers.google.com/project). Once the project has been
created, open its Project Dashboard and do the following:

* Navigate to "APIs & auth" and select the "Consent Screen" option.
* Configure your consent screen. Unless you are using Google services for something else, your users will
never see this, so you can just provide values for the required fields.
* Navigate to "APIs & auth" and select the "Credentials" option.
* Under "OAuth", click "Create a new Client ID".  Select "Web Application", and continue by clicking "Create Client ID".  The values
in this dialog do not impact cross-client authentication, so you can use the default values or provide something appropriate for
your service. The client ID this creates is your "Google Web Client ID", and you will need to use it later to configure client authentication
with Hoomi.
* Still on the same page, you'll now create Client IDs for your Android applications.  Click "Create new Client ID" again, and select
"Installed application". Select "Android" for the "Installed application type", and then provide your app's package name and signing
certificate fingerprint (instructions below). Click "Create Client ID".  You may want to repeat this step for your application's debug
keystore so that your application is still authenticated even while debugging.  These client IDs are your "Google Android Client IDs", and
you will need to use them later to configure client authentication.

To get your signing certificate fingerprint, you'll need to use Java's `keytool` to get the fingerprint from your application's keystore.
For your production keystore, you can use the following command:

```bash
keytool -list -v -keystore ~/path/to/my.keystore -alias {my alias} -storepass {my keystore password} -keypass {my key password}
```

To get the fingerprint for the debug keystore on your machine, you can use the following command:

```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

Now that you have created your Google Client Web and Android Client IDs, you should add these to your Android redirect
using the [developer portal](https://www.hoomi.co/developer/apps). You can add multiple client IDs by separating them with
commas.

Finally, to enable client authentication in your app, add the following call to
[`setWebGoogleClientId()`](https://gethoomi.github.io/hoomi-sdk-android/javadoc/co/hoomi/HoomiClient.html#setWebGoogleClientId-java.lang.String-)
to your `Application.onCreate()` method after you initialize your `HoomiClient`:

```java
public void onCreate() {
  super.onCreate();
  HoomiClient.setCurrentClient(new HoomiClient(this, HOOMI_APPLICATION_ID));
  // TODO: Add your application's Google Web Client ID to enable client authentication
  HoomiClient.getCurrentClient().setWebGoogleClientId(GOOGLE_WEB_CLIENT_ID);
}
```

Client authentication requires Google Play Services and a Google account on the device (which be there if your app
was downloaded from the Play Store).  The SDK will automatically skip client authentication if either
of these conditions are not met.  You can check whether a token was issued to an authenticated client by
[fetching the token information](#using-token) from Hoomi.

<a name="using-token">
## Using an Access Token
</a>

After Hoomi has redirected back to your app, authorization completes and your code receives an access token.  You can use
this token to do a number of things on behalf of the user, depending upon the scopes you requested.  The Hoomi SDK will
automatically cache the access token it has received in local storage on the device so that it can be accessed across
runs of the application.

You can retrieve the stored access token at any time by calling
[`getCurrentToken()`](https://gethoomi.github.io/hoomi-sdk-android/javadoc/co/hoomi/HoomiClient.html#getCurrentToken--).

To get information about the access token you have (e.g. the user ID for the user that authorized your application or the
token's expiration time), you can call
[`getTokenInformationAsync`](https://gethoomi.github.io/hoomi-sdk-android/javadoc/co/hoomi/HoomiClient.html#getTokenInformationAsync-co.hoomi.HoomiAccessToken-).
If the `Task` returns successfully, the token is valid, and a
[`TokenInformation`](https://gethoomi.github.io/hoomi-sdk-android/javadoc/co/hoomi/response/TokenInformation.html) provides
information about the token itself.

<a name="app-data">
## Personalization using App Data
</a>

Hoomi makes it easy to store personalization data for your users without having to resort to a dedicated backend.  When you use Hoomi
for login, you can save and retrieve "App Data" for your users, which lets you keep an arbitrary JSON object for each user.  For example,
you might store bookmarks, favorites, preferences, saved games, or high scores for each user, and Hoomi will ensure that access
to that data is restricted to those who have a valid token for the user.

Taking advantage of this feature requires that you request the `user:app:data:read` and `user:app:data:write` scopes during login.
Once you've done this, you can retrieve the App Data for a user by calling
[`getAppDataAsync()`](https://gethoomi.github.io/hoomi-sdk-android/javadoc/co/hoomi/HoomiClient.html#getAppDataAsync--) like this:

```java
HoomiClient.getCurrentClient()
    .getAppDataAsync()
    .onSuccess(new Continuation<AppData, Void>() {
      @Override
      public Void then(Task<AppData> task) throws Exception {
        JSONObject data = task.getResult().getData();
        return null;
      }
    });
```

Similarly, you can save App Data using the
[`setAppDataAsync()`](https://gethoomi.github.io/hoomi-sdk-android/javadoc/co/hoomi/HoomiClient.html#setAppDataAsync-co.hoomi.HoomiAccessToken-JSONObject-java.lang.String-)
method:

```java
JSONObject data = new JSONObject();
data.put("highScore", 9001);
data.put("favorites", new JSONArray(Arrays.asList(favorite1, favorite2)));
data.put("theme", "dark");
data.put("lastLogin", new Date().toString());
HoomiClient.getCurrentClient()
    .setAppDataAsync(data);
```

The `AppData` object also includes an `ETag`, which allows optimistic concurrency control when saving data. Every time your `AppData` for
a user changes, the `ETag` gets a new value.  If you pass that `ETag` back to Hoomi while saving a new value, the save will only succeed
if the provided `ETag` matches the current `ETag` in our database. Usually, when this occurs, you'll want to re-fetch the App Data,
reapply your changes, and then attempt to save again.

Unless an access token is explicitly provided, the App Data APIs will use the current token stored for your app.