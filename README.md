# JEMVS - Java Email Verification System

A Java-based email verification system that validates email addresses through syntax checking, DNS resolution, SMTP probing, and behavioral analysis. This implementation addresses the problem of distinguishing valid email addresses from invalid ones without sending actual messages, while explicitly identifying catch-all mail servers that accept all addresses.

## Problem Statement

Standard email syntax validation (regex matching) cannot determine whether an email address actually exists. This system performs deeper verification by interrogating mail servers directly to assess deliverability, while accounting for servers that unconditionally accept all addresses (catch-all servers).

## Verification Approach

The system implements a multi-stage verification pipeline:

### 1. Syntax Validation
RFC-compliant email syntax checking using pattern matching against standard email format specifications.

### 2. DNS Resolution
Queries DNS for MX (Mail Exchange) records. Falls back to A records if no MX records exist. Extracts the primary mail host for SMTP probing.

### 3. SMTP Protocol Probing
Establishes SMTP connection to the mail server and simulates the initial phases of email delivery:
- `HELO/EHLO` - Server greeting
- `MAIL FROM` - Sender declaration
- `RCPT TO` - Recipient verification (the address being tested)

The server's response to `RCPT TO` indicates acceptance or rejection without completing delivery.

### 4. Catch-All Detection
Servers configured to accept all addresses (catch-all) will return positive responses regardless of whether the mailbox exists. The system detects this behavior through multi-probe analysis:

- Sends verification requests for both the target address and randomly generated non-existent addresses
- If the server accepts all probe addresses, it is classified as catch-all
- Uses at least 2 probes by default to confirm behavior consistency

## Verification Statuses

The system returns one of four statuses:

| Status | Meaning |
|--------|---------|
| `VALID` | Email passed all checks AND server rejected probe addresses (selective server) |
| `CATCH_ALL` | Email accepted but server accepts all addresses indiscriminately |
| `INVALID` | Email failed verification (syntax error, no MX records, or explicit SMTP rejection) |
| `UNKNOWN` | Unable to determine validity (timeouts, connection failures, rate limiting, or greylisting) |

## Confidence Levels

For addresses that pass SMTP verification, the system reports catch-all detection confidence:

| Confidence | Interpretation |
|------------|----------------|
| `NOT_DETECTED` | Server rejected probe addresses; exhibits selective behavior |
| `CONFIRMED` | Server accepted all probe addresses; catch-all behavior confirmed |
| `SUSPECTED` | Mixed results suggest potential catch-all (non-deterministic) |
| `INDETERMINATE` | Unable to probe or analyze server behavior |

## Limitations and Non-Guarantees

This system **does not** and **cannot**:

- **Guarantee deliverability**: A `VALID` status means the server accepted the address during SMTP handshake, not that delivery will succeed. Messages may still bounce due to quota limits, server-side filtering, or policy changes.

- **Detect all catch-all servers**: Some servers implement complex heuristics that may not be caught by multi-probe analysis. False negatives are possible.

- **Bypass anti-verification measures**: Mail servers may employ greylisting, rate limiting, or outright blocking of verification attempts. Such servers return `UNKNOWN` status.

- **Verify disposable or temporary addresses**: The system validates SMTP acceptance, not whether the address is a temporary/disposable service.

- **Work with all mail servers**: Some providers (particularly large webmail services) block or throttle verification attempts as an anti-spam measure.

- **Function without network access**: Requires outbound network connectivity for DNS queries and SMTP connections (typically port 25).

**Expected accuracy**: High accuracy for standard corporate/ISP mail servers with deterministic behavior. Lower accuracy for servers with aggressive anti-verification policies or complex acceptance rules.

## Running the System

### Prerequisites
- Java 11 or higher
- Network access for DNS and SMTP (outbound port 25)

### Compilation
```bash
javac -d out src/jemvs/**/*.java src/jemvs/*.java
```

### Execution
```bash
java -cp out jemvs.Main
```

The system presents a menu-driven interface with three modes:

#### Interactive Mode
Verify addresses one at a time:
```
Main Menu:
  1. Interactive Mode - Verify emails one at a time
  2. Batch Mode - Verify emails from file
  3. Help - Display usage information
  4. Exit

Enter choice (1-4): 1

Email> user@example.com
─── Verification Result ───
Status:           VALID
Syntax Valid:     Yes
Domain Resolves:  Yes
SMTP Accepted:    Yes
Catch-All:        NOT DETECTED (server is selective)
Duration:         1247ms
```

#### Batch Mode
Verify multiple addresses from a text file (one address per line):
```
Enter choice (1-4): 2
Enter path to email file: /path/to/emails.txt

Processing 150 emails...
[Progress indicators...]
Results written to: verification_results_20260210_143022.csv
```

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                  VerificationPipeline                   │
│                   (Orchestration)                       │
└────────────┬────────────────────────────────────────────┘
             │
             ├──> EmailSyntaxValidator
             │    (RFC syntax validation)
             │
             ├──> DnsResolver
             │    (MX/A record queries)
             │
             ├──> SmtpClient + SmtpSession
             │    (SMTP connection & protocol handling)
             │
             ├──> SmtpResponseInterpreter
             │    (Response code classification: 2xx/4xx/5xx)
             │
             └──> CatchAllDetector
                  (Multi-probe behavioral analysis)
```

### Component Responsibilities

- **VerificationPipeline**: Coordinates execution flow with early exits when verification becomes impossible
- **EmailSyntaxValidator**: Pattern-based RFC syntax compliance checking
- **DnsResolver**: DNS query execution with MX record prioritization and A record fallback
- **SmtpClient**: Low-level socket management and SMTP command/response exchange
- **SmtpSession**: Protocol state management (connection → HELO → MAIL FROM → RCPT TO)
- **SmtpResponseInterpreter**: Maps SMTP status codes to verification outcomes (accepted/rejected/indeterminate)
- **CatchAllDetector**: Generates probe addresses and analyzes server acceptance patterns

### Design Principles

- **Conservative interpretation**: Ambiguous responses default to `UNKNOWN` rather than false positives/negatives
- **Fail-fast**: Pipeline terminates at first definitive failure (invalid syntax, no MX records, explicit rejection)
- **Separation of concerns**: Protocol handling, interpretation, and analysis are independent layers
- **No side effects**: Verification does not complete delivery; connections are closed after RCPT TO phase

## Output Format

The CLI provides detailed verification results including:
- Final status classification
- Individual stage results (syntax/DNS/SMTP)
- Catch-all detection analysis (when applicable)
- Diagnostic messages from mail server
- Execution time

Batch mode generates CSV output with all verification metadata for further processing.

## License

This project is provided as-is for educational and verification purposes.
