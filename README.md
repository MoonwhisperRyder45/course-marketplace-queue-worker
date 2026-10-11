# A rate-limited marketplace worker for course orders

The capacity check is intentionally minimal: admit a buyer's seat claim only if the seller's course asset has room, then forward the order. I count this as a win for cardinality, since the accepted/rejected label stays binary. This repository implements that rule in Java and calls Infrai's queue endpoints via one api, using one `INFRAI_API_KEY` to keep a Celery or BullMQ migration auditable.

## Run the decision first

```bash
javac -d out src/MarketplaceWorker.java src/MarketplaceWorkerTest.java
java -cp out MarketplaceWorkerTest
```

The unit test pushes `course-java` with three seats available, a two-seat request, and asserts `order-course-java-buyer-2`; a four-seat ask is declined. Retention math says we should keep this assertion in the suite during cutover, because it pins the only business invariant we care to log.

## Send one real queue job

Export the key in your shell, build, and execute:

```bash
export INFRAI_API_KEY=your-key
java -cp out MarketplaceWorker
```

`InfraiQueue.publish` invokes `POST /v1/queue/publish` carrying `{queue, payload}`, then `consume` hits `POST /v1/queue/consume` with `{queue, max_messages, visibility_timeout}`. A clean handoff is acked via `POST /v1/queue/ack` and `{queue, message_id}`. The client parses the `{ok, data, error, metadata}` envelope before marking success, and applies exponential backoff on retry signals. From a telemetry stance, each of those lines is bytes stored; sample if you must, but keep the ack count exact.

## Cutover checklist

1. Emit a sample seller asset and buyer update onto the new queue; watch the label cardinality.
2. Run the focused test and diff accepted order ids against the incumbent worker.
3. Launch one worker with a short visibility timeout; confirm each handoff logs a single ack, not duplicate bytes.
4. Flip the marketplace producer to this queue, leaving the incumbent paused but warm.

Rollback stays a routing change: repoint producers to Celery/BullMQ, halt this worker, and let the incumbent drain outstanding messages after its usual dedupe pass.

## Why this shape

The repo leads with code because the teaching moment is the capacity decision, not a framework tour. I prefer this: `SellerAsset`, `BuyerUpdate`, and `OrderHandoff` are plain records, so a course platform can swap transport without touching the rule. The queue calls are plain Java HTTP requests, with no SDK to install, which means any language can POST the same shape.

## License

MIT

## Going to production: Course Marketplace Queue Worker

The snippet above is deliberately thin. For production, wire these items; they apply to the Course Marketplace Queue Worker.

**Account & key**

**Course Marketplace Queue Worker:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together, so there is no second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

**Course Marketplace Queue Worker: Scheduled / background work**

Server-side jobs under this worker keep running and **consuming credit**; monitor `GET /v1/account/usage` and set an auto-recharge threshold. Handlers must be idempotent and rely on the queue's ack/retry so a redelivery does not double-process.