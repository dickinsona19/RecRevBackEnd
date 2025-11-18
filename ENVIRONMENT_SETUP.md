# Environment Variables Setup Guide

## Overview

This project uses environment variables for sensitive configuration. This ensures that secrets are not committed to version control and can be easily configured per environment.

## Setup Instructions

### 1. Create `.env` file (for local development)

Copy the `.env.example` file to `.env` in the root of the `Boss-Lifting-Club-API` directory:

```bash
cp .env.example .env
```

### 2. Fill in your values

Edit the `.env` file and replace all placeholder values with your actual credentials.

**IMPORTANT:** Never commit the `.env` file to version control. It should already be in `.gitignore`.

### 3. Required Environment Variables

#### Critical (Must be set in production):
- `JWT_SECRET` - Secret key for JWT token signing (minimum 64 characters)
- `STRIPE_SECRET_KEY` - Stripe API secret key
- `STRIPE_WEBHOOK_SECRET` - Stripe webhook signing secret
- `STRIPE_WEBHOOK_SUBSCRIPTION_SECRET` - Stripe subscription webhook secret

#### Important (Should be set):
- `TWILIO_ACCOUNT_SID` - Twilio account SID
- `TWILIO_AUTH_TOKEN` - Twilio authentication token
- `SPRING_MAIL_USERNAME` - Email username for sending emails
- `SPRING_MAIL_PASSWORD` - Email app password

#### Optional (Have defaults):
- `SERVER_PORT` - Server port (default: 8081)
- `SPRING_PROFILES_ACTIVE` - Active profile (default: local)
- `JWT_EXPIRATION` - JWT expiration in milliseconds (default: 604800000 = 7 days)
- `JWT_REFRESH_EXPIRATION` - Refresh token expiration (default: 2592000000 = 30 days)

## Loading Environment Variables

### Local Development

For local development, you can:

1. **Use a `.env` file** (recommended):
   - Spring Boot doesn't natively support `.env` files
   - Use a library like `dotenv-java` or set variables manually

2. **Set in your IDE**:
   - IntelliJ IDEA: Run → Edit Configurations → Environment variables
   - Eclipse: Run → Run Configurations → Environment

3. **Export in terminal**:
   ```bash
   export JWT_SECRET=your_secret_here
   export STRIPE_SECRET_KEY=your_key_here
   # ... etc
   ```

4. **Use Spring Boot's application.properties**:
   - The `application-local.properties` file can still contain values for local dev
   - But prefer environment variables for secrets

### Production Deployment

For production, set environment variables in your deployment platform:

#### Docker:
```dockerfile
ENV JWT_SECRET=your_secret
ENV STRIPE_SECRET_KEY=your_key
```

Or use docker-compose:
```yaml
environment:
  - JWT_SECRET=${JWT_SECRET}
  - STRIPE_SECRET_KEY=${STRIPE_SECRET_KEY}
```

#### Cloud Platforms:

**AWS (Elastic Beanstalk / ECS):**
- Set in environment configuration
- Use AWS Secrets Manager for sensitive values

**Heroku:**
```bash
heroku config:set JWT_SECRET=your_secret
heroku config:set STRIPE_SECRET_KEY=your_key
```

**Google Cloud (Cloud Run):**
- Set in Cloud Run service configuration
- Use Secret Manager for sensitive values

**Azure (App Service):**
- Set in Application Settings
- Use Key Vault for sensitive values

## Security Best Practices

1. **Never commit secrets** to version control
2. **Use different secrets** for development, staging, and production
3. **Rotate secrets regularly**, especially if exposed
4. **Use secret management services** in production (AWS Secrets Manager, Azure Key Vault, etc.)
5. **Limit access** to production secrets
6. **Audit secret access** regularly

## Verifying Configuration

After setting environment variables, verify they're loaded correctly:

1. Check application logs on startup
2. Use Spring Boot Actuator `/actuator/env` endpoint (if enabled)
3. Test that sensitive endpoints require authentication

## Troubleshooting

### Variables not loading:
- Check that variable names match exactly (case-sensitive)
- Verify the variable is exported in your shell
- Check Spring Boot logs for configuration errors

### Default values being used:
- Ensure environment variables are set before Spring Boot starts
- Check that `application-local.properties` isn't overriding values

### Production issues:
- Verify environment variables are set in deployment platform
- Check deployment logs for configuration errors
- Ensure secrets are accessible to the application

## Migration from Hardcoded Values

If you're migrating from hardcoded values in `application.properties`:

1. Identify all secrets and sensitive values
2. Add them to `.env.example` with placeholders
3. Update `application.properties` to use `${ENV_VAR:default}` syntax
4. Set actual values in `.env` (local) or deployment platform (production)
5. Remove hardcoded values from version control

---

*Last Updated: 2025-01-14*

