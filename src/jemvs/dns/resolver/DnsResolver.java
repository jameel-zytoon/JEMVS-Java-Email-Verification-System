package jemvs.dns.resolver;

import jemvs.dns.result.DnsResolutionResult;

import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;


/**
 * DNS resolver for email verification.
 * Queries MX records, falls back to A/AAAA per RFC 5321.
 */
public class DnsResolver {

    private final int timeoutMs;


    public DnsResolver(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public DnsResolutionResult resolve(String domain) {
        if (domain == null || domain.isEmpty()) {
            return DnsResolutionResult.failure("Domain is null or empty");
        }

        // Step 1: Try MX records
        DnsResolutionResult mxResult = queryMxRecords(domain);
        if (mxResult.getStatus() == DnsResolutionResult.Status.MX_FOUND) {
            return mxResult;
        }

        // Step 2: If no MX found (but not NXDOMAIN), try A/AAAA fallback
        if (mxResult.getStatus() != DnsResolutionResult.Status.NXDOMAIN) {
            DnsResolutionResult fallbackResult = queryARecord(domain);
            if (fallbackResult.getStatus() == DnsResolutionResult.Status.FALLBACK_A_RECORD) {
                return fallbackResult;
            }
        }

        // Step 3: Return the failure reason (NXDOMAIN, TIMEOUT, or FAILURE)
        return mxResult;
    }

    /**
     * Query MX records using JNDI DNS context.
     * MX records indicate the mail server(s) responsible for a domain.
     */
    private DnsResolutionResult queryMxRecords(String domain) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", String.valueOf(timeoutMs));
        env.put("com.sun.jndi.dns.timeout.retries", "1");

        try {
            DirContext ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes(domain, new String[]{"MX"});
            Attribute mxAttr = attrs.get("MX");

            if (mxAttr == null || mxAttr.size() == 0) {
                // No MX records, but domain exists
                return DnsResolutionResult.failure("No MX records found");
            }

            List<String> mailServers = new ArrayList<>();
            NamingEnumeration<?> mxRecords = mxAttr.getAll();

            while (mxRecords.hasMore()) {
                String mxRecord = (String) mxRecords.next();
                String[] parts = mxRecord.split("\\s+");
                if (parts.length >= 2) {
                    String hostname = parts[1];
                    // Remove trailing dot if present
                    if (hostname.endsWith(".")) {
                        hostname = hostname.substring(0, hostname.length() - 1);
                    }
                    mailServers.add(hostname);
                }
            }

            ctx.close();

            if (mailServers.isEmpty()) {
                return DnsResolutionResult.failure("MX records malformed");
            }

            return DnsResolutionResult.mxFound(mailServers);

        } catch (NameNotFoundException e) {
            return DnsResolutionResult.nxDomain();
        } catch (NamingException e) {
            // Check for timeout
            if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                return DnsResolutionResult.timeout();
            }
            return DnsResolutionResult.failure("DNS query failed: " + e.getMessage());
        }
    }

    /**
     * Query A/AAAA records as fallback.
     * if no MX exists, treat the domain itself as the mail server (Per RFC 5321).
     */
    private DnsResolutionResult queryARecord(String domain) {
        try {
            // InetAddress.getByName queries A/AAAA records
            InetAddress.getByName(domain);
            // If successful, domain has A/AAAA record
            return DnsResolutionResult.fallbackARecord(domain);
        } catch (UnknownHostException e) {
            return DnsResolutionResult.nxDomain();
        }
    }
}

