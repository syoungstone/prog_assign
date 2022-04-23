import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
                if (!Files.exists(Paths.get(args[2]))) {
                    throw new Exception("ERR - FILE NOT FOUND");
                }
                PUT(URL, args[2]);
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

    // Makes a GET request
    private static void GET(String URL) throws Exception {
        try {
            makeRequest("GET", URL, null);
        } catch (Exception e) {
            throw new Exception("Err - arg 0"); // Error with URL
        }
    }

    // Makes a PUT request
    private static void PUT(String URL, String filepath) throws Exception {
        try {
            makeRequest("PUT", URL, filepath);
        } catch (Exception e) {
            throw new Exception("Err - arg 1"); // Error with URL
        }
    }

    // Does the heavy lifting of making an HTTP request
    private static void makeRequest(String method, String URL, String filepath) {

        // Parse URL
        String[] URLParsed = parseURL(URL);
        String hostname = URLParsed[0];
        String port = URLParsed[1];
        String path = URLParsed[2];

        // Establish connection
        int portNumber;
        if (port == null) {
            // Default to port 80
            portNumber = 80;
        } else {
            portNumber = Integer.parseInt(port);
        }
        try {
            // Prepare HTTP request string
            String CRLF = "\r\n"; // Carriage return + line feed
            String dateTimeString = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC));
            String requestString = method + " " + path +
                    // Add file name to path if making a PUT request
                    (method.equals("PUT") && filepath != null
                            // Make sure a single "/" separates path from file name
                            ? (path.endsWith("/") ? "" : "/") + getFilename(filepath)
                            : ""
                    ) + " HTTP/1.0" + CRLF +
                    // Include port number in hostname if specified
                    "Host: " + hostname + ((port != null) ? ":" + port : "") + CRLF +
                    "Time: " + dateTimeString + CRLF +
                    // Add "Content-Length" header if making a PUT request
                    // Required by my implementation of HTTPServer.java
                    (method.equals("PUT") && filepath != null
                            ? "Content-Length: " + Files.size(Paths.get(filepath)) + CRLF
                            : ""
                    ) +
                    "Class-name: VCU-CMSC440-2022" + CRLF +
                    "User-name: Sean Youngstone" + CRLF +
                    CRLF;

            // Print request string
            System.out.print(requestString);

            // Create socket and send request
            Socket socket = new Socket(hostname, portNumber);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeBytes(requestString);

            // Send file contents if PUT request
            if ("PUT".equals(method) && filepath != null) {
                Files.copy(Paths.get(filepath), out);
            }

            // Get header and contents from response
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            StringBuilder responseHeaderStringBuilder = new StringBuilder();
            StringBuilder responseContentStringBuilder = new StringBuilder();
            boolean readingHeader = true;
            int character;
            while ((character = in.read()) != -1) {
                if (readingHeader) {
                    responseHeaderStringBuilder.append((char) character);
                    int length = responseHeaderStringBuilder.length();
                    if (length > 4) {
                        String lastFour = responseHeaderStringBuilder.substring(length - 4);
                        if (lastFour.equals(CRLF + CRLF)) {
                            readingHeader = false;
                        }
                    }
                } else {
                    responseContentStringBuilder.append((char) character);
                }
            }
            String responseHeader = responseHeaderStringBuilder.toString();
            String content = responseContentStringBuilder.toString();

            // Get and print response code from HTTP header
            int firstSpaceIndex = responseHeader.indexOf(' ');
            int secondSpaceIndex = responseHeader.indexOf(' ', firstSpaceIndex + 1);
            String responseCodeString = responseHeader.substring(firstSpaceIndex + 1, secondSpaceIndex);
            int responseCode = Integer.parseInt(responseCodeString);
            System.out.println(responseCode);

            // Print selected information from HTTP response depending on response code
            printHeader(responseHeader, "Server");
            if ("GET".equals(method) && responseCode >= 200 && responseCode < 300) {
                printHeader(responseHeader, "Last-Modified");
                System.out.println(content.length());
            } else if ("GET".equals(method) && responseCode >= 300 && responseCode < 400) {
                printHeader(responseHeader, "Location");
            }

            // Print entire HTTP response header
            System.out.print(CRLF + responseHeader);

            if ("GET".equals(method) && content.length() > 0) {
                    // Save to file
                    String filename = getFilename(path);
                    PrintWriter writer = new PrintWriter(filename, StandardCharsets.UTF_8);
                    writer.write(content);
                    writer.close();
            }

            socket.close();

        } catch (Exception e) {
            System.out.println("There was an error.");
            System.out.println(e.toString());
        }
    }

    // If given the path "/", or one that ends in "/", returns "index.html"
    // If given a path that ends in a file name, returns the file name
    // Used to decide the filename for content received by a GET request
    private static String getFilename(String path) {
        if (path.equals("/")) {
            return "index.html";
        } else {
            if (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            String[] pathElements = path.split("/");
            int nameIndex = pathElements.length - 1;
            String filename = pathElements[nameIndex].trim();
            if (filename.equals("")) {
                filename = "index.html";
            }
            return filename;
        }
    }

    // Splits URL into hostname, port, and path
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

    // Prints the content of a given header in the HTTP header
    private static void printHeader(String responseHeader, String headerName) {
        String content = getHeader(responseHeader, headerName);
        if (content != null) System.out.println(content);
    }

    // Finds the content of a given header in the HTTP header
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
