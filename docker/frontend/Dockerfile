FROM node:18-alpine AS builder

WORKDIR /app

COPY zhixue-frontend/package*.json ./

RUN npm ci --only=production

COPY zhixue-frontend .

RUN npm run build

FROM nginx:alpine

RUN apk add --no-cache tzdata

COPY --from=builder /app/dist /usr/share/nginx/html

COPY docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
