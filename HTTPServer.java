import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class HTTPServer {
    public static void main(String[] args) {
        // Main code wrapped in a try block to catch any errors
        try {
            // Number of arguments expected
            if (args.length == 1) {

                // Get and validate port number
                String portString = args[0];
                int port;
                try {
                    port = Integer.parseInt(portString);
                } catch (Exception e) {
                    throw new Exception("ERR - arg 0");
                }
                if (port < 0 || port >= 65536) {
                    throw new Exception("ERR - arg 0");
                }

                // Create welcome socket
                ServerSocket welcomeSocket = new ServerSocket(port);

                // Accept connections on a loop
                while (true) acceptConnection(welcomeSocket);

            } else {
                throw new Exception("Incorrect number of args");
            }
        // In case of any errors, print error message and exit
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    private static void acceptConnection(ServerSocket welcomeSocket) throws Exception {
        // Receive connection request
        Socket connectionSocket = welcomeSocket.accept();
        byte[] received = connectionSocket.getInputStream().readAllBytes();

        // Convert header into string
        String CRLF = "\r\n"; // Carriage return + line feed
        StringBuilder requestHeaderStringBuilder = new StringBuilder();
        boolean finishedReadingHeader = false;
        int byteIndex = 0;
        while (!finishedReadingHeader && byteIndex < received.length) {
            char character = (char) received[byteIndex];
            requestHeaderStringBuilder.append(character);
            int length = requestHeaderStringBuilder.length();
            // End of header marked by CR/LF twice in sequence
            if (length >= 4) {
                String lastFourChars = requestHeaderStringBuilder.substring(length - 4);
                finishedReadingHeader = lastFourChars.equals(CRLF + CRLF);
            }
            byteIndex++;
        }
        String requestHeader = requestHeaderStringBuilder.toString();

        // Parse request and print info about request
        int firstSpaceIndex = requestHeader.indexOf(" ");
        int secondSpaceIndex = requestHeader.indexOf(" ", firstSpaceIndex + 1);
        String method = requestHeader.substring(0, firstSpaceIndex);
        String path = requestHeader.substring(firstSpaceIndex + 1, secondSpaceIndex);
        String clientIPAddress = connectionSocket.getInetAddress().toString();
        int clientPort = connectionSocket.getPort();
        System.out.println(clientIPAddress + ":" + clientPort + ":" + method + CRLF);
        System.out.print(requestHeader);

        // Prepare and send response
        OutputStream out = connectionSocket.getOutputStream();
        if ("GET".equals(method)) {
            Path filepath = Paths.get(path);
            if (!Files.exists(filepath)) {
                // Send 404 Not Found
                sendResponse(out, "404 Not Found", null);
            } else {
                // Send 200 OK
                sendResponse(out, "200 OK", filepath);
            }
        } else if ("PUT".equals(method)) {
            try {
                // Copy data to new array and save as file, if any data received
                int dataLength = received.length - byteIndex;
                // Copy data only (not header) into new byte array
                byte[] data = new byte[dataLength];
                System.arraycopy(received, byteIndex, data, 0, dataLength);
                // Save to file
                OutputStream os = new FileOutputStream(path);
                os.write(data);
                os.close();
                // Send 200 OK File Created
                sendResponse(out, "200 OK File Created", null);
            } catch (Exception e) {
                // Send 606 FAILED File NOT Created
                sendResponse(out, "606 FAILED File NOT Created", null);
            }
        }

        connectionSocket.close();

    }

    private static void sendResponse(OutputStream out, String status, Path filepath) throws Exception {
        // Construct and send HTTP response
        String CRLF = "\r\n"; // Carriage return + line feed
        String dateTimeString = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC));
        String responseString = "HTTP/1.0 " + status + CRLF +
                "Time: " + dateTimeString + CRLF +
                "Class-name: VCU-CMSC440-2022" + CRLF +
                "User-name: Sean Youngstone" + CRLF +
                CRLF;
        out.write(StandardCharsets.UTF_8.encode(responseString).array());

        // Send file if included
        if (filepath != null) {
            Files.copy(filepath, out);
        }
    }
}
