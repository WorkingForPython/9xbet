import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class http {
    public static void main(String[] args) throws IOException {
        // Binding to 0.0.0.0 (default) allows BOTH localhost AND your Network IP
        // sudo ufw allow 8080/tcp -> Firewall check
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/api/hello", exchange -> {
            String response = "Success! The Windows client connected to the Linux server.";
            
            // Set headers and body
            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
            
            System.out.println("LOG: Received a request from: " + exchange.getRemoteAddress());
        });

        server.start();

        // Updated print statements to show you exactly what to use
        System.out.println("=== SERVER IS LIVE ===");
        System.out.println("1. For THIS laptop:    http://localhost:8080/api/hello");
        System.out.println("2. For WINDOWS laptop: http://10.11.65.0:8080/api/hello");
        System.out.println("=======================");
    }
}