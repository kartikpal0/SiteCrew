// Offline-First Sync Center & Queue Manager for Krishna Enterprises
const KrishnaSync = {
  isOnline: navigator.onLine,
  clientId: 'client-' + Math.random().toString(36).substring(2, 9),
  pendingQueue: [],
  syncLogs: [],
  statusBadgeEl: null,
  pendingCountEl: null,
  isSyncing: false,

  async init() {
    this.statusBadgeEl = document.getElementById('network-status-badge');
    this.pendingCountEl = document.getElementById('pending-sync-count');

    window.addEventListener('online', () => {
      this.isOnline = true;
      this.updateStatusUI();
      this.triggerAutoSync();
    });

    window.addEventListener('offline', () => {
      this.isOnline = false;
      this.updateStatusUI();
    });

    await this.refreshQueue();
    this.updateStatusUI();
  },

  async refreshQueue() {
    this.pendingQueue = await KrishnaDB.getSyncQueue();
    this.updateStatusUI();
  },

  updateStatusUI() {
    if (!this.statusBadgeEl) return;
    const count = this.pendingQueue.length;

    if (this.isOnline) {
      this.statusBadgeEl.className = 'status-badge status-online';
      this.statusBadgeEl.innerHTML = `<span class="pulse-dot green"></span> Live Server`;
    } else {
      this.statusBadgeEl.className = 'status-badge status-offline';
      this.statusBadgeEl.innerHTML = `<span class="pulse-dot orange"></span> Offline Local`;
    }

    if (this.pendingCountEl) {
      if (count > 0) {
        this.pendingCountEl.textContent = `${count} pending`;
        this.pendingCountEl.style.display = 'inline-block';
      } else {
        this.pendingCountEl.style.display = 'none';
      }
    }

    // Render in Sync Center tab if present
    this.renderSyncCenterView();
  },

  async enqueue(entity, action, payload) {
    await KrishnaDB.enqueueSync(entity, action, payload);
    await this.refreshQueue();

    // If online, auto-sync immediately
    if (this.isOnline && !this.isSyncing) {
      this.triggerAutoSync();
    }
  },

  async triggerAutoSync() {
    if (this.isSyncing || !this.isOnline) return;
    const queue = await KrishnaDB.getSyncQueue();
    if (queue.length === 0) return;

    this.isSyncing = true;
    const btn = document.getElementById('btn-manual-sync');
    if (btn) btn.disabled = true;

    try {
      const resp = await fetch('/api/sync/batch', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          client_id: this.clientId,
          changes: queue
        })
      });

      if (resp.ok) {
        await KrishnaDB.clearSyncQueue();
        this.addLocalLog(`Successfully reconciled ${queue.length} offline records with server.`);
        await this.refreshQueue();
      } else {
        this.addLocalLog(`Sync attempt failed: ${resp.statusText}`);
      }
    } catch (err) {
      this.addLocalLog(`Sync connection error: ${err.message}`);
    } finally {
      this.isSyncing = false;
      if (btn) btn.disabled = false;
      this.updateStatusUI();
    }
  },

  addLocalLog(message) {
    this.syncLogs.unshift({
      time: new Date().toLocaleTimeString(),
      message
    });
    if (this.syncLogs.length > 50) this.syncLogs.pop();
    this.renderSyncCenterView();
  },

  async renderSyncCenterView() {
    const queueList = document.getElementById('sync-queue-list');
    const logList = document.getElementById('sync-log-list');
    const netState = document.getElementById('sync-net-state');

    if (netState) {
      netState.innerHTML = this.isOnline
        ? `<span class="badge badge-success">ONLINE</span> Connected to Krishna Enterprises Core Backend`
        : `<span class="badge badge-warning">OFFLINE</span> Writing to Local IndexedDB Storage`;
    }

    if (queueList) {
      if (this.pendingQueue.length === 0) {
        queueList.innerHTML = `<div class="empty-state-card"><p>✓ Sync queue is clean. All records are up to date with server.</p></div>`;
      } else {
        queueList.innerHTML = this.pendingQueue
          .map(
            (q) => `
          <div class="sync-queue-item">
            <div class="queue-header">
              <span class="badge badge-info">${q.entity.toUpperCase()}</span>
              <span class="badge badge-outline">${q.action}</span>
              <span class="queue-time">${new Date(q.timestamp).toLocaleTimeString()}</span>
            </div>
            <pre class="queue-payload">${JSON.stringify(q.payload, null, 2)}</pre>
          </div>
        `
          )
          .join('');
      }
    }

    if (logList) {
      try {
        const resp = await fetch('/api/sync/status');
        if (resp.ok) {
          const data = await resp.json();
          const serverLogs = data.recent_logs || [];
          logList.innerHTML = serverLogs
            .map(
              (l) => `
            <div class="sync-log-row">
              <span class="log-time">${l.synced_at}</span>
              <span class="log-client">[${l.client_id}]</span>
              <span class="log-entity">${l.entity_type}:${l.action}</span>
              <span class="log-status badge badge-success">${l.status}</span>
            </div>
          `
            )
            .join('');
          return;
        }
      } catch (e) {
        // Fallback to local logs
      }

      if (this.syncLogs.length === 0) {
        logList.innerHTML = `<p class="text-muted">No recent reconciliation events recorded.</p>`;
      } else {
        logList.innerHTML = this.syncLogs
          .map(
            (l) => `
          <div class="sync-log-row">
            <span class="log-time">${l.time}</span>
            <span class="log-msg">${l.message}</span>
          </div>
        `
          )
          .join('');
      }
    }
  }
};
