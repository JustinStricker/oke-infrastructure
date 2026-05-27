// Configure webpack-dev-server to use a fixed port (avoid conflict with Ktor on 8080).
// COOP/COEP headers are NOT required — we use the opfs-sahpool VFS which works
// without cross-origin isolation. See https://sqlite.org/wasm/doc/tip/persistence.md#opfs-sahpool

config.devServer = config.devServer || {};
config.devServer.port = 8082;