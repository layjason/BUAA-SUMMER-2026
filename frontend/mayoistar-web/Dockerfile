FROM node:24-alpine AS build

ARG VITE_USE_MOCK=false
ARG VITE_API_BASE_URL=
ENV VITE_USE_MOCK=${VITE_USE_MOCK}
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

WORKDIR /app

RUN corepack enable && corepack prepare pnpm@10 --activate

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile

COPY . ./
RUN pnpm build

FROM nginx:alpine

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://127.0.0.1:80/ || exit 1
