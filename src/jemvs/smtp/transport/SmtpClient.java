package jemvs.smtp.transport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

public class SmtpClient implements AutoCloseable {

    private static final int DEFAULT_SMTP_PORT = 25;
    private static final int CONNECTION_TIMEOUT_MS = 10000; // 10 seconds
    private static final int READ_TIMEOUT_MS = 15000; // 15 seconds

    private final String mailHost;
    private final int port;

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;


    public SmtpClient(String mailHost) {
        this(mailHost, DEFAULT_SMTP_PORT);
    }

    public SmtpClient(String mailHost, int port) {
        if (mailHost == null || mailHost.trim().isEmpty()) {
            throw new IllegalArgumentException("Mail host cannot be null or empty");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }

        this.mailHost = mailHost;
        this.port = port;
    }

    //Establishes a TCP connection to the SMTP server.

    public void connect() throws IOException {
        try {
            socket = new Socket();
            socket.connect(
                    new InetSocketAddress(mailHost, port),
                    CONNECTION_TIMEOUT_MS
            );
            socket.setSoTimeout(READ_TIMEOUT_MS);

            // Use UTF-8 for proper international character support
            // although SMTP traditionally uses ASCII, modern servers
            // may support SMTPUTF8 (RFC 6531)

            reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
            );

            writer = new PrintWriter(
                    socket.getOutputStream(),
                    true, // autoFlush = true for immediate command transmission
                    StandardCharsets.UTF_8
            );

        } catch (SocketTimeoutException e) {
            throw new IOException("Connection timeout to " + mailHost + ":" + port, e);
        } catch (IOException e) {
            // Ensure cleanup if connection partially succeeded
            closeQuietly();
            throw new IOException("Failed to connect to " + mailHost + ":" + port, e);
        }
    }

    // Sends an SMTP command to the server.

    public void sendCommand(String command) throws IOException {
        if (writer == null || socket == null || socket.isClosed()) {
            throw new IllegalStateException("Not connected to SMTP server");
        }

        // RFC 5321 requires CRLF line termination
        // PrintWriter.println() uses platform line separator, so we append \r\n explicitly
        writer.print(command + "\r\n");
        writer.flush();
    }

    // Reads a single-line response from the SMTP server.

    public String readLine() throws IOException {
        if (reader == null || socket == null || socket.isClosed()) {
            throw new IllegalStateException("Not connected to SMTP server");
        }

        try {
            return reader.readLine();
        } catch (SocketTimeoutException e) {
            throw new IOException("Read timeout from " + mailHost + ":" + port, e);
        }
    }

    //Reads a complete SMTP response, handling multi-line responses.

    public String readResponse() throws IOException {
        StringBuilder response = new StringBuilder();
        String line;

        while ((line = readLine()) != null) {
            response.append(line);

            // SMTP multi-line responses: code + hyphen indicates continuation
            // code + space indicates final line
            // We check if line has at least 4 chars (3-digit code + separator)
            if (line.length() >= 4 && line.charAt(3) == ' ') {
                break; // Final line of response
            }

            // Add newline for multi-line responses (except after last line)
            response.append("\n");
        }

        return response.toString();
    }

    // Checks if the client is currently connected to the SMTP server.

    public boolean isConnected() {
        return socket != null && !socket.isClosed() && socket.isConnected();
    }

    // Closes the connection to the SMTP server.

    @Override
    public void close() {
        closeQuietly();
    }

    //Internal cleanup that suppresses exceptions.

    private void closeQuietly() {
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException ignored) {
                // Suppress exceptions during cleanup
            }
            reader = null;
        }

        if (writer != null) {
            writer.close(); // PrintWriter.close() doesn't throw
            writer = null;
        }

        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Suppress exceptions during cleanup
            }
            socket = null;
        }
    }

    // Returns the mail host this client is configured for.

    public String getMailHost() {
        return mailHost;
    }

    //Returns the port this client is configured for.

    public int getPort() {
        return port;
    }
}
