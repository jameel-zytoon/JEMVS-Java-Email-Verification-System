package jemvs.verification;

import jemvs.result.VerificationResult;
import jemvs.smtp.analysis.CatchAllDetectionResult;
import jemvs.syntax.EmailSyntaxValidator;
import jemvs.syntax.SyntaxResult;

import jemvs.dns.resolver.DnsResolver;
import jemvs.dns.result.DnsResolutionResult;

import jemvs.smtp.transport.SmtpClient;
import jemvs.smtp.session.SmtpSession;
import jemvs.smtp.protocol.SmtpResponse;
import jemvs.smtp.interpretation.SmtpResponseInterpreter;
import jemvs.smtp.result.SmtpVerificationResult;

import jemvs.smtp.analysis.CatchAllDetector;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Orchestrates the complete email verification process.
 *
 * <p>This pipeline coordinates multiple verification stages
 * (syntax → DNS → SMTP → analysis) while preserving strict
 * separation of concerns and conservative interpretation.
 *
 * <p>The pipeline enforces early exits when verification becomes
 * impossible or meaningless, preventing unnecessary probing.
 */

public final class VerificationPipeline {

    private final EmailSyntaxValidator syntaxValidator;
    private final DnsResolver dnsResolver;
    private final SmtpResponseInterpreter smtpInterpreter;
    private final CatchAllDetector catchAllDetector;

    private final String heloDomain;
    private final String mailFrom;




    public VerificationPipeline(
            EmailSyntaxValidator syntaxValidator,
            DnsResolver dnsResolver,
            SmtpResponseInterpreter smtpInterpreter,
            CatchAllDetector catchAllDetector,
            String heloDomain,
            String mailFrom
    ) {
        this.syntaxValidator = Objects.requireNonNull(syntaxValidator);
        this.dnsResolver = Objects.requireNonNull(dnsResolver);
        this.smtpInterpreter = Objects.requireNonNull(smtpInterpreter);
        this.catchAllDetector = Objects.requireNonNull(catchAllDetector);
        this.heloDomain = Objects.requireNonNull(heloDomain);
        this.mailFrom = Objects.requireNonNull(mailFrom);
    }


    // Executes the full verification pipeline for an email address.

    public VerificationResult verify(String email) {
        Objects.requireNonNull(email, "email must not be null");

        /* ---------- Stage 1: Syntax ---------- */

        SyntaxResult syntaxResult = syntaxValidator.validate(email);
        if (!syntaxResult.isValid()) {
            return VerificationResult.invalid(
                    false,
                    false,
                    "Invalid email syntax"
            );
        }

        /* ---------- Stage 2: DNS ---------- */

        String domain = syntaxResult.getDomain();
        DnsResolutionResult dnsResult = dnsResolver.resolve(domain);

        if (!dnsResult.hasMailHosts()) {
            return VerificationResult.invalid(
                    true,
                    false,
                    "Domain has no valid MX/A mail hosts"
            );
        }

        /* ---------- Stage 3: SMTP ---------- */

        List<SmtpResponse> smtpResponses;

        try (SmtpClient client = new SmtpClient(dnsResult.getPrimaryMailHost())) {

            client.connect();

            SmtpSession session = new SmtpSession(
                    client,
                    heloDomain,
                    mailFrom
            );

            smtpResponses = session.verify(email);

        } catch (IOException e) {
            return VerificationResult.unknown(
                    "SMTP transport failure: " + e.getMessage()
            );
        }

        /* ---------- Stage 4: SMTP Interpretation ---------- */

        SmtpVerificationResult smtpResult =
                smtpInterpreter.interpret(smtpResponses);

        /* ---------- Stage 5: Behavioral Analysis ---------- */

        CatchAllDetectionResult catchAllResult;

        if (smtpResult.getOutcome() == SmtpVerificationResult.Outcome.ACCEPTED) {
            catchAllResult = catchAllDetector.analyzeMultiProbe(
                    smtpResponses,
                    dnsResult.getPrimaryMailHost(),
                    domain
            );
        }
        else {
            // No need to probe if already rejected
            catchAllResult = CatchAllDetectionResult.notDetected(
                    "Primary verification rejected"
            );
        }

        /* ---------- Final Aggregation ---------- */

        // Determine final status based on SMTP outcome and catch-all detection
        switch (smtpResult.getOutcome()) {
            case ACCEPTED:
                // Check if catch-all was confirmed
                if (catchAllResult.getConfidence() == CatchAllDetectionResult.Confidence.CONFIRMED) {
                    return VerificationResult.catchAll(
                            smtpResult.getDiagnosticSummary().orElse(null)
                    );
                } else {
                    // Server is selective - address is VALID
                    return VerificationResult.valid(
                            catchAllResult.getConfidence(),
                            smtpResult.getDiagnosticSummary().orElse(null)
                    );
                }

            case REJECTED:
                // Permanent rejection - INVALID
                return VerificationResult.invalid(
                        true,
                        true,
                        smtpResult.getDiagnosticSummary().orElse(null)
                );

            case INDETERMINATE:
                // Cannot determine - UNKNOWN
                return VerificationResult.unknown(
                        smtpResult.getDiagnosticSummary().orElse(null)
                );

            default:
                // Defensive fallback
                return VerificationResult.unknown(
                        "Unhandled verification outcome"
                );
        }
    }
}