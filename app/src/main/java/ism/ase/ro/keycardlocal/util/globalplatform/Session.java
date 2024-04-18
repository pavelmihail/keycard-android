package ism.ase.ro.keycardlocal.util.globalplatform;

import im.status.keycard.globalplatform.SCP02Keys;

/**
 * SCP02 Session.
 */
public class Session {
  private im.status.keycard.globalplatform.SCP02Keys keys;
  private byte[] cardChallenge;
  private boolean fallbackKeys;

  /**
   * Constructs the SCP02 session.
   *
   * @param keys the session keys
   * @param cardChallenge the card challenge
   */
  public Session(im.status.keycard.globalplatform.SCP02Keys keys, byte[] cardChallenge) {
    this.keys = keys;
    this.cardChallenge = cardChallenge;
    this.fallbackKeys = false;
  }

  /**
   * The SCP02 keys
   * @return SCP02 keys
   */
  public SCP02Keys getKeys() {
    return keys;
  }

  /**
   * The card challenge
   * @return card challenge
   */
  public byte[] getCardChallenge() {
    return cardChallenge;
  }

  /**
   * Marks this session as using a fallback keyset.
   */
  public void markAsUsingFallbackKeys() {
    fallbackKeys = true;
  }

  /**
   * True if a fallback keyset is being used.
   *
   * @return true or false
   */
  public boolean usesFallbackKeys() {
    return fallbackKeys;
  }

}
