package jemvs.smtp.protocol;

public enum SmtpPhase {

    GREETING,

    HELO,

    MAIL_FROM,

    RCPT_TO,

    QUIT
}