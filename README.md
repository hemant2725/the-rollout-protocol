# The Rollout Protocol - Java Spring Boot Canary Rollout System Demo

This project is a small Java Spring Boot microservices lab that demonstrates
progressive delivery, feature flags, canary rollout, and automatic rollback.

It shows how a new version of a service can be released to a small percentage
of traffic first, monitored for errors, and then either promoted or rolled
back safely.

## What this project does

- Routes traffic between a stable service and a canary service.
- Uses a feature flag service to control rollout percentage.
- Simulates a new release going to only part of the traffic.
- Automatically promotes the rollout when the canary is healthy.
- Automatically rolls back when the canary error rate is too high.

## High-Level Idea

Instead of sending all users to a new version at once, this system starts with
small traffic exposure:

- 0% means everything goes to stable.
- 1%, 5%, 25%, 50%, and 100% are used as rollout steps.
- The rollout controller watches service health and moves between those steps.

This is the same basic strategy used in real production deployments to reduce
risk.

## Architecture

```text
Client
  |
  v
Router :8080
  | \
  |  \
  |   -> Stable App :8082  -> /process, /health
  |
  +---> Canary App  :8083  -> /process, /health
  
Router reads rollout config from Flag Service :8081
Rollout Controller :8084 watches router health and updates rollout
```

## Services

### 1. Flag Service

Port: `8081`

Purpose:

- Stores the feature flag state for `fraud-model-v2`.
- Keeps track of whether the flag is enabled.
- Stores the current rollout percentage.

Endpoints:

- `GET /flags/{name}`
- `POST /flags/{name}/enable`
- `POST /flags/{name}/disable`
- `POST /flags/{name}/rollout?percent=N`

Default flag:

```json
{
  "name": "fraud-model-v2",
  "enabled": false,
  "rolloutPercent": 0
}
```

Notes:

- The flag data is stored in memory.
- Restarting the service resets the state.

### 2. Stable App

Port: `8082`

Purpose:

- Represents the stable production version.
- Handles requests when traffic is not routed to canary.

Endpoints:

- `GET /process`
- `GET /health`

Sample `/process` response:

```json
{
  "version": "v1",
  "result": "processed by stable",
  "status": "ok"
}
```

### 3. Canary App

Port: `8083`

Purpose:

- Represents the new version being tested.
- Receives a small percentage of traffic during rollout.

Endpoints:

- `GET /process`
- `GET /health`

Sample `/process` response:

```json
{
  "version": "v2",
  "result": "processed by canary with fraud model",
  "status": "ok"
}
```

Error simulation:

- The canary app supports the environment variable `SIMULATE_ERRORS=true`.
- When enabled, the canary will intentionally fail a large portion of requests.
- This is used to test rollback behavior.

### 4. Router

Port: `8080`

Purpose:

- Acts as the entry point for requests.
- Reads the rollout percentage from the flag service.
- Randomly routes traffic to stable or canary.
- Exposes traffic statistics.

Endpoints:

- `GET /route`
- `GET /health`
- `GET /stats`

Important:

- The router does **not** expose `/score`.
- If you call `http://localhost:8080/score`, you will get `404 Not Found`.
- The correct traffic endpoint is `http://localhost:8080/route`.

Routing behavior:

- The router asks the flag service for the current `rolloutPercent`.
- It generates a random number from 0 to 99.
- If the random number is lower than the rollout percentage, traffic goes to canary.
- Otherwise traffic goes to stable.

Example:

- `rolloutPercent = 25`
- About 25% of requests go to canary.
- About 75% of requests go to stable.

### 5. Rollout Controller

Port: `8084`

Purpose:

- Automatically manages rollout progression.
- Watches service health through the router.
- Promotes or rolls back the rollout based on canary error rate.

Endpoint:

- `GET /status`

Decision logic:

- If canary error rate is `>= 0.5`, the rollout is rolled back.
- If canary error rate is `< 0.1`, the rollout is promoted to the next step.
- Promotion steps are: `1% -> 5% -> 25% -> 50% -> 100%`

## Request Flow

### Normal request path

1. Client calls the router at `GET /route`.
2. Router reads the flag service to get the rollout percentage.
3. Router randomly chooses stable or canary.
4. Router calls either:
   - `http://app-stable:8082/process`
   - `http://app-canary:8083/process`
5. Router returns the service response plus routing metadata.

### Health flow

1. Rollout controller calls `GET /health` on the router.
2. Router checks stable and canary health.
3. Router returns a combined health object.
4. Rollout controller uses the canary error rate to decide whether to promote or roll back.

## Technology Stack

- Java
- Spring Boot
- Spring Web / WebFlux client
- Maven
- Docker
- Docker Compose

## Project Structure

```text
The Rollout Protocol
├── app-stable
├── app-canary
├── flag-service
├── router
├── rollout-controller
├── docker-compose.yml
├── Makefile
└── pom.xml
```

## Prerequisites

Install the following:

- Java 17 or newer
- Maven
- Docker Desktop
- Docker Compose

Check versions:

```bash
java -version
mvn -version
docker --version
docker-compose --version
```

## Build and Run

### Option 1: Run everything with Docker Compose

```bash
docker-compose up --build -d
```

This builds and starts all services:

- flag-service
- app-stable
- app-canary
- router
- rollout-controller

Stop everything:

```bash
docker-compose down
```

### Option 2: Build with Maven

From the project root:

```bash
mvn clean package -DskipTests
```

This builds all modules in the multi-module Maven project.

## Service Ports

| Service | Port | Purpose |
| --- | ---: | --- |
| flag-service | 8081 | Stores the feature flag and rollout percent |
| app-stable | 8082 | Stable version |
| app-canary | 8083 | Canary version |
| router | 8080 | Routes traffic |
| rollout-controller | 8084 | Automatic rollout decisions |

## Manual Testing

The examples below use PowerShell because this project is being run on Windows.

## End-to-End Test Checklist

Use this if you want to test the whole system from start to finish.

1. Build everything:

```powershell
mvn clean package -DskipTests
```

2. Start the stack:

```powershell
docker-compose up --build -d
```

3. Wait for the services to become healthy, then verify:

```powershell
Invoke-RestMethod http://localhost:8081/health
Invoke-RestMethod http://localhost:8082/health
Invoke-RestMethod http://localhost:8083/health
Invoke-RestMethod http://localhost:8080/health
Invoke-RestMethod http://localhost:8084/status
Invoke-RestMethod http://localhost:8090/
```

4. Check the initial flag state:

```powershell
Invoke-RestMethod http://localhost:8081/flags/fraud-model-v2
```

Expected:

- `enabled = false`
- `rolloutPercent = 0`

5. Enable the feature flag:

```powershell
Invoke-RestMethod -Method Post http://localhost:8081/flags/fraud-model-v2/enable
```

6. Set rollout to 1%:

```powershell
Invoke-RestMethod -Method Post "http://localhost:8081/flags/fraud-model-v2/rollout?percent=1"
```

7. Send traffic through the router:

```powershell
1..100 | ForEach-Object { Invoke-RestMethod http://localhost:8080/route | Out-Null }
```

8. Check routing stats:

```powershell
Invoke-RestMethod http://localhost:8080/stats
```

Expected:

- Most requests go to stable at low rollout percentages
- The response includes `totalRequests`, `stableCount`, `canaryCount`, and `canaryPercent`

9. Check the service responses directly:

```powershell
Invoke-RestMethod http://localhost:8082/process
Invoke-RestMethod http://localhost:8083/process
```

10. Watch the rollout controller:

```powershell
Invoke-RestMethod http://localhost:8084/status
```

Expected:

- If canary error rate stays below `0.1`, the rollout advances through `1%`, `5%`, `25%`, `50%`, and `100%`
- If canary error rate reaches `0.5` or higher, the rollout is rolled back to `0%`

11. Open the dashboard:

```text
http://localhost:8090/
```

12. Stop the stack when you are done:

```powershell
docker-compose down
```

### 1. Check the current flag state

```powershell
Invoke-RestMethod http://localhost:8081/flags/fraud-model-v2
```

### 2. Enable the feature flag

```powershell
Invoke-RestMethod -Method Post http://localhost:8081/flags/fraud-model-v2/enable
```

### 3. Set rollout to 1%

```powershell
Invoke-RestMethod -Method Post "http://localhost:8081/flags/fraud-model-v2/rollout?percent=1"
```

### 4. Generate traffic through the router

```powershell
1..100 | ForEach-Object { Invoke-RestMethod http://localhost:8080/route | Out-Null }
```

### 5. Check router stats

```powershell
Invoke-RestMethod http://localhost:8080/stats
```

### 6. Check router health

```powershell
Invoke-RestMethod http://localhost:8080/health
```

### 7. Test stable directly

```powershell
Invoke-RestMethod http://localhost:8082/process
```

### 8. Test canary directly

```powershell
Invoke-RestMethod http://localhost:8083/process
```

### 9. Check rollout controller status

```powershell
Invoke-RestMethod http://localhost:8084/status
```

## Demo Scenarios

### Scenario 1: Safe rollout

1. Start the system normally.
2. Enable `fraud-model-v2`.
3. Set rollout to `1%`.
4. Send traffic to `GET /route`.
5. Watch `GET /status` on the rollout controller.

Expected result:

- The rollout controller promotes the canary step by step.
- The rollout can move through `1%`, `5%`, `25%`, `50%`, and `100%`.

### Scenario 2: Rollback test

1. Start the canary with `SIMULATE_ERRORS=true`.
2. Enable the feature flag.
3. Set rollout to `50%`.
4. Send traffic to `GET /route`.
5. Watch the rollout controller.

Expected result:

- Canary error rate becomes high.
- The rollout controller rolls the release back to `0%`.
- The feature flag is disabled.

## Windows PowerShell Example for Error Simulation

If you want to start Docker Compose with canary errors enabled:

```powershell
$env:SIMULATE_ERRORS = "true"
docker-compose up --build -d
```

To go back to normal behavior:

```powershell
Remove-Item Env:SIMULATE_ERRORS
docker-compose down
docker-compose up --build -d
```

## Common Mistakes

### Wrong endpoint

This will fail:

```powershell
Invoke-RestMethod http://localhost:8080/score
```

Reason:

- `/score` is not implemented.

Use this instead:

```powershell
Invoke-RestMethod http://localhost:8080/route
```

### Docker is not running

If you see a connection error, start Docker Desktop first.

### Service is not ready yet

After starting the stack, wait a little for health checks to pass.

### Port conflict

If a port is already in use, stop the process using that port or update the compose file.

## Implementation Notes

- The flag service keeps state in memory.
- The rollout controller keeps its current rollout state in memory.
- There is no database in this project.
- The router uses the flag service to decide how much traffic should go to canary.
- The canary app can be healthy or intentionally error-prone, depending on `SIMULATE_ERRORS`.

## Learning Outcomes

This project demonstrates:

- Spring Boot microservices
- REST API design
- Feature flags
- Canary deployment
- Progressive rollout
- Automated rollback
- Service-to-service communication
- Dockerized local development

## Conclusion

This repository is a hands-on demo of progressive delivery with Java Spring
Boot. It shows how to release a new version safely by routing a small amount of
traffic to a canary, watching health and error rates, and then either
promoting the rollout or rolling it back.
