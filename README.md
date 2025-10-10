# Student Course Registration (Java CLI) — Step-by-step project

This is a non-Spring, command-line Java implementation of the Student Course Registration microservice that uses **AWS DynamoDB (local or remote)** for persistence. It follows the requirements in the uploaded requirements document. fileciteturn0file0

---

## What is included (quick)

- `pom.xml` with dependencies (AWS SDK v2 for DynamoDB, jBCrypt, JUnit).
- A minimal Java CLI app (`Main.java`) that supports signup, login, list courses, enroll, drop, join waitlist.
- DAOs that use the AWS SDK v2 DynamoDbClient to read/write items.
- DynamoDB table creation scripts (AWS CLI) for Local and AWS (five tables: Students, Courses, Enrollments, Waitlists, StudentLogs).
- Sample datasets (JSON) for `Students` and `Courses` to preload.
- PlantUML files for Class Diagram and a few Sequence Diagrams.
- A short README section explaining how to run locally with **DynamoDB Local** and how to load sample data.

**Library notes / versions** (used in this project):
- AWS SDK for Java v2 (DynamoDB) — artifact `software.amazon.awssdk:dynamodb:2.33.1`. citeturn0search0
- DynamoDB Local recommended for local testing; see AWS docs. citeturn0search2
- Password hashing: `org.mindrot:jbcrypt:0.4`. citeturn0search3
- JUnit Jupiter (tests) `org.junit.jupiter:junit-jupiter:5.10.0`. citeturn1search0

---

## Quick start (run locally with DynamoDB Local)

1. Download and run DynamoDB Local (or use the Docker image). See the official docs for options. citeturn0search2

2. Start DynamoDB Local (example using Docker):
   ```bash
   docker run -p 8000:8000 amazon/dynamodb-local
   ```

3. Create the tables (script included): `dynamodb_create_tables.sh` — this script uses `aws` CLI pointed at `--endpoint-url http://localhost:8000`.

4. Load sample data: `sample_data/load_sample_data.sh` (provided).

5. Build & run the Java app:
   ```bash
   mvn clean package
   java -jar target/student-course-registration-1.0-SNAPSHOT.jar
   ```

6. The CLI uses a Scanner for simple input — follow the menu.

---

## Files to inspect / edit

- `src/main/java/org/example/registration` — main code
- `dynamodb_create_tables.sh` — create tables using AWS CLI (Local)
- `sample_data/*.json` — sample students & courses

---

## Notes on correctness and transactions

- The sample code demonstrates conditional updates (to prevent overbooking) and shows where to replace with `TransactWriteItems` for stronger atomic guarantees.
- For production-grade atomicity under high concurrency, prefer DynamoDB transactions (`TransactWriteItems`) and optimistic locking patterns; see AWS docs. citeturn0search12

---

Enjoy — files are in this ZIP. If you want, I can:
- add a complete `TransactWriteItems`-based enroll implementation (server-safe), or
- convert this into a Spring Boot example (if preferred).
