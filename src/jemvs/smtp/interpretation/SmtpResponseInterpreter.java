package jemvs.smtp.interpretation;

import jemvs.smtp.protocol.SmtpPhase;
import jemvs.smtp.protocol.SmtpResponse;
import jemvs.smtp.result.SmtpVerificationResult;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class SmtpResponseInterpreter {

    public SmtpResponseInterpreter() {
        // Stateless - no initialization required
    }

    public SmtpVerificationResult interpret(List<SmtpResponse> responses) {
        Objects.requireNonNull(responses, "responses must not be null");

        if (responses.isEmpty()) {
            return SmtpVerificationResult.indeterminate(
                    null,
                    "Empty response collection - session failed to initiate or collapsed immediately"
            );
        }

        // RCPT TO is the authoritative phase for recipient verification
        Optional<SmtpResponse> rcptToResponse = findResponseByPhase(responses, SmtpPhase.RCPT_TO);

        if (rcptToResponse.isPresent()) {
            // Decisive interpretation: RCPT TO response exists
            return interpretRcptToResponse(rcptToResponse.get());
        }

        // RCPT TO not reached - determine why session terminated prematurely
        return interpretIncompleteSession(responses);
    }

    private SmtpVerificationResult interpretRcptToResponse(SmtpResponse rcptTo) {
        int code = rcptTo.getCode();

        // 2xx: Positive completion
        if (code >= 200 && code < 300) {
            String diagnostic = String.format(
                    "RCPT TO accepted with code %d: %s",
                    code,
                    rcptTo.getMessage()
            );
            return SmtpVerificationResult.accepted(false, diagnostic);
        }

        // 4xx: Transient negative completion
        if (code >= 400 && code < 500) {
            String diagnostic = String.format(
                    "RCPT TO returned transient failure %d: %s",
                    code,
                    rcptTo.getMessage()
            );
            return SmtpVerificationResult.temporaryFailure(diagnostic);
        }

        // 5xx: Permanent negative completion
        if (code >= 500 && code < 600) {
            String diagnostic = String.format(
                    "RCPT TO permanently rejected with code %d: %s",
                    code,
                    rcptTo.getMessage()
            );
            return SmtpVerificationResult.rejected(diagnostic);
        }

        // Non-standard code: cannot interpret with certainty
        String diagnostic = String.format(
                "RCPT TO returned non-standard code %d: %s - unable to interpret definitively",
                code,
                rcptTo.getMessage()
        );
        return SmtpVerificationResult.indeterminate(SmtpPhase.RCPT_TO, diagnostic);
    }

    private SmtpVerificationResult interpretIncompleteSession(List<SmtpResponse> responses) {
        // Analyze GREETING phase
        Optional<SmtpResponse> greeting = findResponseByPhase(responses, SmtpPhase.GREETING);
        if (greeting.isPresent()) {
            int greetingCode = greeting.get().getCode();
            if (greetingCode < 200 || greetingCode >= 400) {
                String diagnostic = String.format(
                        "Connection rejected at GREETING phase with code %d: %s",
                        greetingCode,
                        greeting.get().getMessage()
                );
                return SmtpVerificationResult.blocked(SmtpPhase.GREETING, diagnostic);
            }
        }

        // Analyze HELO/EHLO phase
        Optional<SmtpResponse> helo = findResponseByPhase(responses, SmtpPhase.HELO);
        if (helo.isPresent()) {
            int heloCode = helo.get().getCode();
            if (heloCode < 200 || heloCode >= 400) {
                String diagnostic = String.format(
                        "HELO/EHLO rejected with code %d: %s - common anti-verification measure",
                        heloCode,
                        helo.get().getMessage()
                );
                return SmtpVerificationResult.blocked(SmtpPhase.HELO, diagnostic);
            }
        }

        // Analyze MAIL FROM phase
        Optional<SmtpResponse> mailFrom = findResponseByPhase(responses, SmtpPhase.MAIL_FROM);
        if (mailFrom.isPresent()) {
            int mailFromCode = mailFrom.get().getCode();
            if (mailFromCode < 200 || mailFromCode >= 400) {
                String diagnostic = String.format(
                        "MAIL FROM rejected with code %d: %s - sender validation failed",
                        mailFromCode,
                        mailFrom.get().getMessage()
                );
                return SmtpVerificationResult.blocked(SmtpPhase.MAIL_FROM, diagnostic);
            }
        }

        // Session incomplete with no clear failure point
        SmtpPhase lastPhase = responses.isEmpty()
                ? null
                : responses.get(responses.size() - 1).getPhase();

        String diagnostic = String.format(
                "SMTP session terminated before RCPT TO - last recorded phase: %s",
                lastPhase != null ? lastPhase : "NONE"
        );

        return SmtpVerificationResult.indeterminate(lastPhase, diagnostic);
    }


    private Optional<SmtpResponse> findResponseByPhase(List<SmtpResponse> responses, SmtpPhase phase) {
        return responses.stream()
                .filter(response -> response.getPhase() == phase)
                .findFirst();
    }
}