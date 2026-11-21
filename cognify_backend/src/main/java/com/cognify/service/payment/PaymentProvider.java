package com.cognify.service.payment;

import java.util.UUID;

/** Bank adapters must verify success server-side; browser callbacks are not proof of payment. */
public interface PaymentProvider {
  record Receipt(String reference, boolean confirmed) {}

  String name();

  boolean available();

  boolean testOnly();

  Receipt create(UUID paymentId, long amountMinor, String currency);

  boolean verifyNotification(byte[] payload, String signature);
}
