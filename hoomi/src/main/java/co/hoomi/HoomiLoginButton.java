/*
 * Copyright (c) 2015. Hoomi, Inc. All Rights Reserved
 */

package co.hoomi;

import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.larvalabs.svgandroid.SVG;
import com.larvalabs.svgandroid.SVGBuilder;

import java.util.ArrayList;
import java.util.List;

import bolts.Continuation;
import bolts.Task;

/**
 * Provides a button that can be used to initiate login with Hoomi.
 */
public class HoomiLoginButton extends View {
  /**
   * Defines a listener for login completion when using a HoomiLoginButton.
   */
  public static interface LogInListener {
    /**
     * Called upon login when using a HoomiLoginButton.
     *
     * @param token the token for the logged in user
     */
    public void onLogIn(HoomiAccessToken token);
  }

  private static final int WHITE_COLOR = Color.WHITE;
  private static final int GREEN_COLOR = Color.argb(255, 0, 110, 46);

  private SVG logo;
  private SVG hoomiName;
  private RectF hoomiNameLimits;
  private RectF logoRect;
  private RectF hoomiNameRect;
  private RectF textRect;
  private RectF backgroundRect;
  private float margin;
  private Paint paint;
  private Paint backgroundPaint;

  private boolean imagesBuilt;

  private boolean isPressed;

  private HoomiClient client;
  private List<String> scopes;
  private Uri redirectUri;
  private List<LogInListener> logInListeners;

  /**
   * Creates a HoomiLoginButton.
   *
   * @param context the context
   */
  public HoomiLoginButton(Context context) {
    super(context);
    setUp();
  }

  /**
   * Creates a HoomiLoginButton.
   *
   * @param context the context
   * @param attrs   the attributes
   */
  public HoomiLoginButton(Context context, AttributeSet attrs) {
    super(context, attrs);
    setUp();
  }

  /**
   * Creates a HoomiLoginButton
   *
   * @param context      the context
   * @param attrs        the attributes
   * @param defStyleAttr the style
   */
  public HoomiLoginButton(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    setUp();
  }

  private String getButtonText() {
    return "Continue with ";
  }

  private int getForegroundColor() {
    return WHITE_COLOR;
  }

  private int getBackgroundColor() {
    return GREEN_COLOR;
  }

  private void setUp() {
    logInListeners = new ArrayList<LogInListener>();
    setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    buildImages();
    setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        if (getClient() == null) {
          throw new IllegalStateException("You must first set the client for this button.");
        }
        final ProgressDialog dlg = new ProgressDialog(getContext());
        dlg.setMessage("Please wait...");
        dlg.show();
        getClient().authorizeAsync(redirectUri, scopes)
            .onSuccess(new Continuation<HoomiAccessToken, Object>() {
              @Override
              public Object then(Task<HoomiAccessToken> task) throws Exception {
                onLogIn(task.getResult());
                return null;
              }
            }, Task.UI_THREAD_EXECUTOR)
            .continueWith(new Continuation<Object, Object>() {
              @Override
              public Object then(Task<Object> task) throws Exception {
                dlg.dismiss();
                return null;
              }
            }, Task.UI_THREAD_EXECUTOR);
      }
    });
  }

  /**
   * Gets the HoomiClient to use for login.
   * The default client will be used if none is set.
   *
   * @return the HoomiClient
   */
  public HoomiClient getClient() {
    if (client == null) {
      return HoomiClient.getCurrentClient();
    }
    return client;
  }

  /**
   * Sets the HoomiClient to use for login.
   * The default client will be used if none is set.
   *
   * @param client the HoomiClient
   */
  public void setClient(HoomiClient client) {
    this.client = client;
  }

  /**
   * Gets the list of scopes to request from the user when logging in.
   *
   * @return the list of scopes
   */
  public List<String> getScopes() {
    return scopes;
  }

  /**
   * Sets the list of scopes to request from the user when logging in.
   *
   * @param scopes the list of scopes
   */
  public void setScopes(List<String> scopes) {
    this.scopes = scopes;
  }

  /**
   * Gets the redirect URI for this app.  This value must be set before attempting
   * login.
   *
   * @return the redirect URI
   */
  public Uri getRedirectUri() {
    return redirectUri;
  }

  /**
   * Sets the redirect URI for this app.  This value must be set before attempting
   * login.
   *
   * @param uri the redirect URI
   */
  public void setRedirectUri(Uri uri) {
    this.redirectUri = uri;
  }

  /**
   * Adds a LogInListener.
   *
   * @param listener the listener
   */
  public void addLogInListener(LogInListener listener) {
    logInListeners.add(listener);
  }

  /**
   * Removes a LogInListener.
   *
   * @param listener the listener
   */
  public void removeLogInListener(LogInListener listener) {
    logInListeners.remove(listener);
  }

  /**
   * Invokes the listeners after login.  You may override this method
   * instead of registering listeners.
   *
   * @param token the HoomiAccessToken retrieved when authorizing.
   */
  protected void onLogIn(HoomiAccessToken token) {
    for (LogInListener listener : logInListeners) {
      listener.onLogIn(token);
    }
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    boolean clicked = false;
    switch (event.getAction()) {
      case MotionEvent.ACTION_DOWN:
        isPressed = true;
        clicked = true;
        break;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        isPressed = false;
        break;
    }
    buildImages();
    invalidate();
    if (clicked) {
      callOnClick();
    }
    return true;
  }

  private void buildImages() {
    int background = isPressed ? darkenColor(getBackgroundColor()) : getBackgroundColor();
    int foreground = isPressed ? darkenColor(getForegroundColor()) : getForegroundColor();
    paint = new Paint();
    paint.setTextAlign(Paint.Align.LEFT);
    paint.setTextSize(getResources().getDisplayMetrics().scaledDensity *
        18 * getResources().getConfiguration().fontScale);
    paint.setColor(foreground);
    backgroundPaint = new Paint();
    backgroundPaint.setColor(background);
    if (!isInEditMode()) {
      logo = new SVGBuilder()
          .readFromResource(getResources(), R.raw.hoomi_icon_only)
          .setColorSwap(Color.WHITE, foreground)
          .build();
      hoomiName = new SVGBuilder()
          .readFromResource(getResources(), R.raw.hoomi_text_only)
          .setColorSwap(Color.WHITE, foreground)
          .build();
      hoomiNameLimits = hoomiName.getLimits();
    } else {
      Rect hoomiNameLimitsTemp = new Rect();
      paint.getTextBounds("Hoomi", 0, 0, hoomiNameLimitsTemp);
      hoomiNameLimits = new RectF(hoomiNameLimitsTemp);
    }
    imagesBuilt = true;
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    if (!imagesBuilt) {
      buildImages();
    }
    margin = h * 0.1f;

    String buttonText = getButtonText();
    Rect textRectTemp = new Rect();
    paint.getTextBounds(buttonText, 0, buttonText.length(), textRectTemp);
    textRect = new RectF(textRectTemp);
    textRect.sort();

    float hoomiNameScale = textRect.height() / hoomiNameLimits.height();
    // Get the width of a space
    paint.getTextBounds("_", 0, 1, textRectTemp);
    hoomiNameRect = new RectF(0,
        0,
        hoomiNameLimits.width() * hoomiNameScale,
        hoomiNameLimits.height() * hoomiNameScale);
    hoomiNameRect.offsetTo(textRect.right + textRectTemp.width(),
        (h - hoomiNameRect.height()) * 0.5f);

    float right = hoomiNameRect.right;

    float logoSize = Math.max(Math.min(h - (2 * margin),
        w - right - (2 * margin)), hoomiNameRect.height() * 2);
    logoRect = new RectF(margin, margin, margin + logoSize, margin + logoSize);
    textRect.offsetTo(logoRect.right + margin, logoRect.centerY() - textRect.height() * 0.5f);
    hoomiNameRect.offsetTo(textRect.right + textRectTemp.width(),
        logoRect.centerY() - hoomiNameRect.height() * 0.5f);

    backgroundRect = new RectF(0,
        0,
        Math.max(w, hoomiNameRect.right + margin),
        Math.max(h, logoRect.height() + 2 * margin));

    // Now center the text in the space
    float textOffset = (backgroundRect.right - hoomiNameRect.right - logoRect.right) / 2 - margin;
    hoomiNameRect.offset(textOffset, 0);
    textRect.offset(textOffset, 0);

    Log.d("SizeChanged", "New size is: " + backgroundRect.width() + ", " + backgroundRect.height());
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    boolean hasPrevious = backgroundRect != null;
    float prevHeight = 0, prevWidth = 0;
    if (hasPrevious) {
      prevHeight = backgroundRect.height();
      prevWidth = backgroundRect.width();
      Log.d("OnMeasure", "Measuring with previous size: " + prevWidth + ", " + prevHeight);
    }
    float width = MeasureSpec.getSize(widthMeasureSpec);
    float height = MeasureSpec.getSize(heightMeasureSpec);
    Log.d("OnMeasure", "Measure 1");
    onSizeChanged(0, 0, 0, 0);
    float minHeight = backgroundRect.width();
    float minWidth = backgroundRect.height();
    if (height < minHeight) {
      height = minHeight;
    }
    if (width < minWidth) {
      width = minWidth;
    }
    if (hasPrevious) {
      Log.d("OnMeasure", "Measure 2");
      onSizeChanged((int) prevWidth, (int) prevHeight, 0, 0);
    }
    setMeasuredDimension(resolveSize((int) width, widthMeasureSpec),
        resolveSize((int) height, heightMeasureSpec));
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    canvas.drawRoundRect(backgroundRect,
        backgroundRect.height() * 0.05f,
        backgroundRect.height() * 0.05f,
        backgroundPaint);
    canvas.drawText(getButtonText(), textRect.left, textRect.bottom, paint);
    if (!isInEditMode()) {
      canvas.drawPicture(logo.getPicture(), logoRect);
      canvas.drawPicture(hoomiName.getPicture(), hoomiNameRect);
    } else {
      canvas.drawText("Hoomi", hoomiNameRect.left, hoomiNameRect.bottom, paint);
    }
  }

  private static int darkenColor(int color) {
    float[] hsv = new float[3];
    Color.colorToHSV(color, hsv);
    hsv[2] *= 0.8;
    return Color.HSVToColor(hsv);
  }
}
