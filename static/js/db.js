// IndexedDB Offline-First Local Store for Krishna Enterprises
const KrishnaDB = {
  db: null,
  dbName: 'KrishnaEnterprisesDB',
  version: 1,

  async init() {
    return new Promise((resolve, reject) => {
      const request = indexedDB.open(this.dbName, this.version);

      request.onupgradeneeded = (event) => {
        const db = event.target.result;
        if (!db.objectStoreNames.contains('sites')) {
          db.createObjectStore('sites', { keyPath: 'id' });
        }
        if (!db.objectStoreNames.contains('workers')) {
          db.createObjectStore('workers', { keyPath: 'id' });
        }
        if (!db.objectStoreNames.contains('attendance')) {
          db.createObjectStore('attendance', { keyPath: 'id' });
        }
        if (!db.objectStoreNames.contains('ledger')) {
          db.createObjectStore('ledger', { keyPath: 'id' });
        }
        if (!db.objectStoreNames.contains('invites')) {
          db.createObjectStore('invites', { keyPath: 'id' });
        }
        if (!db.objectStoreNames.contains('sync_queue')) {
          db.createObjectStore('sync_queue', { keyPath: 'queue_id', autoIncrement: true });
        }
      };

      request.onsuccess = (event) => {
        this.db = event.target.result;
        resolve(this.db);
      };

      request.onerror = (event) => {
        console.error('IndexedDB error:', event.target.error);
        reject(event.target.error);
      };
    });
  },

  async put(storeName, item) {
    if (!this.db) await this.init();
    return new Promise((resolve, reject) => {
      const tx = this.db.transaction([storeName], 'readwrite');
      const store = tx.objectStore(storeName);
      const req = store.put(item);
      req.onsuccess = () => resolve(item);
      req.onerror = () => reject(req.error);
    });
  },

  async getAll(storeName) {
    if (!this.db) await this.init();
    return new Promise((resolve, reject) => {
      const tx = this.db.transaction([storeName], 'readonly');
      const store = tx.objectStore(storeName);
      const req = store.getAll();
      req.onsuccess = () => resolve(req.result || []);
      req.onerror = () => reject(req.error);
    });
  },

  async delete(storeName, id) {
    if (!this.db) await this.init();
    return new Promise((resolve, reject) => {
      const tx = this.db.transaction([storeName], 'readwrite');
      const store = tx.objectStore(storeName);
      const req = store.delete(id);
      req.onsuccess = () => resolve(true);
      req.onerror = () => reject(req.error);
    });
  },

  async enqueueSync(entity, action, payload) {
    if (!this.db) await this.init();
    const item = {
      entity,
      action,
      payload,
      timestamp: new Date().toISOString()
    };
    return new Promise((resolve, reject) => {
      const tx = this.db.transaction(['sync_queue'], 'readwrite');
      const store = tx.objectStore('sync_queue');
      const req = store.add(item);
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
  },

  async getSyncQueue() {
    return this.getAll('sync_queue');
  },

  async clearSyncQueue() {
    if (!this.db) await this.init();
    return new Promise((resolve, reject) => {
      const tx = this.db.transaction(['sync_queue'], 'readwrite');
      const store = tx.objectStore('sync_queue');
      const req = store.clear();
      req.onsuccess = () => resolve(true);
      req.onerror = () => reject(req.error);
    });
  }
};
