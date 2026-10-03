public class MarketplaceWorkerTest {
  public static void main(String[] args) {
    var asset = new MarketplaceWorker.SellerAsset("seller-7", "course-java", 3);
    var accepted = MarketplaceWorker.decide(asset, new MarketplaceWorker.BuyerUpdate("buyer-2", 2));
    if (accepted == null || accepted.seats() != 2 || !accepted.orderId().equals("order-course-java-buyer-2")) throw new AssertionError("accepted handoff mismatch");
    var rejected = MarketplaceWorker.decide(asset, new MarketplaceWorker.BuyerUpdate("buyer-3", 4));
    if (rejected != null) throw new AssertionError("oversized request must be rejected");
    System.out.println("business decision test passed");
  }
}
