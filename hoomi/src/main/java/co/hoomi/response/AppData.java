/*
 * Copyright (c) 2015. Hoomi, Inc. All Rights Reserved
 */

package co.hoomi.response;

import org.json.JSONObject;

/**
 * Represents app data stored with a user.
 */
public class AppData {
  private JSONObject data;
  private String eTag;

  /**
   * Creates an AppData instance with the given data and ETag.
   *
   * @param data the app data for the user
   * @param eTag the ETag of the app data (used for optimistic concurrency)
   */
  public AppData(JSONObject data, String eTag) {
    this.data = data;
    this.eTag = eTag;
  }

  /**
   * Sets the app data.
   *
   * @param data the new app data
   */
  public void setData(JSONObject data) {
    this.data = data;
  }

  /**
   * Gets the app data. This is mutable so that it can be easily modified and
   * used to set the data for the user.
   *
   * @return the app data for the user
   */
  public JSONObject getData() {
    return data;
  }

  /**
   * Sets the ETag for the app data.
   *
   * @param eTag the new ETag
   */
  public void setETag(String eTag) {
    this.eTag = eTag;
  }

  /**
   * Gets the ETag for the app data, used for optimistic concurrency control.
   *
   * @return the ETag for the app data
   */
  public String getETag() {
    return eTag;
  }
}
