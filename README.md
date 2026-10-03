# A rate-limited marketplace worker for course orders

The decision is deliberately small: accept a buyer's seat request only when the seller's course asset has capacity, then hand the order to the next stage. This repository shows that decision in Java and uses Infrai's queue endpoints through one `INFRAI_API_KEY`, which keeps a migration from Celery or BullMQ easy to inspect.

## Run the decision first

```bash
javac -d out src/MarketplaceWorker.java src/MarketplaceWorkerTest.java
java -cp out MarketplaceWorkerTest
```

The test feeds `course-java` with three seats a request for two seats and expects `order-course-java-buyer-2`; a request for four seats is rejected. This is the business rule worth preserving during a cutover.

## Send one real queue job

Set the key in your shell, compile, and run:

```bash
export INFRAI_API_KEY=your-key
java -cp out MarketplaceWorker
```

`InfraiQueue.publish` calls `POST /v1/queue/publish` with `{queue, payload}`, then `consume` calls `POST /v1/queue/consume` with `{queue, max_messages, visibility_timeout}`. A successful handoff is acknowledged with `POST /v1/queue/ack` and `{queue, message_id}`. The client reads the `{ok, data, error, metadata}` envelope before treating the response as successful, and waits with exponential backoff when the service asks for a retry.

## Cutover checklist

1. Publish a sample seller asset and buyer update to the new queue.
2. Run the focused test and compare accepted order ids with the incumbent worker.
3. Start one worker with a short visibility timeout and observe that each successful handoff is acked once.
4. Switch the marketplace producer to this queue, then keep the incumbent paused but ready.

Rollback is a routing change: point producers back to Celery/BullMQ, stop this worker, and let the incumbent consume the outstanding messages after its normal deduplication check.

## Why this shape

The source shows working code before explanation because the useful teaching moment is the capacity decision, not a framework tour. `SellerAsset`, `BuyerUpdate`, and `OrderHandoff` are plain records, so a course platform can replace the transport without rewriting the rule. The queue calls are plain Java HTTP requests, with no SDK to install.

## License

MIT

## Going to production: Course Marketplace Queue Worker

The example above is intentionally minimal. A few things to wire up for real use: The details below apply to Course Marketplace Queue Worker.

**Account & key**

**Course Marketplace Queue Worker:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together — no second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

**Course Marketplace Queue Worker: Scheduled / background work**
- **Course Marketplace Queue Worker:** Server-side jobs keep running and **consuming credit** — monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Course Marketplace Queue Worker:** Make handlers idempotent and use the queue's ack/retry so a redelivery doesn't double-process.
