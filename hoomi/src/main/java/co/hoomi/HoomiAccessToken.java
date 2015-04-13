/*
 * Copyright (c) 2015. Hoomi, Inc. All Rights Reserved
 */

package co.hoomi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Represents a Hoomi access token.
 */
public class HoomiAccessToken {
  private String tokenString;
  private List<String> knownScopes;
  private Date knownExpiration;

  /**
   * Creates a HoomiAccessToken instance.
   *
   * @param tokenString the token string
   */
  public HoomiAccessToken(String tokenString) {
    this(tokenString, null, null);
  }

  /**
   * Creates a HoomiAccessToken instance with the given token string as well as the
   * scopes and expiration time that are known for the token.
   *
   * @param tokenString     the token string
   * @param knownScopes     the set of scopes known to be issued for this token
   * @param knownExpiration the known expiration time for this token
   */
  public HoomiAccessToken(String tokenString, List<String> knownScopes, Date knownExpiration) {
    this.tokenString = tokenString;
    this.knownScopes = knownScopes;
    this.knownExpiration = knownExpiration;
  }

  /**
   * Gets the token string that will be used for requests with this token.
   *
   * @return the token string
   */
  public String getTokenString() {
    return tokenString;
  }

  /**
   * Gets the set of scopes known to be provided by this token.  Users may revoke
   * access to these scopes.
   *
   * @return the set of scopes issued for the token, or null if the scopes are unknown
   */
  public List<String> getKnownScopes() {
    return knownScopes;
  }

  /**
   * Gets the expiration known for this token.  The token may become invalid
   * before its expiration date.
   *
   * @return the known expiration time, or null if the expiration time is unknown
   */
  public Date getKnownExpiration() {
    return knownExpiration;
  }

  String serialize() {
    try {
      JSONObject obj = new JSONObject();
      obj.put("tokenString", tokenString);
      if (knownScopes != null) {
        obj.put("knownScopes", new JSONArray(knownScopes));
      }
      if (knownExpiration != null) {
        obj.put("knownExpiration", knownExpiration.getTime());
      }
      return obj.toString();
    } catch (JSONException e) {
      // Can't happen.
      throw new RuntimeException(e);
    }
  }

  static HoomiAccessToken deserialize(String serialized) {
    if (serialized == null) {
      return null;
    }
    try {
      JSONObject obj = new JSONObject(serialized);
      JSONArray rawScopes = obj.optJSONArray("knownScopes");
      ArrayList<String> scopes = null;
      if (rawScopes != null) {
        scopes = new ArrayList<String>();
        for (int i = 0; i < rawScopes.length(); i++) {
          scopes.add(rawScopes.getString(i));
        }
      }
      Date expiration = null;
      if (obj.has("knownExpiration")) {
        expiration = new Date(obj.getLong("knownExpiration"));
      }
      return new HoomiAccessToken(obj.getString("tokenString"),
          scopes,
          expiration);
    } catch (JSONException e) {
      throw new RuntimeException(e);
    }
  }
}
