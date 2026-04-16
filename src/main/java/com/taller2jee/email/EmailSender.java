package com.taller2jee.email;

import com.taller2jee.common.model.EmailEvent;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;

public class EmailSender {
    private final String smtpHost;
    private final int smtpPort;
    private final String smtpUser;
    private final String smtpPassword;
    private final String fromEmail;

    public EmailSender(String smtpHost, int smtpPort, String smtpUser, String smtpPassword, String fromEmail) {
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.smtpUser = smtpUser;
        this.smtpPassword = smtpPassword;
        this.fromEmail = fromEmail;
    }

    public void sendResultEmail(EmailEvent event) {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", String.valueOf(smtpPort));

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpUser, smtpPassword);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromEmail));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(event.studentEmail()));
            message.setSubject("Resultado de evaluación");
            message.setText(
                    "Hola " + event.studentName() + ",\n\n" +
                    "Tu evaluación " + event.evaluationId() + " fue procesada con una calificación de " + event.score() + ".\n"
            );

            Transport.send(message);
        } catch (Exception e) {
            throw new RuntimeException("SMTP send operation failed", e);
        }
    }
}
