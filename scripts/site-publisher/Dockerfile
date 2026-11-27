# Site publisher (openspec website-publish). Playwright's image already carries Node and
# Chromium; the version matches the site repos' erp/package.json so verify.js behaves the
# same here and on a developer machine.
FROM mcr.microsoft.com/playwright:v1.58.2-noble

RUN apt-get update \
 && apt-get install -y --no-install-recommends git rsync openssh-client \
 && rm -rf /var/lib/apt/lists/* \
 && npm install -g playwright@1.58.2 \
 && npm cache clean --force \
 && mkdir -p /work /app \
 && chown pwuser:pwuser /work

# Site repos do not commit erp/node_modules; their verify.js finds Playwright here.
ENV NODE_PATH=/usr/lib/node_modules \
    SITE_PUBLISHER_WORK=/work \
    SITE_PUBLISHER_PORT=8910

WORKDIR /app
COPY package.json ./
COPY src ./src
USER pwuser
EXPOSE 8910
CMD ["node", "src/server.js"]
