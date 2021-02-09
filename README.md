# tsc4j

![CI](https://github.com/tsc4j/tsc4j/workflows/Java%20CI/badge.svg)
[![codecov](https://codecov.io/gh/tsc4j/tsc4j/branch/main/graph/badge.svg?token=78FQI7NZUE)](https://codecov.io/gh/tsc4j/tsc4j)

# typesafe-config-addons

# Building

## Prerequisites

* [JDK8+](https://dev.java.net/)
* [docker-compose](https://docs.docker.com/compose/install/)

## Building with tests

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

## Building a tsc4j cli uberjar

If you want to build self-containing `tsc4j-cli` [uberjar], you'll have to build it on your own:

* edit [tsc4j-uberjar/build.gradle](tsc4j-uberjar/build.gradle) and include only tsc4j dependencies that you need
* build the uberjar
```
./gradlew tsc4j-uberjar:shadowJar
```
* use it:
```
$ java -jar tsc4j-uberjar/build/libs/tsc4j.jar --help
```

[uberjar]: https://stackoverflow.com/questions/11947037/what-is-an-uber-jar
