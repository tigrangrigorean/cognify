FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
ENV BACKEND_URL=http://backend:8080
RUN npm run build
FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production
ENV BACKEND_URL=http://backend:8080
COPY --from=build --chown=node:node /app ./
USER node
EXPOSE 3000
CMD ["npm", "start", "--", "--hostname", "0.0.0.0"]
