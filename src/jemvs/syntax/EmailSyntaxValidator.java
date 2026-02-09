package jemvs.syntax;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Performs email syntax and structural validation using a
 * practical, RFC-aligned subset suitable for real-world SMTP.
 * <p>
 * Explicitly NOT supported by design:<p/>
 * - Quoted local-parts<p>
 * - IP-literal domains<p/>
 * - Internationalized email addresses (SMTPUTF8)<p/>
 */

public class EmailSyntaxValidator {

    /**
     * Dot-atom local-part pattern (RFC 5322 subset).
     * This excludes quoted strings and obsolete forms.
     */

    private static final Pattern LOCAL_PART_PATTERN =
            Pattern.compile("^[A-Za-z0-9!#$%&'*+/=?^_`{|}~.-]+$");

    /**
     * Conservative email shape check.
     * Detailed domain validation is performed separately.
     */

    private static final Pattern BASIC_EMAIL_PATTERN =
            Pattern.compile("^[^@]+@[^@]+$");

    public SyntaxResult validate(String email) {

        // Basic presence
        if (email == null || email.isBlank()) {
            return SyntaxResult.failure("Email is empty");
        }

        // ASCII enforcement (no SMTPUTF8)
        if (!StandardCharsets.US_ASCII.newEncoder().canEncode(email)) {
            return SyntaxResult.failure("Non-ASCII characters are not supported");
        }

        // Length constraints
        if (email.length() > 254) {
            return SyntaxResult.failure("Email exceeds maximum length (254 characters)");
        }

        // Single @ symbol
        if (!BASIC_EMAIL_PATTERN.matcher(email).matches()) {
            return SyntaxResult.failure("Email must contain exactly one '@' symbol");
        }

        int atIndex = email.indexOf('@');
        String localPart = email.substring(0, atIndex);
        String domainPart = email.substring(atIndex + 1);

        // Local-part length
        if (localPart.length() > 64) {
            return SyntaxResult.failure("Local part exceeds maximum length (64 characters)");
        }

        // Domain length
        if (domainPart.length() > 253) {
            return SyntaxResult.failure("Domain part exceeds maximum length (253 characters)");
        }

        // Local-part structural rules
        if (localPart.startsWith(".") || localPart.endsWith(".")) {
            return SyntaxResult.failure("Local part cannot start or end with a dot");
        }

        if (localPart.contains("..")) {
            return SyntaxResult.failure("Local part cannot contain consecutive dots");
        }

        if (!LOCAL_PART_PATTERN.matcher(localPart).matches()) {
            return SyntaxResult.failure("Local part contains illegal characters");
        }

        // Domain structural rules
        if (domainPart.startsWith(".") || domainPart.endsWith(".")) {
            return SyntaxResult.failure("Domain cannot start or end with a dot");
        }

        if (domainPart.contains("..")) {
            return SyntaxResult.failure("Domain cannot contain consecutive dots");
        }

        // Reject IP-literal domains
        if (domainPart.startsWith("[") && domainPart.endsWith("]")) {
            return SyntaxResult.failure("IP-literal domains are not supported");
        }

        // Domain label validation
        String[] labels = domainPart.split("\\.");

        for (String label : labels) {
            if (label.isEmpty()) {
                return SyntaxResult.failure("Domain contains an empty label");
            }

            if (label.length() > 63) {
                return SyntaxResult.failure("Domain label exceeds maximum length (63 characters)");
            }

            if (!label.matches("^[A-Za-z0-9-]+$")) {
                return SyntaxResult.failure("Domain label contains illegal characters");
            }

            if (label.startsWith("-") || label.endsWith("-")) {
                return SyntaxResult.failure("Domain label cannot start or end with a hyphen");
            }
        }

        // TLD sanity check (practical constraint)
        String tld = labels[labels.length - 1];
        if (tld.length() < 2 || !tld.matches("^[A-Za-z]+$")) {

            return SyntaxResult.failure("Top-level domain is invalid");
        }

        return SyntaxResult.success(domainPart);

    }
}