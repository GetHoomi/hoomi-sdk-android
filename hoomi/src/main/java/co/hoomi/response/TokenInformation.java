/*
 * Copyright (c) 2015. Hoomi, Inc. All Rights Reserved
 */

package co.hoomi.response;

import java.util.Date;

import co.hoomi.HoomiAccessToken;

/**
 * Represents information about the given HoomiAccessToken.
 */
public class TokenInformation {
  private final HoomiAccessToken token;
  private final String applicationId;
  private final Date issued;
  private final String userId;
  private final boolean issuedToAuthenticatedClient;

  /**
   * Creates a TokenInformation object.
   *
   * @param token                       the token whose information is being represented
   * @param applicationId               the applicationId for which the token was issued
   * @param issued                      the date and time the token was issued
   * @param userId                      the stable, unique user ID of the user for which the token was issued
   * @param issuedToAuthenticatedClient was the token issued to an authenticated client
   */
  public TokenInformation(HoomiAccessToken token,
                          String applicationId,
                          Date issued,
                          String userId,
                          boolean issuedToAuthenticatedClient) {
    this.token = token;
    this.applicationId = applicationId;
    this.issued = issued;
    this.userId = userId;
    this.issuedToAuthenticatedClient = issuedToAuthenticatedClient;
  }

  /**
   * Gets the user id (scoped to the application) that this token is for, if any.
   *
   * @return the user ID
   */
  public String getUserId() {
    return userId;
  }

  /**
   * Gets the application to which this token belongs.
   *
   * @return the application ID
   */
  public String getApplicationId() {
    return applicationId;
  }

  /**
   * Gets the time at which this token was issued.
   *
   * @return the time at which the token was issued
   */
  public Date getIssued() {
    return issued;
  }

  /**
   * The token (with updated known scopes and expiration).
   *
   * @return the token
   */
  public HoomiAccessToken getToken() {
    return token;
  }

  /**
   * Whether the token was issued to an authenticated client.
   *
   * @return true if and only if the token was issued to an authenticated client
   */
  public boolean isIssuedToAuthenticatedClient() {
    return issuedToAuthenticatedClient;
  }
}
