Application.yml setup
```
spring:
  data:
    mongodb:
      uri: <mongo_db_config_link>
      database: journaldb
      auto-index-creation: true
  main:
    allow-circular-references: true

server:
  port: 8081
  servlet:
    context-path: /journal

weather:
  api:
    key: <weatherstack_api_key>
```

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=Dru-O7_JournalApp&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=Dru-O7_JournalApp)
