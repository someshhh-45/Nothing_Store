FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend/pom.xml ./pom.xml
COPY backend/src ./src
RUN mvn -B -ntp -DskipTests package

FROM nginx:1.27-alpine
RUN apk add --no-cache openjdk21-jre bash curl
WORKDIR /app
COPY --from=build /workspace/target/*.jar /app/app.jar
COPY nothing-coffee /usr/share/nginx/html
COPY backend/nginx.conf.render /etc/nginx/templates/default.conf.template

COPY backend/scripts/start-render.sh /app/start.sh
RUN chmod +x /app/start.sh && mkdir -p /app/product-uploads
ENV PORT=10000
EXPOSE 10000
ENTRYPOINT ["/app/start.sh"]
