package jemvs.smtp.session;

import jemvs.smtp.transport.SmtpClient;
import jemvs.smtp.protocol.SmtpResponse;
import jemvs.smtp.protocol.SmtpPhase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class SmtpSession {

    private static final int NO_RESPONSE_CODE = -1;

    private final SmtpClient client;
    private final String heloDomain;
    private final String mailFrom;
    private final List<SmtpResponse> responses;

    public SmtpSession(SmtpClient client, String heloDomain, String mailFrom) {
        this.client = Objects.requireNonNull(client, "SmtpClient cannot be null");
        this.heloDomain = Objects.requireNonNull(heloDomain, "HELO domain cannot be null");
        this.mailFrom = Objects.requireNonNull(mailFrom, "MAIL FROM cannot be null");
        this.responses = new ArrayList<>();
    }

    public List<SmtpResponse> verify(String recipientEmail) throws IOException {
        Objects.requireNonNull(recipientEmail, "Recipient email cannot be null");

        try {
            readGreeting();
            sendHelo();
            sendMailFrom();
            sendRcptTo(recipientEmail);
        } finally {
            sendQuit();
        }

        return Collections.unmodifiableList(new ArrayList<>(responses));
    }

    private void readGreeting() throws IOException {
        recordResponse(client.readResponse(), SmtpPhase.GREETING);
    }

    private void sendHelo() throws IOException {
        client.sendCommand("HELO " + heloDomain);
        recordResponse(client.readResponse(), SmtpPhase.HELO);
    }

    private void sendMailFrom() throws IOException {
        client.sendCommand("MAIL FROM:<" + mailFrom + ">");
        recordResponse(client.readResponse(), SmtpPhase.MAIL_FROM);
    }

    private void sendRcptTo(String recipientEmail) throws IOException {
        client.sendCommand("RCPT TO:<" + recipientEmail + ">");
        recordResponse(client.readResponse(), SmtpPhase.RCPT_TO);
    }

    private void sendQuit() {
        try {
            client.sendCommand("QUIT");
            recordResponse(client.readResponse(), SmtpPhase.QUIT);
        } catch (IOException e) {
            // Explicitly record connection drop or silence
            responses.add(new SmtpResponse(
                    NO_RESPONSE_CODE,
                    "NO_RESPONSE (connection closed during QUIT)",
                    SmtpPhase.QUIT
            ));
        }
    }

    // Converts raw SMTP text into a structured, explicit observation.

    private void recordResponse(String rawResponse, SmtpPhase phase) {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            responses.add(new SmtpResponse(
                    NO_RESPONSE_CODE,
                    "NO_RESPONSE",
                    phase
            ));
            return;
        }

        String trimmed = rawResponse.trim();

        if (trimmed.length() >= 3) {
            try {
                int code = Integer.parseInt(trimmed.substring(0, 3));
                String message = trimmed.length() > 3
                        ? trimmed.substring(3).trim()
                        : "";
                responses.add(new SmtpResponse(code, message, phase));
                return;
            } catch (NumberFormatException ignored) {
                // Fall through to malformed handling
            }
        }

        // Malformed or non-standard SMTP response
        responses.add(new SmtpResponse(
                NO_RESPONSE_CODE,
                trimmed,
                phase
        ));
    }

    public List<SmtpResponse> getCollectedResponses() {
        return Collections.unmodifiableList(new ArrayList<>(responses));
    }
}
