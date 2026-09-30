'use strict';

const Ably = require('ably');

// Returns a getter that lazily constructs a shared Ably Rest client, so the
// server can still boot and serve the dashboard without a key — requests then
// fail with a clear message.
function lazyRest(apiKey) {
  let rest = null;
  return () => {
    if (!rest) {
      if (!apiKey) {
        throw new Error('ABLY_API_KEY is not set — add it to server/.env');
      }
      rest = new Ably.Rest({
        key: apiKey,
        useBinaryProtocol: false,
      });
    }
    return rest;
  };
}

module.exports = lazyRest;
