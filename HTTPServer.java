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
                while (true) {
                    Socket connectionSocket = welcomeSocket.accept();
                    receiveRequest(connectionSocket);
                }

            } else {
                throw new Exception("Incorrect number of args");
            }
        // In case of any errors, print error message and exit
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    private static void receiveRequest(Socket socket) throws Exception {
        // Get header and contents from request
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        StringBuilder requestHeaderStringBuilder = new StringBuilder();
        StringBuilder requestContentStringBuilder = new StringBuilder();
        String CRLF = "\r\n"; // Carriage return + line feed
        boolean readingHeader = true;
        int remainingBytes = 0;
        while (readingHeader || remainingBytes > 0) {
            int character = in.read();
            if (readingHeader) {
                requestHeaderStringBuilder.append((char) character);
                int length = requestHeaderStringBuilder.length();
                if (length > 4) {
                    String lastFour = requestHeaderStringBuilder.substring(length - 4);
                    // Check if we have reached the end of the header
                    if (lastFour.equals(CRLF + CRLF)) {
                        readingHeader = false;
                        // Continue to read if there is data coming
                        if (requestHeaderStringBuilder.substring(0,3).equals("PUT")) {
                            String contentLength = getHeader(requestHeaderStringBuilder.toString(), "Content-Length");
                            if (contentLength != null) remainingBytes = Integer.parseInt(contentLength);
                        }
                    }
                }
            } else {
                requestContentStringBuilder.append((char) character);
                remainingBytes--;
            }
        }
        String requestHeader = requestHeaderStringBuilder.toString();
        String content = requestContentStringBuilder.toString();

        // Parse request and print info about request
        int firstSpaceIndex = requestHeader.indexOf(" ");
        int secondSpaceIndex = requestHeader.indexOf(" ", firstSpaceIndex + 1);
        String method = requestHeader.substring(0, firstSpaceIndex);
        String path = requestHeader.substring(firstSpaceIndex + 1, secondSpaceIndex);
        String clientIPAddress = socket.getInetAddress().toString();
        if (clientIPAddress.startsWith("/")) {
            clientIPAddress = clientIPAddress.substring(1);
        }
        int clientPort = socket.getPort();
        System.out.println(clientIPAddress + ":" + clientPort + ":" + method + CRLF);
        System.out.print(requestHeader);

        // Prepare and send response
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        if ("GET".equals(method)) {
            String filepath = getFilePath(path);
            if (!Files.exists(Paths.get(filepath))) {
                // Send 404 Not Found
                sendResponse(out, "404 Not Found", null);
            } else {
                // Send 200 OK
                sendResponse(out, "200 OK", Paths.get(filepath));
            }
        } else if ("PUT".equals(method) && content.length() > 0) {
            try {
                // Save to file
                PrintWriter writer = new PrintWriter("index.html", StandardCharsets.UTF_8);
                writer.write(content);
                writer.close();

                // Send 200 OK File Created
                sendResponse(out, "200 OK File Created", null);
            } catch (Exception e) {
                // Send 606 FAILED File NOT Created
                sendResponse(out, "606 FAILED File NOT Created", null);
            }
        }

        socket.close();

    }

    private static String getFilePath(String path) {
        if (path.equals("/")) {
            return "index.html";
        } else {
            if (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            return path;
        }
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

    // Find content of particular header value in the HTTP header and print it out
    private static String getHeader(String responseHeader, String headerName) {
        String CRLF = "\r\n";
        int headerIndex = responseHeader.indexOf(headerName);
        if (headerIndex >= 0) {
            int contentIndex = responseHeader.indexOf(" ", headerIndex) + 1;
            int endIndex = responseHeader.indexOf(CRLF, headerIndex);
            return responseHeader.substring(contentIndex, endIndex);
        }
        return null;
    }
}
