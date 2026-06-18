# Real-Time Event-Driven Notification System 🚀

An enterprise-grade, event-driven notification system built with Java Spring Boot, Apache Kafka, MongoDB Vector Search, and AI integrations (Cohere & Groq). The system is fully containerized with Docker and features automated CI/CD deployment to Azure via GitHub Actions.

## ✨ Features

- **Real-Time Delivery:** WebSocket integration ensures users receive notifications instantly without refreshing the page.
- **Event-Driven Architecture:** Decoupled microservices using Apache Kafka for high throughput and reliability.
- **Semantic Search:** Find related notifications using natural language, powered by **Cohere's Embeddings** and **MongoDB Atlas Vector Search**.
- **AI Summarization:** Automatically summarizes long, complex notifications into concise bullet points using **Groq (LLaMA-3)**.
- **Dead Letter Queue (DLQ):** Fault-tolerant message processing ensuring zero data loss if downstream systems fail.
- **Modern UI:** Responsive, glassmorphism-styled frontend built with HTML/CSS/JS.

## 🏗️ Architecture

The system consists of several containerized components routed through an Nginx reverse proxy:

1. **Frontend (Nginx):** Serves the UI and proxies API requests.
2. **Producer Service (Spring Boot):** Receives incoming events and publishes them to Kafka.
3. **Kafka & Zookeeper:** Handles the message queueing and event streaming.
4. **Notification Service (Spring Boot):** Consumes Kafka messages, processes AI features, saves to MongoDB, and pushes to clients via WebSockets.
5. **MongoDB:** Stores users, notifications, and vector embeddings for semantic search.

## 🚀 Live Demo
You can view the live deployed application here:
[http://notif-system.centralindia.cloudapp.azure.com](http://notif-system.centralindia.cloudapp.azure.com)

## 🛠️ Local Development Setup

### Prerequisites
- Docker and Docker Compose
- Groq API Key
- Cohere API Key

### 1. Configure Environment Variables
Create a `.env` file in the root directory:
```env
# Kafka external hostname (leave as localhost for local dev)
KAFKA_EXTERNAL_HOST=localhost

# JWT Secret Key
JWT_SECRET=your_super_secret_jwt_key_here

# AI API Keys
COHERE_API_KEY=your_cohere_api_key
GROQ_API_KEY=your_groq_api_key
```

### 2. Run with Docker Compose
From the root directory, run:
```bash
docker compose up --build -d
```

### 3. Access the Application
- Frontend: `http://localhost:80`
- Producer API: `http://localhost:80/producer/`
- Notification API: `http://localhost:80/api/`

## 🔄 CI/CD Pipeline
This project uses **GitHub Actions** for automated deployment. Any push to the `main` branch triggers a workflow that securely SSHs into the Azure VM, pulls the latest code, and restarts the Docker containers automatically with zero downtime.
