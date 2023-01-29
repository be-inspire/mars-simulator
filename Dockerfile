FROM maven:3-adoptopenjdk-14 as build

WORKDIR /build

ARG SSH_PRIVATE_KEY

RUN mkdir -p /root/.ssh && umask 0077 && echo "${SSH_PRIVATE_KEY}" > /root/.ssh/id_rsa
RUN ssh-keyscan github.com >> ~/.ssh/known_hosts
RUN git clone git@github.com:opalcompany/invosys.git
WORKDIR invosys/invosys-java
RUN git pull
RUN mvn install -DskipITs

WORKDIR /app
COPY ./pom.xml ./
COPY mars-lib/pom.xml ./mars-lib/
COPY messages/pom.xml ./messages/
COPY side-messages/pom.xml ./side-messages/
COPY tower-lib/pom.xml ./tower-lib/
COPY platform-comm/pom.xml ./platform-comm/
COPY platform-sim/pom.xml ./platform-sim/
COPY mc-comm/pom.xml ./mc-comm/
COPY mc-lib/pom.xml ./mc-lib/
COPY mc-sample/pom.xml ./mc-sample/
COPY tower-sample/pom.xml ./tower-sample/
COPY simulator-sample/pom.xml ./simulator-sample/

RUN mvn dependency:go-offline -B
#RUN --mount=type=cache,target=/root/.m2 mvn clean package

COPY mars-lib/src ./mars-lib/src
COPY messages/src ./messages/src
COPY side-messages/src ./side-messages/src
COPY tower-lib/src ./tower-lib/src
RUN touch ./tower-lib/jdk14logger.properties
COPY platform-comm/src ./platform-comm/src
COPY platform-sim/src ./platform-sim/src
RUN touch ./platform-sim/jdk14logger.properties
COPY mc-comm/src ./mc-comm/src
COPY mc-lib/src ./mc-lib/src
COPY mc-sample/src ./mc-sample/src
COPY tower-sample/src ./tower-sample/src
RUN mvn package

FROM azul/zulu-openjdk-alpine:14

WORKDIR /app

COPY --from=build /app/simulator-sample/target/simulator-sample-*.jar simulator.jar
CMD ["echo", "run me with docker-compose"]
