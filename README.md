# 🧩 Distributed Job Scheduling System

![Build](https://img.shields.io/badge/build-passing-brightgreen?style=flat-square)
![Docker](https://img.shields.io/badge/docker-ready-blue?style=flat-square)
![Kubernetes](https://img.shields.io/badge/kubernetes-deployed-326ce5?style=flat-square)
![License](https://img.shields.io/badge/license-MIT-lightgrey?style=flat-square)
![Status](https://img.shields.io/badge/status-active-success?style=flat-square)

A **high-performance distributed job scheduling system** built with **Spring Boot microservices**, supporting **cron**, **manual**, and **interval-based triggers** for workflow orchestration.  
Designed for **scalability**, **resilience**, and **observability** using modern cloud-native technologies.

---

## 🚀 Key Features

✅ **Flexible Scheduling** – Cron, manual, and interval-based triggers  
✅ **Event-Driven Design** – Kafka-powered pub-sub with batching, retries, and replay  
✅ **High Performance** – Redis-backed priority queues for 40% faster job throughput  
✅ **Resilient Architecture** – API Gateway with retry & fallback patterns  
✅ **Polyglot Persistence** – PostgreSQL + MongoDB (sharded for scalability)  
✅ **Cloud-Native Deployment** – Dockerized microservices managed via Kubernetes  
✅ **Automated CI/CD** – Jenkins pipelines reducing downtime by 35%  
✅ **Full Observability** – Metrics, alerting, and dashboards via Prometheus & Grafana  

---

## 🏗️ Architecture Overview---

## 🧰 Tech Stack

| Category              | Technologies Used |
|------------------------|-------------------|
| **Backend Framework**  | Spring Boot (Microservices) |
| **Message Broker**     | Apache Kafka |
| **Caching & Queuing**  | Redis |
| **Databases**          | PostgreSQL, MongoDB (Sharded) |
| **Containerization**   | Docker |
| **Orchestration**      | Kubernetes |
| **CI/CD**              | Jenkins |
| **Monitoring**         | Prometheus, Grafana |

---

## ⚙️ Installation & Deployment

### 1️⃣ Clone the Repository
```bash
https://github.com/UjvalBorole/Job-Scheduler.git
