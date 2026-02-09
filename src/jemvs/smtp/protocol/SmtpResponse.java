package jemvs.smtp.protocol;


import java.util.Objects;

public final class SmtpResponse {

    private final int code;
    private final String message;
    private final SmtpPhase phase;

    // Constructs an SMTP response with full context.

    public SmtpResponse(int code, String message, SmtpPhase phase) {
        if (code < 100 || code > 599) {
            throw new IllegalArgumentException(
                    "Invalid SMTP code: " + code + " (must be 100-599)"
            );
        }

        this.code = code;
        this.message = Objects.requireNonNull(message, "Message cannot be null (use empty string)");
        this.phase = Objects.requireNonNull(phase, "Phase cannot be null");
    }

    // Returns the numeric SMTP response code.

    public int getCode() {
        return code;
    }

    // Returns the human-readable message portion of the response.
    public String getMessage() {
        return message;
    }

    // Returns the SMTP dialogue phase during which this response was received.

    public SmtpPhase getPhase() {
        return phase;
    }

    // Returns the response code class (first digit of the code).

    public int getCodeClass() {
        return code / 100;
    }

    public boolean isPositiveCompletion() {
        return getCodeClass() == 2;
    }

    public boolean isTransientNegative() {
        return getCodeClass() == 4;
    }

    public boolean isPermanentNegative() {
        return getCodeClass() == 5;
    }

    @Override
    public String toString() {
        return String.format("[%s] %d %s", phase, code, message);
    }

    /**
     * Compares this response to another for equality.
     * Two responses are equal if they have the same code, message, and phase.
     */

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        SmtpResponse that = (SmtpResponse) obj;
        return code == that.code &&
                message.equals(that.message) &&
                phase == that.phase;
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, message, phase);
    }
}