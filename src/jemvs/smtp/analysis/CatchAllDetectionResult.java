package jemvs.smtp.analysis;

import java.util.Objects;

public final class CatchAllDetectionResult {

    public enum Confidence {

        CONFIRMED,

        SUSPECTED,

        NOT_DETECTED,

        INDETERMINATE
    }

    private final Confidence confidence;

    private final String diagnosticSummary;

    private CatchAllDetectionResult(
            Confidence confidence,
            String diagnosticSummary) {

        this.confidence = Objects.requireNonNull(confidence, "confidence must not be null");

        this.diagnosticSummary = diagnosticSummary;
    }

    //Static Methods

    public static CatchAllDetectionResult confirmed(
            String diagnosticSummary) {

        return new CatchAllDetectionResult(
                Confidence.CONFIRMED,
                diagnosticSummary
        );
    }


    public static CatchAllDetectionResult suspected(String diagnosticSummary) {
        return new CatchAllDetectionResult(
                Confidence.SUSPECTED,
                diagnosticSummary
        );
    }

    public static CatchAllDetectionResult notDetected(String diagnosticSummary) {

        return new CatchAllDetectionResult(
                Confidence.NOT_DETECTED,
                diagnosticSummary
        );
    }


    public static CatchAllDetectionResult indeterminate(String diagnosticSummary) {
        return new CatchAllDetectionResult(
                Confidence.INDETERMINATE,
                diagnosticSummary
        );
    }

    //Accessors, only one is used.

    public Confidence getConfidence() {
        return confidence;
    }

    public String getDiagnosticSummary() {
        return diagnosticSummary;
    }

    public boolean isCatchAllDetected() {
        return confidence == Confidence.CONFIRMED
                || confidence == Confidence.SUSPECTED;
    }

    public boolean isConfirmed() {
        return confidence == Confidence.CONFIRMED;
    }



}