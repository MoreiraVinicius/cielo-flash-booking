package com.cielo.flashbooking.notification.email;

import java.nio.charset.StandardCharsets;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

public class SmtpReservationEmailSender implements ReservationEmailSender {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpReservationEmailSender(JavaMailSender mailSender, String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public String send(ReservationEmail email) {
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(email.recipient());
            helper.setSubject(email.subject());
            helper.setText(email.body(), false);
            mailSender.send(message);
            return message.getMessageID() == null ? "smtp-accepted" : message.getMessageID();
        } catch (Exception exception) {
            throw new IllegalStateException("SMTP delivery failed", exception);
        }
    }
}
