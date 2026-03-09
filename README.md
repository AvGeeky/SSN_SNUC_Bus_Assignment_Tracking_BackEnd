
## 🚌 Real-Time Bus Tracking Platform
**Official deployment for SSN College of Engineering & Shiv Nadar University Chennai**  
*Production system serving students and faculty | 2025*

🔗 **Live System:** http://bustracker.snuchennai.edu.in/

A high-performance real-time mobility tracking backend designed to support thousands of concurrent users during peak campus commute hours.

---

# 🚀 Key Highlights

- **3,000+ concurrent users** supported during peak traffic hours
- **Sub-50ms live location propagation** from ingestion → map render
- **Redis-backed in-memory state engine** for millisecond reads
- **Hot-path isolation** removing databases from live request paths
- **Concurrent GPS ingestion** from multiple heterogeneous vendor APIs
- **Adaptive polling system** based on data freshness and load
- **Full on-premise deployment** with production observability stack
-  **Concurrent GPS ingestion:** ~20 vendor GPS APIs are polled in parallel using Java threads (ExecutorService/CommandLineRunner), with **time-of-day adaptive polling intervals** to prevent cascading delays if a provider responds slowly.

---

# 🏗 System Architecture

```text
                 +----------------------+
                 |   GPS Hardware       |
                 | (Multiple Vendors)   |
                 +----------+-----------+
                            |
                            |
                     GPS API Polling
                            |
                            v
                +-----------------------+
                |   Spring Boot Backend |
                |  (Location Processor) |
                +-----------+-----------+
                            |
                Normalize / Clean GPS Data
                            |
                            v
                 +----------------------+
                 |        Redis         |
                 | In-Memory Live State |
                 | busId → GPS location |
                 | Caches student bus   |
                 | allocation and exam/ |
                 |  breakdown overrides |
                 +----------+-----------+
                            |
             +--------------+--------------+
             |                             |
             v                             v
     +---------------+            +---------------+
     |  Mobile App   |            |  Web Client   |
     | (Students)    |            | (Tracking UI) |
     +---------------+            +---------------+

         Monitoring & Observability Layer
      Prometheus → Metrics → Grafana Dashboards
```
## Performance at Scale

The system is engineered to support **thousands of simultaneous users** during campus rush hours.

### Key Optimizations

- **Redis Sets** acting as a high-speed in-memory state engine
- **No database access in the live request path**
- **Read-heavy endpoints optimized for in-memory access**

### Performance Characteristics

- **<50ms location propagation latency**
- Smooth UI updates during **morning and evening traffic spikes**
- Stable performance during **high concurrency bursts**

---

## Smart Location Processing

Campus buses use **different GPS hardware vendors**, producing inconsistent and noisy data streams.

The **Spring Boot backend acts as a normalization layer**, responsible for:

- Aggregating GPS signals from heterogeneous vendor APIs
- Cleaning noisy or inconsistent location updates
- Smoothing position data before publishing to clients
- Converting all GPS inputs into a **single unified location format**

The backend currently exposes **~60 API endpoints** used by mobile and web applications.

---

## Designed for Rush Hour

Campus transport usage spikes dramatically during commute windows.

To handle this load, the platform uses:

- **Containerized services** for predictable deployments
- **Each GPS API (~20 individual calls) are done using a seperate Java Thread using CommandLine Runner or Executor service with dynamic sleep times based on the time of the day for no cascading delays incase one api takes time for responding.**
- **Hot-path optimization** eliminating database reads
- **Adaptive GPS polling intervals** to reduce unnecessary upstream requests

These techniques ensure the system remains stable even when **thousands of users open the app simultaneously**.

---

## 📊 Observability & Reliability

The system runs **entirely on university infrastructure** with full operational visibility.

### Monitoring Stack

- **Prometheus** → system and application metrics
- **Grafana** → real-time operational dashboards
- **Custom alert pipelines**

Operational alerts allow issues to be **detected and resolved within minutes**, often before users notice service disruption.

---

## System Design Decisions

### Why Redis as the Live State Store?

The application is **read-heavy**, with thousands of clients requesting the same bus locations.

Redis enables:

- **In-memory reads (microsecond latency)**
- Efficient keyed access (`busId → location`)
- High throughput during traffic spikes

Using Redis prevents the primary database from becoming a bottleneck.

---

### Why Hot-Path Isolation?

Real-time location endpoints **never touch the SQL database**.

Instead:

1. GPS updates are processed by the backend
2. Cleaned data is written to Redis
3. Client requests read directly from Redis

#### Benefits

- Eliminates disk I/O during peak load
- Ensures predictable low latency
- Prevents cascading database failures

---

### Stateless Backend Design

Backend services are designed to be **stateless**, enabling:

- Horizontal scaling via containers
- Easy rolling deployments
- Simplified fault recovery

All shared state is **externalized into Redis**.

---

## Tech Stack
## 🧰 Tech Stack

- **Java**
- **Spring Boot**
- **Redis**
- **PostgreSQL**
- **Docker**
- **NGINX**
- **Prometheus**
- **Grafana**

---

## 📌 Deployment

The platform is deployed **on-premise within university infrastructure**, providing:

- **Low-latency internal network access**
- **Full operational control over infrastructure**
- **Secure integration with campus systems**

- Deployment NGINX, Docker Compose file and other configurations HAVE NOT BEEN pushed for security purposes. Cradle docker compose has been pushed for building the jar file to dockerhub. Any other configs are not the deployment config. 
---

## 📈 Impact

- Serves **thousands of students and faculty daily**
- Provides **real-time visibility into campus transport routes**
- Handles **large traffic spikes reliably during peak commute hours**

