package jemvs.dns.result;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class DnsResolutionResult {

    public enum Status {

        MX_FOUND,

        FALLBACK_A_RECORD,

        NXDOMAIN,

        TIMEOUT,

        FAILURE
    }

    private final Status status;
    private final List<String> mailServers;
    private final String errorMessage;

    private DnsResolutionResult(Status status, List<String> mailServers, String errorMessage) {
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.mailServers = mailServers != null
                ? Collections.unmodifiableList(mailServers)
                : Collections.emptyList();
        this.errorMessage = errorMessage;
    }

    public static DnsResolutionResult mxFound(List<String> mailServers) {
        if (mailServers == null || mailServers.isEmpty()) {
            throw new IllegalArgumentException("mailServers cannot be null or empty for MX_FOUND");
        }
        return new DnsResolutionResult(Status.MX_FOUND, mailServers, null);
    }

    public static DnsResolutionResult fallbackARecord(String hostname) {
        if (hostname == null || hostname.isEmpty()) {
            throw new IllegalArgumentException("hostname cannot be null or empty for FALLBACK_A_RECORD");
        }
        return new DnsResolutionResult(
                Status.FALLBACK_A_RECORD,
                Collections.singletonList(hostname),
                null
        );
    }

    public static DnsResolutionResult nxDomain() {
        return new DnsResolutionResult(Status.NXDOMAIN, null, "Domain does not exist");
    }

    public static DnsResolutionResult timeout() {
        return new DnsResolutionResult(Status.TIMEOUT, null, "DNS query timed out");
    }

    public static DnsResolutionResult failure(String errorMessage) {
        return new DnsResolutionResult(Status.FAILURE, null, errorMessage);
    }

    public Status getStatus() {
        return status;
    }

    public List<String> getMailServers() {
        return mailServers;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean hasMailServers() {
        return !mailServers.isEmpty();
    }

    public boolean hasMailHosts() {
        return status == Status.MX_FOUND || status == Status.FALLBACK_A_RECORD;
    }

    public String getPrimaryMailHost() {
        if (!hasMailHosts()) {
            throw new IllegalStateException(
                    "No mail hosts available (status=" + status + ")"
            );
        }
        return mailServers.get(0);
    }

    @Override
    public String toString() {
        return "DnsResolutionResult{status=" + status +
                ", mailServers=" + mailServers +
                ", errorMessage='" + errorMessage + "'}";
    }
}