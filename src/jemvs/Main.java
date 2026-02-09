package jemvs;

import jemvs.dns.resolver.DnsResolver;
import jemvs.result.VerificationResult;
import jemvs.result.VerificationStatus;
import jemvs.smtp.analysis.CatchAllDetectionResult;
import jemvs.smtp.analysis.CatchAllDetector;
import jemvs.smtp.interpretation.SmtpResponseInterpreter;
import jemvs.syntax.EmailSyntaxValidator;
import jemvs.verification.VerificationPipeline;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Command-line interface for the Java Email Verification System (JEMVS).
 *
 * <p>This application provides a menu-driven interface with three modes:</p>
 * <ul>
 *   <li><strong>Interactive Mode:</strong> Verify emails one at a time via console input</li>
 *   <li><strong>Batch Mode:</strong> Verify multiple emails from a file</li>
 *   <li><strong>Help:</strong> Display usage information and documentation</li>
 * </ul>
 *
 * <h2>Output Statuses (Simplified):</h2>
 * <ul>
 *   <li><strong>VALID:</strong> Email verified and server is selective (not catch-all)</li>
 *   <li><strong>CATCH-ALL:</strong> Email accepted but server accepts all addresses</li>
 *   <li><strong>INVALID:</strong> Email failed verification</li>
 *   <li><strong>UNKNOWN:</strong> Cannot determine validity (includes timeouts, blocks)</li>
 * </ul>
 *
 * @author Jameel Zytoon
 *
 */
public final class Main {

    // ANSI color codes for terminal output
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_MAGENTA = "\u001B[35m";
    private static final String ANSI_BOLD = "\u001B[1m";

    // Default configuration values
    private static final String DEFAULT_HELO_DOMAIN = "verification.localhost";
    private static final String DEFAULT_MAIL_FROM = "verifier@localhost";
    private static final int DEFAULT_DNS_TIMEOUT_MS = 5000;

    /**
     * Main entry point for the JEMVS application.
     * <p>Displays a menu-driven interface allowing users to select
     * their desired operation mode without requiring command-line arguments.</p>
     */

    public static void main(String[] args) {
        printBanner();
        runMainMenu();
    }

    // Displays and handles the main menu loop.

    private static void runMainMenu() {
        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        while (running) {
            printMainMenu();

            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1":
                    runInteractiveMode();
                    break;

                case "2":
                    runBatchMode(scanner);
                    break;

                case "3":
                    printHelp();
                    break;

                case "4":
                    running = false;
                    System.out.println("\n" + ANSI_CYAN + "Goodbye!" + ANSI_RESET);
                    break;

                default:
                    System.out.println(ANSI_RED + "Invalid choice. Please enter 1-4." +
                            ANSI_RESET + "\n");
                    break;
            }
        }

        scanner.close();
    }

    // Prints the application banner

    private static void printBanner() {
        System.out.println(ANSI_CYAN + ANSI_BOLD);
        System.out.println("╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║                                                           ║");
        System.out.println("║         JEMVS - Java Email Verification System            ║");
        System.out.println("║                                                           ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝");
        System.out.println(ANSI_RESET);
    }
    // Main Menu
    private static void printMainMenu() {
        System.out.println(ANSI_BOLD + "Main Menu:" + ANSI_RESET);
        System.out.println("  1. Interactive Mode - Verify emails one at a time");
        System.out.println("  2. Batch Mode - Verify emails from file");
        System.out.println("  3. Help - Display usage information");
        System.out.println("  4. Exit");
        System.out.println();
        System.out.print(ANSI_BOLD + "Enter choice (1-4): " + ANSI_RESET);
    }

    /**
     * Runs interactive mode where users can verify emails one at a time.
     */

    private static void runInteractiveMode() {
        System.out.println("\n" + ANSI_BLUE + "═══ Interactive Mode ═══" + ANSI_RESET);
        System.out.println("Enter email addresses to verify (or 'menu' to return to main menu)");
        System.out.println("Type 'help' for interactive commands\n");

        VerificationPipeline pipeline = createDefaultPipeline();
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print(ANSI_BOLD + "Email> " + ANSI_RESET);
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                continue;
            }

            if (input.equalsIgnoreCase("menu") || input.equalsIgnoreCase("back")) {
                System.out.println();
                break;
            }

            if (input.equalsIgnoreCase("help")) {
                printInteractiveHelp();
                continue;
            }

            if (input.equalsIgnoreCase("stats")) {
                printSystemInfo();
                continue;
            }

            // Verify the email
            verifyAndPrintDetailed(pipeline, input);
            System.out.println();
        }
    }

    // Prints help information for interactive mode

    private static void printInteractiveHelp() {
        System.out.println("\n" + ANSI_BOLD + "Interactive Mode Commands:" + ANSI_RESET);
        System.out.println("  <email>  - Verify the specified email address");
        System.out.println("  help     - Show this help message");
        System.out.println("  stats    - Display system information");
        System.out.println("  menu     - Return to main menu");
        System.out.println("  back     - (same as menu)\n");
    }


    private static void printSystemInfo() {
        System.out.println("\n" + ANSI_BOLD + "System Information:" + ANSI_RESET);
        System.out.println("  Java Version:     " + System.getProperty("java.version"));
        System.out.println("  Java VM:          " + System.getProperty("java.vm.name"));
        System.out.println("  Operating System: " + System.getProperty("os.name"));
        System.out.println("\n" + ANSI_BOLD + "Configuration:" + ANSI_RESET);
        System.out.println("  HELO Domain:      " + DEFAULT_HELO_DOMAIN);
        System.out.println("  MAIL FROM:        " + DEFAULT_MAIL_FROM);
        System.out.println("  DNS Timeout:      " + DEFAULT_DNS_TIMEOUT_MS + "ms");
        System.out.println("  Detection Mode:   Multi-Probe (2 probes default)");
        System.out.println();
    }

    // Runs batch mode where users can verify multiple emails from a file

    private static void runBatchMode(Scanner scanner) {
        System.out.println("\n" + ANSI_BLUE + "═══ Batch Mode ═══" + ANSI_RESET);
        System.out.print("Enter path to email file (or 'menu' to cancel): ");

        String filePath = scanner.nextLine().trim();

        if (filePath.equalsIgnoreCase("menu") || filePath.equalsIgnoreCase("back")) {
            System.out.println();
            return;
        }

        if (filePath.isEmpty()) {
            System.out.println(ANSI_RED + "Error: File path cannot be empty" + ANSI_RESET + "\n");
            return;
        }

        try {
            processBatchFile(filePath);
        } catch (IOException e) {
            System.out.println(ANSI_RED + "Error reading file: " + e.getMessage() +
                    ANSI_RESET + "\n");
        }
    }

    // Processes a batch file containing email addresses.

    private static void processBatchFile(String filePath) throws IOException {
        // Read all emails from file
        List<String> emails = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    emails.add(line);
                }
            }
        }

        if (emails.isEmpty()) {
            System.out.println(ANSI_YELLOW + "No email addresses found in file." +
                    ANSI_RESET + "\n");
            return;
        }

        System.out.println("Found " + ANSI_BOLD + emails.size() + ANSI_RESET +
                " email(s) to verify\n");

        // Verify all emails
        VerificationPipeline pipeline = createDefaultPipeline();
        int valid = 0, catchAll = 0, invalid = 0, unknown = 0;

        for (int i = 0; i < emails.size(); i++) {
            String email = emails.get(i);
            System.out.println(ANSI_BOLD + "[" + (i + 1) + "/" + emails.size() + "]" +
                    ANSI_RESET + " " + email);

            VerificationResult result = pipeline.verify(email);
            SimplifiedStatus status = determineSimplifiedStatus(result);

            printResultCompact(status, result);

            // Update statistics
            switch (status) {
                case VALID:
                    valid++;
                    break;
                case CATCH_ALL:
                    catchAll++;
                    break;
                case INVALID:
                    invalid++;
                    break;
                case UNKNOWN:
                    unknown++;
                    break;
            }

            System.out.println();
        }

        // Print summary statistics
        printBatchSummary(emails.size(), valid, catchAll, invalid, unknown);
    }

    // Prints summary statistics for batch verification.

    private static void printBatchSummary(int total, int valid, int catchAll,
                                          int invalid, int unknown) {
        System.out.println(ANSI_BOLD + "═══════════════════════════════════════" + ANSI_RESET);
        System.out.println(ANSI_BOLD + "BATCH VERIFICATION SUMMARY" + ANSI_RESET);
        System.out.println(ANSI_BOLD + "═══════════════════════════════════════" + ANSI_RESET);
        System.out.println("Total Verified:  " + total);
        System.out.println(ANSI_GREEN + "Valid:           " + valid +
                String.format(" (%.1f%%)", 100.0 * valid / total) + ANSI_RESET);
        System.out.println(ANSI_MAGENTA + "Catch-All:       " + catchAll +
                String.format(" (%.1f%%)", 100.0 * catchAll / total) + ANSI_RESET);
        System.out.println(ANSI_RED + "Invalid:         " + invalid +
                String.format(" (%.1f%%)", 100.0 * invalid / total) + ANSI_RESET);
        System.out.println(ANSI_YELLOW + "Unknown:         " + unknown +
                String.format(" (%.1f%%)", 100.0 * unknown / total) + ANSI_RESET);
        System.out.println(ANSI_BOLD + "═══════════════════════════════════════" + ANSI_RESET);
        System.out.println();



    }


    // Change access modifier

    private static void printHelp() {
        System.out.println("\n" + ANSI_BOLD + "═══════════════════════════════════════" + ANSI_RESET);
        System.out.println(ANSI_BOLD + "JEMVS HELP & DOCUMENTATION" + ANSI_RESET);
        System.out.println(ANSI_BOLD + "═══════════════════════════════════════" + ANSI_RESET);

        System.out.println("\n" + ANSI_BOLD + "OVERVIEW:" + ANSI_RESET);
        System.out.println("JEMVS performs comprehensive email verification through multiple stages:");
        System.out.println("  1. Syntax Validation (RFC 5322)");
        System.out.println("  2. DNS Resolution (MX/A records)");
        System.out.println("  3. SMTP Protocol Execution");
        System.out.println("  4. SMTP Response Interpretation");
        System.out.println("  5. Behavioral Analysis (Multi-Probe Catch-All Detection)");

        System.out.println("\n" + ANSI_BOLD + "OPERATING MODES:" + ANSI_RESET);
        System.out.println("  Interactive Mode");
        System.out.println("    - Verify emails one at a time");
        System.out.println("    - Detailed results");
        System.out.println("    - Useful for testing and exploration");
        System.out.println();
        System.out.println("  Batch Mode");
        System.out.println("    - Verify multiple emails from a file");
        System.out.println("    - Compact per-email results");
        System.out.println("    - Summary statistics at completion");
        System.out.println("    - File format: One email per line, # for comments");

        System.out.println("\n" + ANSI_BOLD + "VERIFICATION STATUSES (SIMPLIFIED):" + ANSI_RESET);
        System.out.println("  " + ANSI_GREEN + "VALID" + ANSI_RESET +
                "        - Email verified, server is selective");
        System.out.println("                High confidence this address is valid");
        System.out.println();
        System.out.println("  " + ANSI_MAGENTA + "CATCH-ALL" + ANSI_RESET +
                "     - Server accepts ALL addresses");
        System.out.println("                Cannot confirm mailbox actually exists");
        System.out.println("                Verified via multi-probe analysis");
        System.out.println();
        System.out.println("  " + ANSI_RED + "INVALID" + ANSI_RESET +
                "       - Email failed verification");
        System.out.println("                (Invalid syntax, domain, or mailbox)");
        System.out.println();
        System.out.println("  " + ANSI_YELLOW + "UNKNOWN" + ANSI_RESET +
                "       - Cannot determine validity");
        System.out.println("                (Timeout, blocking, temporary failure)");

        System.out.println("\n" + ANSI_BOLD + "CATCH-ALL DETECTION:" + ANSI_RESET);
        System.out.println("  Multi-probe detection sends 2 random addresses to the server.");
        System.out.println();
        System.out.println("  If ALL random probes accept → CATCH-ALL (confirmed)");
        System.out.println("  If ANY random probe rejects → VALID (server is selective)");
        System.out.println("  If probes fail/inconclusive → Status based on primary verification");
        System.out.println();
        System.out.println("  Note: CATCH-ALL means the server accepts any address at that domain,");
        System.out.println("        so we cannot confirm the specific mailbox exists.");

        System.out.println("\n" + ANSI_BOLD + "BATCH FILE FORMAT:" + ANSI_RESET);
        System.out.println("  Create a text file with one email per line:");
        System.out.println();
        System.out.println("    # This is a comment");
        System.out.println("    alice@example.com");
        System.out.println("    bob@company.org");
        System.out.println("    charlie@domain.net");
        System.out.println();
        System.out.println("  Lines starting with # are ignored");
        System.out.println("  Empty lines are ignored");

        System.out.println("\n" + ANSI_BOLD + "CONFIGURATION:" + ANSI_RESET);
        System.out.println("  HELO Domain:     " + DEFAULT_HELO_DOMAIN);
        System.out.println("  MAIL FROM:       " + DEFAULT_MAIL_FROM);
        System.out.println("  DNS Timeout:     " + DEFAULT_DNS_TIMEOUT_MS + "ms");
        System.out.println("  SMTP Timeouts:   10s connect, 15s read");
        System.out.println("  Probe Count:     2 (default)");

        System.out.println("\n" + ANSI_BOLD + "IMPORTANT NOTES:" + ANSI_RESET);
        System.out.println("  • VALID = email verified AND server is selective (not catch-all)");
        System.out.println("  • CATCH-ALL = server accepts all addresses (cannot confirm mailbox)");
        System.out.println("  • Multi-probe detection performs additional SMTP sessions");
        System.out.println("  • Verification requires outbound port 25 access");
        System.out.println("  • Some servers block verification probes");
        System.out.println("  • Results are conservative and honest about uncertainty");

        System.out.println("\n" + ANSI_BOLD + "EXAMPLES:" + ANSI_RESET);
        System.out.println("  Interactive Mode:");
        System.out.println("    Select option 1, then enter emails one at a time");
        System.out.println();
        System.out.println("  Batch Mode:");
        System.out.println("    Select option 2, enter 'emails.txt' as file path");
        System.out.println("    Program verifies all addresses and shows summary");

        System.out.println("\n" + ANSI_BOLD + "═══════════════════════════════════════" + ANSI_RESET);
        System.out.println();
    }

    // Creates a verification pipeline with default configuration.

    private static VerificationPipeline createDefaultPipeline() {
        EmailSyntaxValidator syntaxValidator = new EmailSyntaxValidator();
        DnsResolver dnsResolver = new DnsResolver(DEFAULT_DNS_TIMEOUT_MS);
        SmtpResponseInterpreter smtpInterpreter = new SmtpResponseInterpreter();
        CatchAllDetector catchAllDetector = new CatchAllDetector(
                DEFAULT_HELO_DOMAIN,
                DEFAULT_MAIL_FROM
        );

        return new VerificationPipeline(
                syntaxValidator,
                dnsResolver,
                smtpInterpreter,
                catchAllDetector,
                DEFAULT_HELO_DOMAIN,
                DEFAULT_MAIL_FROM
        );
    }

    // Verifies an email and prints detailed results.

    private static void verifyAndPrintDetailed(VerificationPipeline pipeline, String email) {
        long startTime = System.currentTimeMillis();

        try {
            VerificationResult result = pipeline.verify(email);
            long duration = System.currentTimeMillis() - startTime;

            SimplifiedStatus status = determineSimplifiedStatus(result);
            printResultDetailed(status, result, duration);

        } catch (Exception e) {
            System.out.println(ANSI_RED + "Error during verification: " +
                    e.getMessage() + ANSI_RESET);
            e.printStackTrace();
        }
    }


    private enum SimplifiedStatus {
        VALID,         // Verified and NOT catch-all
        CATCH_ALL,     // Verified but IS catch-all
        INVALID,       // Failed verification
        UNKNOWN        // Cannot determine (includes BLOCKED)
    }

    // Determines the simplified status from the verification result.

    private static SimplifiedStatus determineSimplifiedStatus(VerificationResult result) {
        VerificationStatus status = result.getStatus();

        // Direct mapping from VerificationStatus to SimplifiedStatus
        switch (status) {
            case VALID:
                return SimplifiedStatus.VALID;

            case CATCH_ALL:
                return SimplifiedStatus.CATCH_ALL;

            case INVALID:
                return SimplifiedStatus.INVALID;

            case UNKNOWN:
                return SimplifiedStatus.UNKNOWN;

            default:
                // Defensive fallback - should never happen
                return SimplifiedStatus.UNKNOWN;
        }
    }

    // Prints a detailed verification result.

    private static void printResultDetailed(SimplifiedStatus status,
                                            VerificationResult result,
                                            long durationMs) {
        System.out.println(ANSI_BOLD + "─── Verification Result ───" + ANSI_RESET);

        // Status with color
        System.out.print("Status:           ");
        printColoredSimplifiedStatus(status);
        System.out.println();

        // Stage results
        System.out.println("Syntax Valid:     " + formatBoolean(result.isSyntaxValid()));
        System.out.println("Domain Resolves:  " + formatBoolean(result.isDomainResolvable()));
        System.out.println("SMTP Accepted:    " + formatBoolean(result.isSmtpAccepted()));

        // Catch-all detection (only for VALID or CATCH_ALL)
        if (status == SimplifiedStatus.VALID || status == SimplifiedStatus.CATCH_ALL) {
            System.out.print("Catch-All:        ");
            printCatchAllConfidence(result.getCatchAllConfidence());
            System.out.println();
        }

        // Diagnostic information
        result.getDiagnosticSummary().ifPresent(diagnostic -> {
            System.out.println("Diagnostic:       " + diagnostic);
        });

        // Performance
        System.out.println("Duration:         " + durationMs + "ms");

    }

    // Prints a compact one-line verification result.

    private static void printResultCompact(SimplifiedStatus status, VerificationResult result) {
        System.out.print("  Status: ");
        printColoredSimplifiedStatus(status);

        // Diagnostic
        result.getDiagnosticSummary().ifPresent(diagnostic -> {
            System.out.print(" - " + diagnostic);
        });

        System.out.println();
    }

    // Prints the simplified status with appropriate color coding.

    private static void printColoredSimplifiedStatus(SimplifiedStatus status) {
        switch (status) {
            case VALID:
                System.out.print(ANSI_GREEN + ANSI_BOLD + "VALID" + ANSI_RESET);
                break;
            case CATCH_ALL:
                System.out.print(ANSI_MAGENTA + ANSI_BOLD + "CATCH-ALL" + ANSI_RESET);
                break;
            case INVALID:
                System.out.print(ANSI_RED + ANSI_BOLD + "INVALID" + ANSI_RESET);
                break;
            case UNKNOWN:
                System.out.print(ANSI_YELLOW + ANSI_BOLD + "UNKNOWN" + ANSI_RESET);
                break;
        }
    }

    // Prints catch-all confidence with color coding.

    private static void printCatchAllConfidence(CatchAllDetectionResult.Confidence confidence) {
        switch (confidence) {
            case CONFIRMED:
                System.out.print(ANSI_MAGENTA + ANSI_BOLD + "CONFIRMED" + ANSI_RESET +
                        " (all probes accepted)");
                break;
            case SUSPECTED:
                System.out.print(ANSI_YELLOW + "SUSPECTED" + ANSI_RESET +
                        " (not confirmed)");
                break;
            case NOT_DETECTED:
                System.out.print(ANSI_GREEN + "NOT DETECTED" + ANSI_RESET +
                        " (server is selective)");
                break;
            case INDETERMINATE:
                System.out.print(ANSI_YELLOW + "INDETERMINATE" + ANSI_RESET +
                        " (cannot determine)");
                break;
        }
    }

    // Formats a boolean value with color.

    private static String formatBoolean(boolean value) {
        if (value) {
            return ANSI_GREEN + "Yes" + ANSI_RESET;
        } else {
            return ANSI_RED + "No" + ANSI_RESET;
        }
    }

    /**
     * Prints interpretation guidance based on simplified status.
     * I didn't use it intentionally - I want simple interface for now.
     */

    private static void printInterpretationGuide(SimplifiedStatus status,
                                                 VerificationResult result) {
        System.out.println(ANSI_BOLD + "\nInterpretation:" + ANSI_RESET);

        switch (status) {
            case VALID:
                System.out.println("  • Email verified successfully");
                System.out.println("  • Server is selective (NOT catch-all)");
                System.out.println("  • High confidence this mailbox exists");
                System.out.println("  • Safe to use this email address");
                break;

            case CATCH_ALL:
                System.out.println("  • " + ANSI_MAGENTA + "CATCH-ALL CONFIRMED:" + ANSI_RESET +
                        " Server accepts ALL addresses");
                System.out.println("  • Multi-probe analysis confirmed catch-all behavior");
                System.out.println("  • Cannot confirm this specific mailbox exists");
                System.out.println("  • Server will accept ANY address at this domain");
                System.out.println("  • Use with caution - may bounce after acceptance");
                break;

            case INVALID:
                System.out.println("  • Email failed verification");
                System.out.println("  • Could be: invalid syntax, non-existent domain, or");
                System.out.println("    no such mailbox (SMTP 5xx error)");
                System.out.println("  • This address should NOT be used");
                break;

            case UNKNOWN:
                System.out.println("  • Could not determine validity with certainty");
                System.out.println("  • Common causes:");
                System.out.println("    - Connection timeout");
                System.out.println("    - Server blocking verification probes");
                System.out.println("    - Greylisting or rate limiting");
                System.out.println("    - Temporary server issues");
                System.out.println("  • Consider retrying later or using alternative verification");
                break;
        }
    }
}