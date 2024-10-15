# Build stage
FROM maven:3.9.9-amazoncorretto-23 AS build

WORKDIR /app

# Copy project files and build, caching Maven dependencies folder
COPY . /app
RUN --mount=type=cache,target=/root/.m2 mvn clean package -DskipTests

# Final stage
FROM amazoncorretto:23-alpine AS runtime

# Set default user/group information
ARG GROUPNAME=Alfresco
ARG GROUPID=1000
ARG USERNAME=alfresco-search
ARG USERID=33033

# Create the user and group in the same layer, avoiding multiple layers
RUN addgroup -g ${GROUPID} ${GROUPNAME} && \
    adduser -u ${USERID} -G ${GROUPNAME} -s /bin/sh -D ${USERNAME}

# Set work directory
WORKDIR /app

# Copy the jar file from the build stage
COPY --from=build /app/target/open-ass-*.jar /app/open-ass.jar

# Set the correct permissions for the application files
RUN chown -R ${USERNAME}:${GROUPNAME} /app

# Switch to the non-root user
USER ${USERNAME}

# Default entrypoint for running the application
ENTRYPOINT ["java", "-jar", "/app/open-ass.jar"]