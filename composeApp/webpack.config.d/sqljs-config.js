const CopyWebpackPlugin = require("copy-webpack-plugin");

config.resolve = config.resolve || {};
config.resolve.fallback = {
    ...(config.resolve.fallback || {}),
    crypto: false,
    fs: false,
    path: false,
};

config.plugins.push(
    new CopyWebpackPlugin({
        patterns: [
            {
                from: require.resolve("sql.js/dist/sql-wasm.js"),
                to: "kotlin/sql-wasm.js",
            },
            {
                from: require.resolve("sql.js/dist/sql-wasm.wasm"),
                to: "sql-wasm.wasm",
            },
        ],
    }),
);
