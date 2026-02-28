package com.reddiax.loghealer.monitoring.service;

import com.reddiax.loghealer.monitoring.entity.AlertHistory;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Slf4j
public class MonitoringEmailService {

    @Value("${loghealer.email.sendgrid-apikey:}")
    private String sendGridApiKey;

    @Value("${loghealer.email.from:no-reply@reddia-x.com}")
    private String fromEmail;

    @Value("${loghealer.monitoring.email.enabled:false}")
    private boolean emailEnabled;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public void sendAlertEmail(AlertHistory alert, List<String> recipients) {
        if (!emailEnabled || sendGridApiKey.isBlank()) {
            log.info("Email disabled or API key not configured. Alert would be sent to: {}", recipients);
            return;
        }

        String subject = String.format("[ALERT] %s - %s", 
                alert.getAlertType().name(), 
                alert.getService().getName());

        String htmlContent = buildAlertEmailContent(alert);

        sendEmail(recipients, subject, htmlContent);
    }

    public void sendResolutionEmail(AlertHistory alert, List<String> recipients) {
        if (!emailEnabled || sendGridApiKey.isBlank()) {
            log.info("Email disabled or API key not configured. Resolution notification would be sent to: {}", recipients);
            return;
        }

        String subject = String.format("[RESOLVED] %s - %s", 
                alert.getAlertType().name(), 
                alert.getService().getName());

        String htmlContent = buildResolutionEmailContent(alert);

        sendEmail(recipients, subject, htmlContent);
    }

    private String buildAlertEmailContent(AlertHistory alert) {
        return String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <style>
                        body { font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f5f5f5; }
                        .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                        .header { background: #dc3545; color: white; padding: 20px; text-align: center; }
                        .content { padding: 20px; }
                        .info-row { margin: 10px 0; padding: 10px; background: #f8f9fa; border-radius: 4px; }
                        .label { font-weight: bold; color: #666; }
                        .footer { padding: 20px; text-align: center; color: #999; font-size: 12px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>Service Alert</h1>
                        </div>
                        <div class="content">
                            <div class="info-row">
                                <span class="label">Service:</span> %s
                            </div>
                            <div class="info-row">
                                <span class="label">Alert Type:</span> %s
                            </div>
                            <div class="info-row">
                                <span class="label">Message:</span> %s
                            </div>
                            <div class="info-row">
                                <span class="label">Triggered At:</span> %s
                            </div>
                            <div class="info-row">
                                <span class="label">URL:</span> %s
                            </div>
                        </div>
                        <div class="footer">
                            LogHealer Monitoring System
                        </div>
                    </div>
                </body>
                </html>
                """,
                alert.getService().getName(),
                alert.getAlertType().name(),
                alert.getMessage(),
                FORMATTER.format(alert.getTriggeredAt()),
                alert.getService().getFullHealthUrl()
        );
    }

    private String buildResolutionEmailContent(AlertHistory alert) {
        return String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <style>
                        body { font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f5f5f5; }
                        .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                        .header { background: #28a745; color: white; padding: 20px; text-align: center; }
                        .content { padding: 20px; }
                        .info-row { margin: 10px 0; padding: 10px; background: #f8f9fa; border-radius: 4px; }
                        .label { font-weight: bold; color: #666; }
                        .footer { padding: 20px; text-align: center; color: #999; font-size: 12px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>Alert Resolved</h1>
                        </div>
                        <div class="content">
                            <div class="info-row">
                                <span class="label">Service:</span> %s
                            </div>
                            <div class="info-row">
                                <span class="label">Alert Type:</span> %s
                            </div>
                            <div class="info-row">
                                <span class="label">Original Message:</span> %s
                            </div>
                            <div class="info-row">
                                <span class="label">Triggered At:</span> %s
                            </div>
                            <div class="info-row">
                                <span class="label">Resolved At:</span> %s
                            </div>
                        </div>
                        <div class="footer">
                            LogHealer Monitoring System
                        </div>
                    </div>
                </body>
                </html>
                """,
                alert.getService().getName(),
                alert.getAlertType().name(),
                alert.getMessage(),
                FORMATTER.format(alert.getTriggeredAt()),
                alert.getResolvedAt() != null ? FORMATTER.format(alert.getResolvedAt()) : "N/A"
        );
    }

    private void sendEmail(List<String> recipients, String subject, String htmlContent) {
        try {
            Email from = new Email(fromEmail, "LogHealer Monitoring");
            Content content = new Content("text/html", htmlContent);

            Mail mail = new Mail();
            mail.setFrom(from);
            mail.setSubject(subject);
            mail.addContent(content);

            Personalization personalization = new Personalization();
            recipients.forEach(email -> personalization.addTo(new Email(email.trim())));
            mail.addPersonalization(personalization);

            SendGrid sg = new SendGrid(sendGridApiKey);
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            Response response = sg.api(request);

            if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                log.info("Email sent successfully to {} recipients", recipients.size());
            } else {
                log.error("Failed to send email. Status: {}, Body: {}", response.getStatusCode(), response.getBody());
            }
        } catch (IOException e) {
            log.error("Error sending email: {}", e.getMessage());
        }
    }
}
