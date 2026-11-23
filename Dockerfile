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

RUN mkdir -p /app && printf '#!/bin/sh\nset -e\ncase "$DATABASE_URL" in\n  postgres://*|postgresql://*)\n    db_location="${DATABASE_URL#*://}"\n    db_credentials="${db_location%%@*}"\n    db_host_path="${db_location#*@}"\n    db_host_port="${db_host_path%%/*}"\n    db_path="/${db_host_path#*/}"\n    db_host="${db_host_port%%:*}"\n    db_port="${db_host_port#*:}"\n    if [ "$db_port" = "$db_host_port" ]; then db_port="5432"; fi\n    export DATABASE_URL="jdbc:postgresql://${db_host}:${db_port}${db_path}"\n    ;;\nesac\nexport DATABASE_URL="${DATABASE_URL:-jdbc:postgresql://localhost:5432/nothing}"\nexport PORT="${PORT:-10000}"\nexport SERVER_PORT=8080\nsed -e "s/{{PORT}}/${PORT}/g" -e "s/{{SERVER_PORT}}/${SERVER_PORT}/g" /etc/nginx/templates/default.conf.template > /etc/nginx/conf.d/default.conf\njava -Dserver.port=8080 -jar /app/app.jar &\nnginx -g "daemon off;"\n' > /app/start.sh && chmod +x /app/start.sh

ENV PORT=10000
EXPOSE 10000
CMD ["/app/start.sh"]
