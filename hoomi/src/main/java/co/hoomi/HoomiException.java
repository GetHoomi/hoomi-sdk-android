/*
 * Copyright (c) 2015. Hoomi, Inc. All Rights Reserved
 */

package co.hoomi;

/**
 * Represents an Exception thrown while authorizing with Hoomi.
 */
public class HoomiException extends Exception {
  /**
   * Creates a new HoomiException with the given message.
   *
   * @param message the message for the exception.
   */
  public HoomiException(String message) {
    super(message);
  }
}
