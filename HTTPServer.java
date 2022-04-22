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

        // Get header and contents from request
        BufferedReader in = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
        StringBuilder requestHeaderStringBuilder = new StringBuilder();
        StringBuilder requestContentStringBuilder = new StringBuilder();
        String CRLF = "\r\n"; // Carriage return + line feed
        boolean readingHeader = true;
        int character;
        while ((character = in.read()) != -1) {
            if (readingHeader) {
                requestHeaderStringBuilder.append((char) character);
                int length = requestHeaderStringBuilder.length();
                if (length > 4) {
                    String lastFour = requestHeaderStringBuilder.substring(length - 4);
                    if (lastFour.equals(CRLF + CRLF)) {
                        readingHeader = false;
                    }
                }
            } else {
                requestContentStringBuilder.append((char) character);
            }
        }
        String requestHeader = requestHeaderStringBuilder.toString();
        String content = requestContentStringBuilder.toString();

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
        DataOutputStream out = new DataOutputStream(connectionSocket.getOutputStream());
        if ("GET".equals(method)) {
            Path filepath = Paths.get(path);
            if (!Files.exists(filepath)) {
                // Send 404 Not Found
                sendResponse(out, "404 Not Found", null);
            } else {
                // Send 200 OK
                sendResponse(out, "200 OK", filepath);
            }
        } else if ("PUT".equals(method) && content.length() > 0) {
            try {
                // Get file name
                String filename;
                if (path.equals("/")) {
                    filename = "index.html";
                } else {
                    if (path.endsWith("/")) {
                        path = path.substring(0, path.length() - 1);
                    }
                    String[] pathElements = path.split("/");
                    int nameIndex = pathElements.length - 1;
                    filename = pathElements[nameIndex].trim();
                    if (filename.equals("")) {
                        filename = "index.html";
                    }
                }

                // Save to file
                PrintWriter writer = new PrintWriter(filename, StandardCharsets.UTF_8);
                writer.write(content);
                writer.close();

                // Send 200 OK File Created
                sendResponse(out, "200 OK File Created", null);
            } catch (Exception e) {
                // Send 606 FAILED File NOT Created
                sendResponse(out, "606 FAILED File NOT Created", null);
            }
        }

        connectionSocket.close();

    }

    private static void sendResponse(DataOutputStream out, String status, Path filepath) throws Exception {
        // Construct and send HTTP response
        String CRLF = "\r\n"; // Carriage return + line feed
        String dateTimeString = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC));
        String responseString = "HTTP/1.0 " + status + CRLF +
                "Time: " + dateTimeString + CRLF +
                "Class-name: VCU-CMSC440-2022" + CRLF +
                "User-name: Sean Youngstone" + CRLF +
                CRLF;
        out.writeBytes(responseString);

        // Send file if included
        if (filepath != null) {
            Files.copy(filepath, out);
        }
    }
}
