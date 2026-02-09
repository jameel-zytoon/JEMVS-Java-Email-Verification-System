package jemvs.smtp.result;

import jemvs.smtp.protocol.SmtpPhase;

import java.util.Objects;
import java.util.Optional;

public final class SmtpVerificationResult {


      // High-level semantic outcome of SMTP verification.

    public enum Outcome {

        ACCEPTED,

        REJECTED,

        INDETERMINATE

    }

    private final Outcome outcome;
    private final SmtpPhase decisivePhase;
    private final boolean catchAllSuspected;
    private final String diagnosticSummary;

    // Private constructor. Use static factory methods.

    private SmtpVerificationResult(
            Outcome outcome,
            SmtpPhase decisivePhase,
            boolean catchAllSuspected,
            String diagnosticSummary) {

        this.outcome = Objects.requireNonNull(outcome, "outcome must not be null");
        this.decisivePhase = decisivePhase;
        this.catchAllSuspected = catchAllSuspected;
        this.diagnosticSummary = diagnosticSummary;
    }

    // Static Factory Methods

    public static SmtpVerificationResult accepted(
            boolean catchAllSuspected,
            String diagnosticSummary) {

        return new SmtpVerificationResult(
                Outcome.ACCEPTED,
                SmtpPhase.RCPT_TO,
                catchAllSuspected,
                diagnosticSummary
        );
    }

    public static SmtpVerificationResult temporaryFailure(String diagnosticSummary) {
        return new SmtpVerificationResult(
                Outcome.INDETERMINATE,
                SmtpPhase.RCPT_TO,
                false,
                diagnosticSummary
        );
    }


    public static SmtpVerificationResult rejected(String diagnosticSummary) {
        return new SmtpVerificationResult(
                Outcome.REJECTED,
                SmtpPhase.RCPT_TO,
                false,
                diagnosticSummary
        );
    }


    public static SmtpVerificationResult blocked(
            SmtpPhase blockingPhase,
            String diagnosticSummary) {

        return new SmtpVerificationResult(
                Outcome.INDETERMINATE,
                blockingPhase,
                false,
                diagnosticSummary
        );
    }


    public static SmtpVerificationResult indeterminate(
            SmtpPhase phase,
            String diagnosticSummary) {

        return new SmtpVerificationResult(
                Outcome.INDETERMINATE,
                phase,
                false,
                diagnosticSummary
        );
    }

   // Accessors

    public Outcome getOutcome() {
        return outcome;
    }

    //Not used, written for possible future improvements

    public Optional<SmtpPhase> getDecisivePhase() {
        return Optional.ofNullable(decisivePhase);
    }

    public Optional<String> getDiagnosticSummary() {
        return Optional.ofNullable(diagnosticSummary);
    }

    // Object Overrides

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SmtpVerificationResult that = (SmtpVerificationResult) o;
        return catchAllSuspected == that.catchAllSuspected &&
                outcome == that.outcome &&
                decisivePhase == that.decisivePhase &&
                Objects.equals(diagnosticSummary, that.diagnosticSummary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(outcome, decisivePhase, catchAllSuspected, diagnosticSummary);
    }

    @Override
    public String toString() {
        return "SmtpVerificationResult{" +
                "outcome=" + outcome +
                ", decisivePhase=" + decisivePhase +
                ", catchAllSuspected=" + catchAllSuspected +
                ", diagnosticSummary='" + diagnosticSummary + '\'' +
                '}';
    }
}
