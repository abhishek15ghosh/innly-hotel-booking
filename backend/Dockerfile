# Multi-stage production container for Innly Backend service
FROM node:20-alpine AS builder

WORKDIR /app

COPY package*.json ./
RUN npm ci --only=production

FROM node:20-alpine AS runner

WORKDIR /app

ENV NODE_ENV=production
ENV PORT=8080

COPY package*.json ./
COPY --from=builder /app/node_modules ./node_modules
COPY src ./src
COPY sql ./sql

# Run container as unprivileged non-root user
USER node

EXPOSE 8080

CMD ["node", "src/server.js"]
