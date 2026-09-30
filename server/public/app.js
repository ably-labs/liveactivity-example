'use strict';

// Logging
function log(message, type = 'info') {
  const container = document.getElementById('log');
  const el = document.createElement('div');
  el.className = `log-entry ${type}`;
  const ts = new Date().toLocaleTimeString('en-GB', { hour12: false });
  el.innerHTML = `<span class="ts">${ts}</span><span class="msg">${escapeHtml(message)}</span>`;
  container.prepend(el);
}

function escapeHtml(str) {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

// API helper
async function apiPost(endpoint, body, btn) {
  const origText = btn.textContent;
  btn.disabled = true;
  btn.textContent = 'Sending...';

  try {
    const res = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    const data = await res.json();
    if (data.success) {
      log(`${endpoint} → 200 OK`, 'ok');
    } else {
      log(`${endpoint} → Error: ${data.error}`, 'err');
    }
    return data;
  } catch (err) {
    log(`${endpoint} → Network error: ${err.message}`, 'err');
    return { success: false, error: err.message };
  } finally {
    btn.disabled = false;
    btn.textContent = origText;
  }
}

// Create broadcast channel
document.getElementById('btn-create-broadcast').addEventListener('click', async () => {
  const btn = document.getElementById('btn-create-broadcast');
  const data = await apiPost('/api/broadcasts', {}, btn);
  if (data && data.success && data.id) {
    document.getElementById('bc-id').value = data.id;
    document.getElementById('bc-apns-channel').value = data.apnsChannelId || '';
    log(`Broadcast created → id=${data.id}, apnsChannelId=${data.apnsChannelId}`, 'ok');
  }
});

// Start Live Activity (push-to-start over Ably channels)
document.getElementById('btn-start').addEventListener('click', () => {
  const btn = document.getElementById('btn-start');
  const apnsBroadcast = document.getElementById('bc-id').value.trim();
  if (!apnsBroadcast) { log('Broadcast ID is required (create one above)', 'err'); return; }
  const channels = document.getElementById('start-channels').value
    .split(',')
    .map((c) => c.trim())
    .filter(Boolean);
  const deviceId = document.getElementById('start-device-id').value.trim();
  if (channels.length === 0 && !deviceId) {
    log('Provide at least one Ably channel or a device ID', 'err');
    return;
  }
  apiPost('/api/live-activity/start', {
    apnsBroadcast,
    apnsChannelId: document.getElementById('bc-apns-channel').value.trim(),
    channels,
    ...(deviceId ? { deviceId } : {}),
    homeTeam: document.getElementById('start-home').value.trim(),
    awayTeam: document.getElementById('start-away').value.trim(),
  }, btn);
});

// Update activity (broadcast)
document.getElementById('btn-update').addEventListener('click', () => {
  const btn = document.getElementById('btn-update');
  const apnsBroadcast = document.getElementById('bc-id').value.trim();
  if (!apnsBroadcast) { log('Broadcast ID is required (create one above)', 'err'); return; }
  apiPost('/api/live-activity/update', {
    apnsBroadcast,
    homeScore: parseInt(document.getElementById('bc-home-score').value, 10),
    awayScore: parseInt(document.getElementById('bc-away-score').value, 10),
    gameStatus: document.getElementById('bc-status').value,
    period: document.getElementById('bc-period').value,
    clock: document.getElementById('bc-clock').value.trim(),
    lastPlay: document.getElementById('bc-play').value.trim(),
  }, btn);
});

// End activity (broadcast)
document.getElementById('btn-end').addEventListener('click', () => {
  const btn = document.getElementById('btn-end');
  const apnsBroadcast = document.getElementById('bc-id').value.trim();
  if (!apnsBroadcast) { log('Broadcast ID is required (create one above)', 'err'); return; }
  apiPost('/api/live-activity/end', {
    apnsBroadcast,
    homeScore: parseInt(document.getElementById('bc-home-score').value, 10),
    awayScore: parseInt(document.getElementById('bc-away-score').value, 10),
  }, btn);
});

// --- Android Live Updates ---------------------------------------------------

// Target devices + teams, shared by every Android event. Returns null (after
// logging why) when the target is incomplete.
function androidTarget() {
  const channels = document.getElementById('and-channels').value
    .split(',')
    .map((c) => c.trim())
    .filter(Boolean);
  const deviceId = document.getElementById('and-device-id').value.trim();
  if (channels.length === 0 && !deviceId) {
    log('Provide at least one Ably channel or a device ID', 'err');
    return null;
  }
  return {
    channels,
    ...(deviceId ? { deviceId } : {}),
    homeTeam: document.getElementById('and-home').value.trim(),
    awayTeam: document.getElementById('and-away').value.trim(),
  };
}

function androidScore() {
  return {
    homeScore: parseInt(document.getElementById('and-home-score').value, 10),
    awayScore: parseInt(document.getElementById('and-away-score').value, 10),
  };
}

document.getElementById('btn-and-start').addEventListener('click', (e) => {
  const target = androidTarget();
  if (target) apiPost('/api/android/start', target, e.currentTarget);
});

document.getElementById('btn-and-update').addEventListener('click', (e) => {
  const target = androidTarget();
  if (!target) return;
  apiPost('/api/android/update', {
    ...target,
    ...androidScore(),
    gameStatus: document.getElementById('and-status').value,
    period: document.getElementById('and-period').value,
    clock: document.getElementById('and-clock').value.trim(),
    lastPlay: document.getElementById('and-play').value.trim(),
  }, e.currentTarget);
});

document.getElementById('btn-and-end').addEventListener('click', (e) => {
  const target = androidTarget();
  if (target) apiPost('/api/android/end', { ...target, ...androidScore() }, e.currentTarget);
});

// --- Tabs -------------------------------------------------------------------

// The selected tab is kept in the URL hash so a reload stays on it.
function selectTab(name) {
  for (const tab of document.querySelectorAll('.tab')) {
    const selected = tab.dataset.tab === name;
    tab.setAttribute('aria-selected', String(selected));
    document.getElementById(`panel-${tab.dataset.tab}`).hidden = !selected;
  }
}

for (const tab of document.querySelectorAll('.tab')) {
  tab.addEventListener('click', () => {
    history.replaceState(null, '', `#${tab.dataset.tab}`);
    selectTab(tab.dataset.tab);
  });
}
selectTab(location.hash === '#android' ? 'android' : 'ios');

// Clear log
document.getElementById('btn-clear').addEventListener('click', () => {
  document.getElementById('log').innerHTML = '';
});
