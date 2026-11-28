FROM eclipse-temurin:21.0.12_8-jdk-noble@sha256:5dd9dbc6f6eccb82184b340da9488b70b68d2af81474cf8f280f8ce6b2933282 AS build
ARG MAVEN_VERSION=3.9.11
ARG MAVEN_SHA512=bcfe4fe305c962ace56ac7b5fc7a08b87d5abd8b7e89027ab251069faebee516b0ded8961445d6d91ec1985dfe30f8153268843c89aa392733d1a3ec956c9978
# Maven Central rate-limits shared CI runners (HTTP 429). Try the GCS copy of Central,
# then Central, then the Apache archive, with retries; the pinned SHA-512 below is the
# only integrity authority, whichever host served the file.
RUN for url in \
      "https://maven-central.storage-download.googleapis.com/maven2/org/apache/maven/apache-maven/${MAVEN_VERSION}/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
      "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${MAVEN_VERSION}/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
      "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz"; do \
      curl --fail --silent --show-error --location --retry 5 --retry-delay 10 --retry-all-errors \
        "$url" --output /tmp/apache-maven.tar.gz && break; \
      rm -f /tmp/apache-maven.tar.gz; \
    done \
 && echo "${MAVEN_SHA512}  /tmp/apache-maven.tar.gz" | sha512sum --check --strict \
 && tar -xzf /tmp/apache-maven.tar.gz -C /opt \
 && ln -s "/opt/apache-maven-${MAVEN_VERSION}/bin/mvn" /usr/local/bin/mvn \
 && rm /tmp/apache-maven.tar.gz
# Dependencies: the GCS copy of Central is tried first and Central stays second, so a
# mirror outage or sync lag falls back automatically. Checksums are verified strictly.
RUN mkdir -p /root/.m2 && printf '%s\n' \
      '<settings>' \
      ' <profiles><profile><id>gcs-central-first</id>' \
      '  <repositories><repository><id>gcs-maven-central</id>' \
      '   <url>https://maven-central.storage-download.googleapis.com/maven2/</url>' \
      '   <releases><enabled>true</enabled></releases><snapshots><enabled>false</enabled></snapshots>' \
      '  </repository></repositories>' \
      '  <pluginRepositories><pluginRepository><id>gcs-maven-central</id>' \
      '   <url>https://maven-central.storage-download.googleapis.com/maven2/</url>' \
      '   <releases><enabled>true</enabled></releases><snapshots><enabled>false</enabled></snapshots>' \
      '  </pluginRepository></pluginRepositories>' \
      ' </profile></profiles>' \
      ' <activeProfiles><activeProfile>gcs-central-first</activeProfile></activeProfiles>' \
      '</settings>' > /root/.m2/settings.xml
ENV MAVEN_ARGS="--strict-checksums -Daether.connector.http.retryHandler.count=5 -Daether.connector.http.retryHandler.serviceUnavailable=429,503"
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21.0.12_8-jre-noble@sha256:86883d2dc1d0e57d4fb2c539f5fd3a2155749c0bdcdccb9cb452828bdc8b0caf
WORKDIR /app
RUN groupadd --system --gid 10001 aetheris \
 && useradd --system --uid 10001 --gid aetheris --create-home --home-dir /home/aetheris --shell /usr/sbin/nologin aetheris \
 && install -d -o 10001 -g 10001 /app/workspace
COPY --from=build --chown=10001:10001 /app/target/*.jar app.jar
USER 10001:10001
EXPOSE 8090
ENTRYPOINT ["java","-jar","app.jar"]
