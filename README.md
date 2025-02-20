# Journal App APIs

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=Dru-O7_JournalApp&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=Dru-O7_JournalApp)

## Overview
This project provides a RESTful API for the **Journal App**, enabling users to create, update, delete, and view journal entries. It also includes authentication and admin functionalities.

## Base URL
```
http://localhost:8081/journal
```

## Authentication
The API uses **JWT Bearer Authentication** to secure endpoints.

## API Endpoints

### 1. Public APIs
These endpoints are accessible without authentication.

| Endpoint               | Method | Description                  |
|------------------------|--------|------------------------------|
| `/public/signup`       | POST   | User registration            |
| `/public/login`        | POST   | User login                   |
| `/public/health-check` | GET    | API health check             |

### 2. User APIs
These endpoints require authentication.

| Endpoint      | Method | Description                |
|--------------|--------|----------------------------|
| `/user`      | GET    | Retrieve a greeting message |
| `/user`      | PUT    | Update user details        |
| `/user`      | DELETE | Delete user account       |

### 3. Journal Entry APIs
Users can manage journal entries using these endpoints.

| Endpoint                  | Method | Description                                      |
|---------------------------|--------|--------------------------------------------------|
| `/journal`                | GET    | Retrieve all journal entries                    |
| `/journal/id/{myId}`      | GET    | Retrieve a journal entry by ID                  |
| `/journal/id/{myId}`      | PUT    | Update a journal entry by ID                    |
| `/journal/id/{myId}`      | DELETE | Delete a journal entry by ID                    |
| `/journal`                | POST   | Create a new journal entry (Sentiments: HAPPY, SAD, ANGRY, ANXIOUS) |

### 4. Admin APIs
These endpoints require authentication with admin privileges.

| Endpoint                      | Method | Description                         |
|--------------------------------|--------|-------------------------------------|
| `/admin/create-admin-user`     | POST   | Create an admin user               |
| `/admin/update-admin-user`     | PUT    | Update admin user details          |
| `/admin/all-users`             | GET    | Retrieve all users                 |

## Technologies Used
- **Spring Boot** - Backend framework
- **MongoDB** - NoSQL database
- **Redis** - Caching mechanism
- **Spring Security** - Authentication & authorization
- **JWT (JSON Web Tokens)** - Token-based authentication
- **SonarCloud** - Code quality and security analysis

## Configuration
The following **`application.yml`** file is required for application setup:

```yaml
spring:
  data:
    mongodb:
      uri: mongodb+srv://<your-mongodb-uri>
      database: journaldb
      auto-index-creation: true
  main:
    allow-circular-references: true

  mail:
    host: smtp.gmail.com
    port: 587
    username: <your-email>
    password: <your-app-password>
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true

  redis:
    host: <your-redis-host>
    port: <your-redis-port>
    password: <your-redis-password>

server:
  port: 8081
  servlet:
    context-path: /journal

weather:
  api:
    key: <your-weather-api-key>

springdoc:
  api-docs:
    enabled: true
  swagger-ui:
    enabled: true
```

## MongoDB Collection Setup
Before running the application, manually create a **MongoDB collection** named **config_journal_app** with the following key-value pairs:

```json
{
  "key": "weather_api",
  "value": "http://api.weatherstack.com/current?access_key=<apiKey>&query=<city>"
}
```

```json
{
  "key": "quote_api",
  "value": "https://stoic.tekloon.net/stoic-quote"
}
```

## Code Quality & Security
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=Dru-O7_JournalApp&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=Dru-O7_JournalApp)

This project undergoes continuous **code quality and security analysis** using **SonarCloud**.

## Running the Application
1. Clone the repository:
   ```sh
   git clone https://github.com/your-repository/journal-app.git
   ```
2. Navigate to the project directory:
   ```sh
   cd journal-app
   ```
3. Build and run the application:
   ```sh
   mvn spring-boot:run
   ```

## Running Locally
Access the API documentation via **Swagger UI** at:
```
http://localhost:8081/journal/swagger-ui/index.html
```

## License
This project is licensed under the **MIT License**.

