# Adding a new GPS provider

A provider needs: env vars, a parser (JSON -> `BusLocationDTO`), a `fetchAndPublishX()` method in `BusDataService`, and a worker loop in `BusTrackingThread`.

What the shared code already does for you, so a new provider should not redo it:

* **Bad records**: `parseRecords` skips a malformed record and counts it. It never drops the rest of the batch.
* **Empty answers**: a valid response with no vehicles is a `SUCCESS` (normal cadence). A response that is not JSON, has no array, or has only bad records is a `FAILURE`.
* **Errors**: `runFetch` catches everything (HTTP, parsing, Redis) and returns a `FetchResult`. `BusTrackingThread.safely` is a second net. A worker thread must never die.
* **Timeouts**: use the shared `restTemplate` (5s connect, 10s read). Do not create your own `RestTemplate`.
* **Registration numbers**: `requiredRegNo` runs `RegNoNormalizer` (uppercase, letters and digits only). Lookups must use the same normaliser.
* **Timestamps**: `providerStamp` turns the provider's time (any common format, read as IST) into `timestamp` ("yyyy-MM-dd HH:mm:ss") plus `epochMs`.
* **Bad fixes**: `checkFix` rejects out of range coordinates and (0,0).

---

## Step 1: `BusDataService.java`

### 1.1 Env vars

Add a field, read it in `initialiseEnvs`, and add a `requireEnv(missing, "URL_NEW", urlNew)` line. A missing var stops startup with a message naming it.

```java
private String urlNew;
// in initialiseEnvs():
this.urlNew = System.getenv("URL_NEW");
requireEnv(missing, "URL_NEW", urlNew);
```

### 1.2 Parser

Map one JSON record to a `BusLocationDTO`. Throw (via the helpers) if a required field is bad, return `null` to deliberately ignore a record.

```java
ParseResult parseApiNew(String json) throws JsonProcessingException {
    return parseRecords(json, "vehicles", node -> {          // "vehicles" = name of the array in the response
        double lat = requiredDouble(node, "lat");
        double lng = requiredDouble(node, "lng");
        checkFix(lat, lng);
        Stamp stamp = providerStamp("API_NEW", optionalText(node, "time"));
        return BusLocationDTO.builder()
                .regNo(requiredRegNo(node, "reg_no"))
                .latitude(lat)
                .longitude(lng)
                .speed(optionalDouble(node, "speed", 0.0))
                .timestamp(stamp.text())
                .epochMs(stamp.epochMs())
                .source("API_NEW")
                .build();
    });
}
```

If the provider sends no GPS time (like NMT and Tata), leave the timestamp out of the builder and call
`applyStableTimestamps(result.buses())` on the result before returning it.

### 1.3 Fetch method

```java
public FetchResult fetchAndPublishApiNew() {
    return runFetch("API NEW", null, () -> parseApiNew(restTemplate.getForObject(urlNew, String.class)));
}
```

For an API with a bearer token, pass the Redis key of the token as the second argument (it is deleted on a 401),
and get the token *inside* the lambda so a Redis error there is handled too. See `fetchAndPublishApiNMT`.

---

## Step 2: `BusTrackingThread.java`

Each worker has its own `Backoff`. Use a 10s base for normal APIs. For an API with a rate limit, use the limit as the base
(`ONE_MINUTE_API_FAILURE_BASE_MS` for the 1m 5s APIs): a retry is never sooner than the base.

```java
private void eventLoopApiNew() {
    log.info("API NEW Worker Started...");
    Backoff backoff = new Backoff(FAILURE_BASE_MS, FAILURE_MAX_MS);
    while (running) {
        FetchResult result = safely("API NEW", dataService::fetchAndPublishApiNew);
        handleSleep(result, "API NEW", backoff);             // handleSleepApiOneMinute for the 1 minute APIs
    }
}
```

Start it in `run()`:

```java
Thread threadNew = new Thread(this::eventLoopApiNew);
threadNew.setName("APINEW-Worker");
threadNew.start();
```

---

## Step 3: Environment

Add `URL_NEW=...` to the `.env` next to `docker-compose.yml` (and to your local run configuration).

## Checklist

* [ ] Env var read and required in `initialiseEnvs`
* [ ] `parseApiNew` written with `parseRecords`
* [ ] `fetchAndPublishApiNew` written with `runFetch`
* [ ] `eventLoopApiNew` written with `safely` and a `Backoff`
* [ ] Thread started in `run()`
* [ ] Env var added to the server `.env`
