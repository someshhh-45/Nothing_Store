# Render deployment

The hosted container runs Nginx and Spring Boot together. PostgreSQL is provisioned by the Blueprint; Kafka, RabbitMQ and OpenSearch require separately configured reachable services.

1. Connect the GitHub repository to a Render Blueprint using main.
2. Supply the external service environment variables requested by render.yaml. Kafka SASL settings must match the provider. Configure RabbitMQ port and TLS as required by the provider before using it.
3. Set FRONTEND_URL to the actual HTTPS storefront origin and configure Stripe redirect URLs if using test payments.
4. Configure a verified mail provider for signup and optional notifications. Keep credentials in Render secrets.
5. Attach persistent storage at /app/product-uploads if using seller image uploads. Without it uploads are ephemeral; verify the chosen plan supports disks.
6. Verify catalog, login, seller uploads, search, checkout, outbox and delivery checks before public access.

The Blueprint does not guarantee a free plan or a production-ready service. CSRF is currently disabled in the application security configuration and must be addressed before public commerce. Do not accept real payments or customer data until security and operational checks pass.

The startup script supervises both processes and exits if either stops. The Nginx health check routes to the backend categories endpoint; it does not prove broker or search health.
