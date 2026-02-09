package jemvs.syntax;

import java.util.Objects;

public final class SyntaxResult {

    private final boolean valid;
    private final String message;
    private final String domain;

    private SyntaxResult(boolean valid, String message, String domain) {
        this.valid = valid;
        this.message = message;
        this.domain = domain;
    }

    public static SyntaxResult success(String domain) {
        return new SyntaxResult(
                true,
                "Syntax is valid",
                Objects.requireNonNull(domain, "domain must not be null")
        );
    }

    public static SyntaxResult failure(String message) {

    return new SyntaxResult(false, message, null);

    }

    public boolean isValid() {
        return valid;
    }

    public String getMessage() {
        return message;
    }


    public String getDomain() {
        if (!valid) {
            throw new IllegalStateException("Cannot get domain from invalid syntax result");
        }
        return domain;
    }

}
