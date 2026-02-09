package jemvs.result;

import jemvs.smtp.analysis.CatchAllDetectionResult;

import java.util.Objects;
import java.util.Optional;

public final class VerificationResult {

    private final VerificationStatus status;
    private final boolean syntaxValid;
    private final boolean domainResolvable;
    private final boolean smtpAccepted;
    private final CatchAllDetectionResult.Confidence catchAllConfidence;
    private final String diagnosticSummary;

    private VerificationResult(
            VerificationStatus status,
            boolean syntaxValid,
            boolean domainResolvable,
            boolean smtpAccepted,
            CatchAllDetectionResult.Confidence catchAllConfidence,
            String diagnosticSummary
    ) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.syntaxValid = syntaxValid;
        this.domainResolvable = domainResolvable;
        this.smtpAccepted = smtpAccepted;
        this.catchAllConfidence = catchAllConfidence;
        this.diagnosticSummary = diagnosticSummary;
    }


    public static VerificationResult valid(
            CatchAllDetectionResult.Confidence catchAllConfidence,
            String diagnosticSummary
    ) {
        return new VerificationResult(
                VerificationStatus.VALID,
                true,
                true,
                true,
                catchAllConfidence,
                diagnosticSummary
        );
    }

    public static VerificationResult catchAll(String diagnosticSummary) {
        return new VerificationResult(
                VerificationStatus.CATCH_ALL,
                true,
                true,
                true,
                CatchAllDetectionResult.Confidence.CONFIRMED,
                diagnosticSummary
        );
    }

    public static VerificationResult invalid(
            boolean syntaxValid,
            boolean domainResolvable,
            String diagnosticSummary) {
        return new VerificationResult(
                VerificationStatus.INVALID,
                syntaxValid,
                domainResolvable,
                false,
                CatchAllDetectionResult.Confidence.NOT_DETECTED,
                diagnosticSummary
        );
    }

    public static VerificationResult unknown(String diagnosticSummary) {
        return new VerificationResult(
                VerificationStatus.UNKNOWN,
                true,
                true,
                false,
                CatchAllDetectionResult.Confidence.INDETERMINATE,
                diagnosticSummary
        );
    }


// Accessors

    public VerificationStatus getStatus() {
        return status;
    }

    public boolean isSyntaxValid() {
        return syntaxValid;
    }

    public boolean isDomainResolvable() {
        return domainResolvable;
    }

    public boolean isSmtpAccepted() {
        return smtpAccepted;
    }

    public CatchAllDetectionResult.Confidence getCatchAllConfidence() {
        return catchAllConfidence;
    }

    public Optional<String> getDiagnosticSummary() {
        return Optional.ofNullable(diagnosticSummary);
    }

    //Object Identity

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        VerificationResult that = (VerificationResult) o;
        return syntaxValid == that.syntaxValid &&
                domainResolvable == that.domainResolvable &&
                smtpAccepted == that.smtpAccepted &&
                status == that.status &&
                catchAllConfidence == that.catchAllConfidence &&
                Objects.equals(diagnosticSummary, that.diagnosticSummary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                status,
                syntaxValid,
                domainResolvable,
                smtpAccepted,
                catchAllConfidence,
                diagnosticSummary
        );
    }

    @Override
    public String toString() {
        return "VerificationResult{" +
                "status=" + status +
                ", syntaxValid=" + syntaxValid +
                ", domainResolvable=" + domainResolvable +
                ", smtpAccepted=" + smtpAccepted +
                ", catchAllConfidence=" + catchAllConfidence +
                ", diagnosticSummary='" + diagnosticSummary + '\'' +
                '}';
    }
}