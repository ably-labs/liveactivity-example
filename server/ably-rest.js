'use strict';

const Ably = require('ably');

// Returns a getter that lazily constructs a shared Ably Rest client, so the
// server can still boot and serve the dashboard without a key — requests then
// fail with a clear message. `endpoint` selects a non-production Ably
// environment (e.g. `sandbox`); omit it for production.
function lazyRest(apiKey, endpoint) {
  let rest = null;
  return () => {
    if (!rest) {
      if (!apiKey) {
        throw new Error('ABLY_API_KEY is not set — add it to server/.env');
      }
      rest = new Ably.Rest({
        key: apiKey,
        ...(endpoint ? { endpoint } : {}),
        useBinaryProtocol: false,
      });
    }
    return rest;
  };
}

module.exports = lazyRest;
