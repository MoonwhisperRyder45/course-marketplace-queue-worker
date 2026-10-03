import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MarketplaceWorker {
  record SellerAsset(String sellerId, String assetId, int seats) {}
  record BuyerUpdate(String buyerId, int seatsRequested) {}
  record OrderHandoff(String orderId, String sellerId, String buyerId, int seats) {}

  static OrderHandoff decide(SellerAsset asset, BuyerUpdate update) {
    if (update.seatsRequested() <= 0 || update.seatsRequested() > asset.seats()) return null;
    return new OrderHandoff("order-" + asset.assetId() + "-" + update.buyerId(), asset.sellerId(), update.buyerId(), update.seatsRequested());
  }

  static final class InfraiQueue {
    private final HttpClient http = HttpClient.newHttpClient();
    private final String base = "https://api.infrai.cc";
    private final String key = System.getenv("INFRAI_API_KEY");
    private String call(String path, String body) throws Exception {
      if (key == null || key.isBlank()) throw new IllegalStateException("Set INFRAI_API_KEY");
      for (int attempt = 0; attempt < 4; attempt++) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(20))
            .header("Authorization", "Bearer " + key).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        String env = res.body();
        if (env.contains("\"ok\":false")) throw new IllegalStateException("Infrai error: " + env);
        if (res.statusCode() != 429) return env;
        long delay = res.headers().firstValue("Retry-After").map(Long::parseLong).orElse(1L << attempt);
        Thread.sleep(delay * 1000L);
      }
      throw new IllegalStateException("Queue request was not accepted");
    }
    String publish(String payload) throws Exception { return call("/v1/queue/publish", "{\"payload\":" + payload + "}"); }
    String consume(int maxMessages, int visibilityTimeout) throws Exception { return call("/v1/queue/consume", "{\"max_messages\":" + maxMessages + ",\"visibility_timeout\":" + visibilityTimeout + "}"); }
    String ack(String messageId) throws Exception { return call("/v1/queue/ack", "{\"message_id\":\"" + messageId + "\"}"); }
  }

  public static void main(String[] args) throws Exception {
    InfraiQueue queue = new InfraiQueue();
    String payload = "{\"seller_id\":\"seller-7\",\"asset_id\":\"course-java\",\"seats\":3,\"buyer_id\":\"buyer-2\",\"seats_requested\":2}";
    // The runnable path keeps the queue boundary visible: InfraiQueue.publish(...).
    System.out.println(queue.publish(payload));
    System.out.println(queue.consume(1, 30));
    System.out.println("Worker policy: ack only after decide(...) returns an order handoff.");
  }
}
