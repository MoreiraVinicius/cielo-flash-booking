package com.cielo.flashbooking.notification.email;

import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

public class SesReservationEmailSender implements ReservationEmailSender {

    private final SesV2Client sesClient;
    private final String fromAddress;

    public SesReservationEmailSender(SesV2Client sesClient, String fromAddress) {
        this.sesClient = sesClient;
        this.fromAddress = fromAddress;
    }

    @Override
    public String send(ReservationEmail email) {
        return sesClient.sendEmail(SendEmailRequest.builder()
                        .fromEmailAddress(fromAddress)
                        .destination(Destination.builder().toAddresses(email.recipient()).build())
                        .content(EmailContent.builder()
                                .simple(Message.builder()
                                        .subject(Content.builder().data(email.subject()).build())
                                        .body(Body.builder().text(Content.builder().data(email.body()).build()).build())
                                        .build())
                                .build())
                        .build())
                .messageId();
    }
}
