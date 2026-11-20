package com.cognify.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ResetMailService {
  private final ObjectProvider<JavaMailSender> sender;
  private final boolean enabled;
  private final String from;
  private final String frontend;

  public ResetMailService(
      ObjectProvider<JavaMailSender> sender,
      @Value("${cognify.mail-enabled:false}") boolean enabled,
      @Value("${cognify.mail-from:}") String from,
      @Value("${cognify.frontend-url:http://localhost:3000}") String frontend) {
    this.sender = sender;
    this.enabled = enabled;
    this.from = from;
    this.frontend = frontend;
  }

  public void requireAvailable() {
    if (!enabled || from.isBlank() || sender.getIfAvailable() == null) throw unavailable();
  }

  public void send(String email, String token) {
    requireAvailable();
    SimpleMailMessage mail = new SimpleMailMessage();
    mail.setFrom(from);
    mail.setTo(email);
    mail.setSubject("Cognify — գաղտնաբառի վերականգնում");
    mail.setText(
        "Գաղտնաբառը վերականգնելու համար բացեք հղումը։ Այն գործում է 30 րոպե։\n\n"
            + frontend
            + "/reset-password#token="
            + token
            + "\n\nԵթե դուք չեք ուղարկել այս հարցումը, անտեսեք նամակը։");
    try {
      sender.getObject().send(mail);
    } catch (org.springframework.mail.MailException e) {
      throw unavailable();
    }
  }

  private ResponseStatusException unavailable() {
    return new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE, "Էլ․ փոստով վերականգնումն այժմ հասանելի չէ։");
  }
}
