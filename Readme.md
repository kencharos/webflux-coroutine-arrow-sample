# Spring WebFlux samples with Kotlin Coroutines and Arrow

## Requiremens

+ Java8

## Useage

### Running server

server app is backend API, running in 8080 port.

+ cd server
+ `./gradlew bootRun`

### Running fluxk

fluxk is WebFlux client application, runngin in 8082 port.
This app connect to server app.

+ cd fluxk
+ `./gradlew bootRun`
+ access http://localhost:8082/

