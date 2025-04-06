import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.GZIPOutputStream;

public class LServer {
    private final int port;
    private final File root;
    private ServerSocket serverSocket;
    private final Map<String, String> mimeTypes = Map.ofEntries(
        Map.entry("html", "text/html"),
        Map.entry("css", "text/css"),
        Map.entry("js", "application/javascript"),
        Map.entry("json", "application/json"),
        Map.entry("png", "image/png"),
        Map.entry("jpg", "image/jpeg"),
        Map.entry("jpeg", "image/jpeg"),
        Map.entry("gif", "image/gif"),
        Map.entry("svg", "image/svg+xml"),
        Map.entry("ico", "image/x-icon"),
        Map.entry("txt", "text/plain")
    );
    private final Map<String, String> cacheHeaders = Map.of(
        "Cache-Control", "public, max-age=3600",
        "Expires", "" + (System.currentTimeMillis() + 3600000)
    );
    private final Map<String, Integer> requestCounts = new HashMap<>();
    private final Set<String> blockedIps = new HashSet<>();
    private final int RATE_LIMIT = 100; // max requests per minute
    private final int BLOCK_TIME = 5 * 60 * 1000; // Block time in ms
    private final String USERNAME = "admin";
    private final String PASSWORD = "password";

    public LServer(int port, String directory) {
        this.port = port;
        this.root = new File(directory).getAbsoluteFile();
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("LServer running at http://localhost:" + port);
        while (true) {
            Socket clientSocket = serverSocket.accept();
            new Thread(() -> handleClient(clientSocket)).start();
        }
    }

    private void handleClient(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             OutputStream out = socket.getOutputStream()) {

            String clientIp = socket.getInetAddress().getHostAddress();
            if (blockedIps.contains(clientIp)) {
                writeForbidden(out);
                return;
            }

            String requestLine = in.readLine();
            if (requestLine == null || requestLine.isEmpty()) return;

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String method = parts[0];
            String path = URLDecoder.decode(parts[1], "UTF-8");

            if (!method.equals("GET")) {
                writeMethodNotAllowed(out);
                return;
            }

            if (isRateLimited(clientIp)) {
                blockedIps.add(clientIp);
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        blockedIps.remove(clientIp);
                    }
                }, BLOCK_TIME);
                writeForbidden(out);
                return;
            }

            File file = new File(root, path).getCanonicalFile();
            if (!file.getPath().startsWith(root.getPath())) {
                writeForbidden(out);
                return;
            }

            if (file.isDirectory()) {
                File index = new File(file, "index.html");
                if (index.exists()) {
                    file = index;
                } else {
                    writeDirectoryListing(out, file, path);
                    return;
                }
            }

            if (!file.exists()) {
                writeNotFound(out);
                return;
            }

            byte[] data = Files.readAllBytes(file.toPath());
            String extension = getFileExtension(file.getName());
            String contentType = mimeTypes.getOrDefault(extension, "application/octet-stream");

            String header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: " + contentType + "\r\n" +
                    "Content-Length: " + data.length + "\r\n" +
                    "Connection: close\r\n" +
                    "Date: " + getServerTime() + "\r\n" +
                    getCacheHeaders() + "\r\n";

            if (shouldGzip(extension)) {
                writeGzipResponse(out, data, header);
            } else {
                out.write(header.getBytes());
                out.write(data);
            }

            logRequest(clientIp, method, path, "200 OK");

        } catch (IOException ignored) {
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private boolean shouldGzip(String extension) {
        return extension.equals("html") || extension.equals("css") || extension.equals("js");
    }

    private void writeGzipResponse(OutputStream out, byte[] data, String header) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream)) {
            gzipOutputStream.write(data);
        }
        byte[] compressedData = byteArrayOutputStream.toByteArray();
        String gzipHeader = header + "Content-Encoding: gzip\r\n" + "Content-Length: " + compressedData.length + "\r\n\r\n";
        out.write(gzipHeader.getBytes());
        out.write(compressedData);
    }

    private boolean isRateLimited(String clientIp) {
        requestCounts.put(clientIp, requestCounts.getOrDefault(clientIp, 0) + 1);
        if (requestCounts.get(clientIp) > RATE_LIMIT) {
            return true;
        }
        return false;
    }

    private String getCacheHeaders() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : cacheHeaders.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
        }
        return sb.toString();
    }

    private void logRequest(String clientIp, String method, String path, String status) {
        String log = String.format("[%s] %s %s %s %s", getServerTime(), clientIp, method, path, status);
        System.out.println(log);
    }

    private void writeNotFound(OutputStream out) throws IOException {
        String body = "<html><body><h1>404 Not Found</h1></body></html>";
        String header = "HTTP/1.1 404 Not Found\r\n" +
                "Content-Type: text/html\r\n" +
                "Content-Length: " + body.length() + "\r\n" +
                "Connection: close\r\n\r\n";
        out.write(header.getBytes());
        out.write(body.getBytes());
    }

    private void writeMethodNotAllowed(OutputStream out) throws IOException {
        String body = "<html><body><h1>405 Method Not Allowed</h1></body></html>";
        String header = "HTTP/1.1 405 Method Not Allowed\r\n" +
                "Allow: GET\r\n" +
                "Content-Type: text/html\r\n" +
                "Content-Length: " + body.length() + "\r\n" +
                "Connection: close\r\n\r\n";
        out.write(header.getBytes());
        out.write(body.getBytes());
    }

    private void writeForbidden(OutputStream out) throws IOException {
        String body = "<html><body><h1>403 Forbidden</h1></body></html>";
        String header = "HTTP/1.1 403 Forbidden\r\n" +
                "Content-Type: text/html\r\n" +
                "Content-Length: " + body.length() + "\r\n" +
                "Connection: close\r\n\r\n";
        out.write(header.getBytes());
        out.write(body.getBytes());
    }

    private void writeDirectoryListing(OutputStream out, File dir, String requestPath) throws IOException {
        StringBuilder body = new StringBuilder("<html><body><h1>Index of " + requestPath + "</h1><ul>");
        for (File file : Objects.requireNonNull(dir.listFiles())) {
            String name = file.getName() + (file.isDirectory() ? "/" : "");
            body.append("<li><a href=\"").append(requestPath);
            if (!requestPath.endsWith("/")) body.append("/");
            body.append(name).append("\">").append(name).append("</a></li>");
        }
        body.append("</ul></body></html>");

        String response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html\r\n" +
                "Content-Length: " + body.length() + "\r\n" +
                "Connection: close\r\n\r\n" + body;
        out.write(response.getBytes());
    }

    private String getFileExtension(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(i + 1).toLowerCase() : "";
    }

    private String getServerTime() {
        return new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z", Locale.US)
                .format(new Date());
    }

    public static void main(String[] args) throws IOException {
        int port = 8000;
        String directory = ".";

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--port") && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            } else if (args[i].equals("--dir") && i + 1 < args.length) {
                directory = args[++i];
            }
        }

        new LServer(port, directory).start();
    }
      }
