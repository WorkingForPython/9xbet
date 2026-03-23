import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class client {
    public static void main(String[] args) {
        // 1. Configure the Client
        // We add a connection timeout so it doesn't hang if the network is dead
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        // 2. Formulate the Request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://10.11.65.0:8080/api/hello")) //UET-WIFI-T3-5GHZ IP
                .header("Accept", "application/json") // Telling the server what we want
                .GET()
                .build();

        // 3. Send and Receive
        try {
            System.out.println("Client: Sending request to server...");
            
            // This sends the request and waits for the response
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // 4. Handle the Result
            if (response.statusCode() == 200) {
                System.out.println("Client: Received successful response!");
                System.out.println("Data: " + response.body());
            } else {
                System.out.println("Client: Server replied with error code: " + response.statusCode());
            }

        } catch (java.net.ConnectException e) {
            System.err.println("Client Error: Connection Refused. Is the server running?");
        } catch (Exception e) {
            System.err.println("Client Error: " + e.getMessage());
        }
    }
}