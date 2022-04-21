import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class HTTPClient {

    public static void main(String[] args) {
        // Main code wrapped in a try block to catch any errors
        try {
            // Number of arguments for a GET request
            if (args.length == 1) {
                // Validate arg 0 - URL
                String URL = args[0];
                if (invalidURL(URL)) {
                    throw new Exception("ERR - arg 0");
                }
                GET(URL);
            // Number of arguments for a PUT request
            } else if (args.length == 3) {
                // Validate arg 0 - "PUT"
                if (!"PUT".equalsIgnoreCase(args[0])) {
                    throw new Exception("ERR - arg 0");
                }
                // Validate arg 1 - URL
                String URL = args[1];
                if (invalidURL(URL)) {
                    throw new Exception("ERR - arg 1");
                }
                // Validate arg 2 - file path
                Path filepath = Paths.get(args[2]);
                if (!Files.exists(filepath)) {
                    throw new Exception("ERR - FILE NOT FOUND");
                }
                PUT(URL, filepath);
            // Incorrect number of arguments
            } else {
                throw new Exception("Incorrect number of args");
            }
        // In case of any errors, print error message and exit
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    // Confirm that URL string starts with "http://"
    private static boolean invalidURL(String url) {
        return !url.startsWith("http://");
    }

    private static void GET(String URL) throws Exception {
        try {
            makeRequest("GET", URL, null);
        } catch (Exception e) {
            throw new Exception("Err - arg 0");
        }
    }

    private static void PUT(String URL, Path filepath) throws Exception {
        try {
            makeRequest("PUT", URL, filepath);
        } catch (Exception e) {
            throw new Exception("Err - arg 1");
        }
    }

    private static void makeRequest(String method, String URL, Path filepath) {

        // Parse URL
        String[] URLParsed = parseURL(URL);
        String hostname = URLParsed[0];
        String port = URLParsed[1];
        String path = URLParsed[2];

        // Establish connection
        int portNumber;
        if (port == null) {
            portNumber = 80;
        } else {
            portNumber = Integer.parseInt(port);
        }
        try {
            // Prepare HTTP request string
            String CRLF = "\r\n"; // Carriage return + line feed
            String dateTimeString = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC));
            String requestString = method + " " + path + " HTTP/1.0" + CRLF +
                    "Host: " + hostname + CRLF +
                    "Time: " + dateTimeString + CRLF +
                    "Class-name: VCU-CMSC440-2022" + CRLF +
                    "User-name: Sean Youngstone" + CRLF +
                    CRLF;

            // Print request string
            System.out.print(requestString);

            // Create socket and send request
            Socket socket = new Socket(hostname, portNumber);
            OutputStream out = socket.getOutputStream();
            out.write(StandardCharsets.UTF_8.encode(requestString).array());

            // Send file contents if PUT request
            if ("PUT".equals(method)) {
                Files.copy(filepath, out);
            }

            // Read response as byte array
            byte[] received = socket.getInputStream().readAllBytes();

            // Convert header into string
            StringBuilder responseHeaderStringBuilder = new StringBuilder();
            boolean finishedReadingHeader = false;
            int byteIndex = 0;
            while (!finishedReadingHeader && byteIndex < received.length) {
                char character = (char) received[byteIndex];
                responseHeaderStringBuilder.append(character);
                int length = responseHeaderStringBuilder.length();
                // End of header marked by CR/LF twice in sequence
                if (length >= 4) {
                    String lastFourChars = responseHeaderStringBuilder.substring(length - 4);
                    finishedReadingHeader = lastFourChars.equals(CRLF + CRLF);
                }
                byteIndex++;
            }
            String responseHeader = responseHeaderStringBuilder.toString();

            // Get and print response code from HTTP header
            int firstSpaceIndex = responseHeader.indexOf(' ');
            int secondSpaceIndex = responseHeader.indexOf(' ', firstSpaceIndex + 1);
            String responseCodeString = responseHeader.substring(firstSpaceIndex + 1, secondSpaceIndex);
            int responseCode = Integer.parseInt(responseCodeString);
            System.out.println(responseCode);

            // Print selected headers from HTTP header
            printHeader(responseHeader, "Server");
            if ("GET".equals(method) && responseCode >= 200 && responseCode < 300) {
                printHeader(responseHeader, "Last-Modified");
                int dataLength = received.length - byteIndex;
                System.out.println(dataLength);
                // printHeader(responseHeader, "Content-Length");
            } else if ("GET".equals(method) && responseCode >= 300 && responseCode < 400) {
                printHeader(responseHeader, "Location");
            }

            // Print entire HTTP response header
            System.out.print(CRLF + responseHeader);

            if ("GET".equals(method)) {
                // Copy data to new array and save as file, if any data received
                int dataLength = received.length - byteIndex;
                if (dataLength > 0) {

                    // Copy data only (not header) into new byte array
                    byte[] data = new byte[dataLength];
                    System.arraycopy(received, byteIndex, data, 0, dataLength);

                    // Get file name
                    String filename;
                    if (path.equals("/")) {
                        filename = "newFile";
                    } else {
                        if (path.endsWith("/")) {
                            path = path.substring(0, path.length() - 1);
                        }
                        String[] pathElements = path.split("/");
                        int nameIndex = pathElements.length - 1;
                        filename = pathElements[nameIndex].trim();
                        if (filename.equals("")) {
                            filename = "newFile";
                        }
                    }

                    // Save to file
                    OutputStream os = new FileOutputStream(filename);
                    os.write(data);
                    os.close();
                }
            }

            socket.close();

        } catch (Exception e) {
            System.out.println("There was an error.");
            System.out.println(e.toString());
        }
    }

    private static String[] parseURL(String URL) {
        int hostIndex = 7; // First character index after "http://"
        int portIndex = URL.indexOf(":", hostIndex);
        int pathIndex = URL.indexOf("/", hostIndex);
        String hostname;
        String port;
        String path;

        // No path, no port
        if (pathIndex < 0 && portIndex < 0) {
            hostname = URL.substring(hostIndex);
            port = null;
            path = "/";

        // Port but no path
        } else if (pathIndex < 0) {
            hostname = URL.substring(hostIndex, portIndex);
            port = URL.substring(portIndex + 1);
            path = "/";

        // Path but no port
        } else if (portIndex < 0) {
            hostname = URL.substring(hostIndex, pathIndex);
            port = null;
            path = URL.substring(pathIndex);

        // Both port and path
        } else {
            hostname = URL.substring(hostIndex, portIndex);
            port = URL.substring(portIndex + 1, pathIndex);
            path = URL.substring(pathIndex);
        }

        return new String[] { hostname, port, path };
    }

    private static void printHeader(String responseHeader, String headerName) {
        String content = getHeader(responseHeader, headerName);
        if (content != null) System.out.println(content);
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
