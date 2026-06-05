#!/usr/bin/env node

import { createReadStream, existsSync, statSync } from 'node:fs';
import { extname, join, normalize, resolve, sep } from 'node:path';
import { createServer } from 'node:http';

const [rootArg, portArg = '8099', host = '127.0.0.1'] = process.argv.slice(2);

if (!rootArg) {
  console.error('Usage: serve-spa.mjs <root> [port] [host]');
  process.exit(1);
}

const root = resolve(rootArg);
const port = Number(portArg);

if (!Number.isFinite(port)) {
  console.error(`Invalid port: ${portArg}`);
  process.exit(1);
}

const indexFile = join(root, 'index.html');

if (!existsSync(indexFile)) {
  console.error(`Missing index.html in ${root}`);
  process.exit(1);
}

const mimeTypes = new Map([
  ['.css', 'text/css; charset=utf-8'],
  ['.html', 'text/html; charset=utf-8'],
  ['.js', 'text/javascript; charset=utf-8'],
  ['.json', 'application/json; charset=utf-8'],
  ['.mjs', 'text/javascript; charset=utf-8'],
  ['.png', 'image/png'],
  ['.svg', 'image/svg+xml'],
  ['.wasm', 'application/wasm'],
  ['.webp', 'image/webp'],
]);

function safeFileFor(urlPath) {
  const decodedPath = decodeURIComponent(urlPath.split('?')[0] ?? '/');
  const normalizedPath = normalize(decodedPath).replace(/^(\.\.(?:\/|\\|$))+/, '');
  const candidate = resolve(root, `.${sep}${normalizedPath}`);
  if (candidate !== root && !candidate.startsWith(`${root}${sep}`)) return null;
  if (!existsSync(candidate)) return null;
  const stats = statSync(candidate);
  if (stats.isDirectory()) {
    const directoryIndex = join(candidate, 'index.html');
    return existsSync(directoryIndex) ? directoryIndex : null;
  }
  return stats.isFile() ? candidate : null;
}

function serveFile(response, file, statusCode = 200) {
  response.writeHead(statusCode, {
    'Content-Type': mimeTypes.get(extname(file)) ?? 'application/octet-stream',
  });
  createReadStream(file).pipe(response);
}

createServer((request, response) => {
  try {
    const requestUrl = new URL(request.url ?? '/', `http://${host}:${port}`);
    const file = safeFileFor(requestUrl.pathname);
    if (file) {
      serveFile(response, file);
    } else if (extname(requestUrl.pathname) === '') {
      serveFile(response, indexFile);
    } else {
      response.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
      response.end('Not found');
    }
  } catch (error) {
    response.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' });
    response.end(error instanceof Error ? error.message : String(error));
  }
}).listen(port, host, () => {
  console.log(`Serving ${root} at http://${host}:${port}`);
});
