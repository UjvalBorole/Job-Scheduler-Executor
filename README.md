# 🛠️ Job Scheduler

This project is a **Job Scheduler** designed to handle timed, recurring, and dependency-based job executions using a structured backend workflow.

The architecture, as illustrated in the `job-scheduler.excalidraw` file, supports:
- **One-time and recurring jobs**
- **Job dependency resolution**
- **Retry logic and failure handling**
- **Partitioned or parallel execution**

---

## 📐 Architecture Overview

![Job Scheduler Architecture](./job%20sheduler.excalidraw)

> 🔍 Refer to the `job sheduler.excalidraw` file for a visual flow of the system components, including job ingestion, scheduling, execution, and status tracking.

---

## 💡 Features

- ⏰ Cron-like and one-time job scheduling
- 🔁 Retry mechanism with configurable limits
- ⚙️ Sequential and parallel execution support
- 🧩 Dependency tracking across job sequences
- 📦 Pluggable executor framework (e.g., Bash, Python, Kafka, etc.)

---

## 🚀 Getting Started

### Prerequisites

- Java 17+
- Kafka (for queue-based dispatching)
- PostgreSQL or Redis (for job persistence)
- Spring Boot (if using Spring)

### Run Locally

1. Clone the repository:

```bash
git clone https://github.com/your-username/job-scheduler.git
cd job-scheduler
