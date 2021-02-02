# tsc4j
=======
# typesafe-config-addons

# Building

## Prerequisites

* [JDK8+](https://dev.java.net/)
* [docker-compose](https://docs.docker.com/compose/install/)

## building with tests

* start `docker-compose` managed services required for tests:
  * create docker-compose environment (one-time job):
  ```
  docker-compose up --no-start
  ```
  * start docker compose services
  ```
  docker-compose start
  ```

* build project and run tests
```
./gradlew clean build
```

... or you can build project without running tests
```
./gradlew clean build -xtest
```
