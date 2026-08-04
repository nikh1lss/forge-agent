# forge in a container.
#
# The point of this image is the bash tool. Outside a container, `bash` runs
# model-authored commands as your user. Here the only host path that
# exists is the project you bind-mount at /work, 

#  build
FROM eclipse-temurin:24-jdk AS build

WORKDIR /src

# Wrapper and build definitions land in their own layer: they change far less
# often than sources.
COPY gradlew gradle.properties settings.gradle.kts ./
COPY gradle ./gradle
COPY agent/build.gradle.kts ./agent/

RUN ./gradlew --no-daemon --console=plain \
        :agent:dependencies --configuration runtimeClasspath > /dev/null

COPY agent/src ./agent/src

RUN ./gradlew --no-daemon --console=plain installDist

# runtime
FROM eclipse-temurin:24-jdk

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        git \
        less \
        unzip \
        zip \
    && rm -rf /var/lib/apt/lists/*

# uid 1000 so files written into the bind mount come out owned by a normal user
# instead of root. The Ubuntu base already ships a uid-1000 account, so drop it
# first. HOME and the Gradle cache are world-writable because `docker run
# --user` with some other uid still needs somewhere to put caches — a fresh
# named volume inherits these permissions when it is first populated.
RUN if id -u 1000 > /dev/null 2>&1; then userdel -r "$(id -nu 1000)" || true; fi \
    && useradd --create-home --uid 1000 --user-group forge \
    && mkdir -p /home/forge/.gradle \
    && chmod 0777 /home/forge /home/forge/.gradle

COPY --from=build /src/agent/build/install/agent /opt/forge
RUN ln -s /opt/forge/bin/agent /usr/local/bin/forge

ENV HOME=/home/forge
USER forge
WORKDIR /work

ENTRYPOINT ["/opt/forge/bin/agent"]
