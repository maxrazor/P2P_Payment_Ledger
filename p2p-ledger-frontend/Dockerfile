# Build the React application
FROM node:22-alpine AS build

WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .

# Accept the argument from docker-compose.yml
ARG VITE_API_BASE_URL=/api/v1

# Inline the variable straight into the build command
RUN VITE_API_BASE_URL=$VITE_API_BASE_URL npm run build

# Serve the production build
FROM nginx:alpine

COPY --from=build /app/dist /usr/share/nginx/html
# Explicitly set standard read permissions to avoid 403 errors
RUN chmod -R 755 /usr/share/nginx/html

COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80