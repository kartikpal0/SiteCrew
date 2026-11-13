// Krishna Enterprises Construction ERP & Crew Tracker Controller
const App = {
  state: {
    user: null,
    role: 'admin', // 'admin' or 'worker'
    activeTab: 'attendance',
    sites: [],
    workers: [],
    trades: [],
    selectedSiteId: '',
    selectedTrade: '',
    currentDate: new Date().toISOString().split('T')[0],
    payrollPeriod: '7days',
    currentWorkerLedger: null,
    activeInviteCodes: []
  },

  async init() {
    // 1. Initialize IndexedDB & Sync Engine
    await KrishnaDB.init();
    await KrishnaSync.init();

    // 2. Start GPS Geolocation Engine & Telemetry
    await KrishnaGeo.getCurrentLocation();
    KrishnaGeo.startLiveWatch();
    KrishnaGeo.onLocationUpdate(() => this.updateTelemetryBanner());

    // 3. Register Service Worker for offline capability
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.register('/sw.js').catch((err) => console.log('SW registration:', err));
    }

    // 4. Check or restore user session
    const savedUser = localStorage.getItem('krishna_user');
    if (savedUser) {
      try {
        this.state.user = JSON.parse(savedUser);
        this.state.role = this.state.user.role || 'admin';
      } catch (e) {
        localStorage.removeItem('krishna_user');
      }
    }

    // 5. Load Firm Data
    await this.loadInitialData();
    this.updateUserRoleUI();
    this.setupEventListeners();
    this.updateTelemetryBanner();
  },

  async loadInitialData() {
    try {
      const [sitesRes, workersRes] = await Promise.all([
        fetch('/api/sites'),
        fetch('/api/workers')
      ]);

      if (sitesRes.ok) {
        this.state.sites = await sitesRes.json();
        // Save to offline db
        for (const s of this.state.sites) await KrishnaDB.put('sites', s);
        if (this.state.sites.length > 0 && !this.state.selectedSiteId) {
          this.state.selectedSiteId = this.state.sites[0].id;
        }
      }

      if (workersRes.ok) {
        const wData = await workersRes.json();
        this.state.workers = wData.workers || [];
        this.state.trades = wData.trades || [];
        for (const w of this.state.workers) await KrishnaDB.put('workers', w);
      }
    } catch (err) {
      console.warn("Offline fallback loading from IndexedDB:", err);
      this.state.sites = await KrishnaDB.getAll('sites');
      this.state.workers = await KrishnaDB.getAll('workers');
      if (this.state.sites.length > 0 && !this.state.selectedSiteId) {
        this.state.selectedSiteId = this.state.sites[0].id;
      }
    }

    this.populateSiteDropdowns();
    this.renderActiveView();
  },

  populateSiteDropdowns() {
    const selects = [
      document.getElementById('site-select-header'),
      document.getElementById('att-site-select'),
      document.getElementById('worker-site-select'),
      document.getElementById('payroll-site-select'),
      document.getElementById('invite-site-select'),
      document.getElementById('worker-modal-site')
    ];

    selects.forEach((sel) => {
      if (!sel) return;
      const current = sel.value;
      sel.innerHTML = this.state.sites
        .map((s) => `<option value="${s.id}" ${s.id === this.state.selectedSiteId ? 'selected' : ''}>${s.name}</option>`)
        .join('');
      if (current) sel.value = current;
    });
  },

  updateUserRoleUI() {
    const adminToggle = document.getElementById('role-btn-admin');
    const workerToggle = document.getElementById('role-btn-worker');
    const adminPortal = document.getElementById('admin-portal');
    const workerPortal = document.getElementById('worker-portal');
    const userDisplay = document.getElementById('current-user-display');

    if (this.state.role === 'admin') {
      adminToggle?.classList.add('active');
      workerToggle?.classList.remove('active');
      adminPortal?.classList.add('active');
      workerPortal?.classList.remove('active');
      if (userDisplay) userDisplay.textContent = this.state.user ? this.state.user.name : "Admin (Krishna Enterprises)";
    } else {
      adminToggle?.classList.remove('active');
      workerToggle?.classList.add('active');
      adminPortal?.classList.remove('active');
      workerPortal?.classList.add('active');
      if (userDisplay) userDisplay.textContent = this.state.user ? this.state.user.name : "Worker Portal";
    }

    this.renderActiveView();
  },

  switchRole(newRole) {
    this.state.role = newRole;
    if (this.state.user) {
      this.state.user.role = newRole;
      localStorage.setItem('krishna_user', JSON.stringify(this.state.user));
    }
    this.updateUserRoleUI();
  },

  switchTab(tabId) {
    this.state.activeTab = tabId;
    document.querySelectorAll('nav.app-nav .nav-tab').forEach((tab) => {
      tab.classList.toggle('active', tab.dataset.tab === tabId);
    });
    document.querySelectorAll('.tab-pane').forEach((pane) => {
      pane.classList.toggle('active', pane.id === `tab-${tabId}`);
    });
    this.renderActiveView();
  },

  updateTelemetryBanner() {
    const liveCoordsEl = document.getElementById('telemetry-live-coords');
    const siteCoordsEl = document.getElementById('telemetry-site-coords');
    const geofenceBadgeEl = document.getElementById('telemetry-geofence-badge');
    const activeSite = this.state.sites.find((s) => s.id === this.state.selectedSiteId);

    const pos = KrishnaGeo.currentPosition;
    if (liveCoordsEl) {
      liveCoordsEl.textContent = pos
        ? `${pos.latitude.toFixed(5)}, ${pos.longitude.toFixed(5)} (±${pos.accuracy}m)`
        : 'Detecting GPS...';
    }

    if (activeSite) {
      if (siteCoordsEl) {
        siteCoordsEl.textContent = `${activeSite.name} [${activeSite.latitude.toFixed(5)}, ${activeSite.longitude.toFixed(5)}] (R: ${activeSite.radius_meters}m)`;
      }

      if (pos && geofenceBadgeEl) {
        const check = KrishnaGeo.checkGeofence(activeSite.latitude, activeSite.longitude, activeSite.radius_meters);
        geofenceBadgeEl.className = `geofence-pill ${check.isInside ? 'inside' : 'outside'}`;
        geofenceBadgeEl.textContent = check.status;
      }
    }
  },

  renderActiveView() {
    if (this.state.role === 'admin') {
      if (this.state.activeTab === 'attendance') this.renderAttendanceTab();
      else if (this.state.activeTab === 'roster') this.renderRosterTab();
      else if (this.state.activeTab === 'parties') this.renderSitesTab();
      else if (this.state.activeTab === 'analytics') this.renderAnalyticsTab();
      else if (this.state.activeTab === 'ledger') this.renderLedgerTab();
      else if (this.state.activeTab === 'payroll') this.renderPayrollTab();
      else if (this.state.activeTab === 'sync') KrishnaSync.renderSyncCenterView();
    } else {
      this.renderWorkerDashboard();
    }
  },

  // --- 1. Authentication Handlers ---
  openAuthModal() {
    document.getElementById('auth-modal')?.classList.add('active');
  },

  closeModal(modalId) {
    document.getElementById(modalId)?.classList.remove('active');
  },

  async handleGoogleSignIn(customEmail = null, customName = null) {
    const email = customEmail || (this.state.role === 'admin' ? 'rajesh.sharma@krishnaenterprises.com' : 'ramesh.kumar@krishna.work');
    const name = customName || (this.state.role === 'admin' ? 'Rajesh Sharma' : 'Ramesh Kumar');

    try {
      const resp = await fetch('/api/auth/google', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, name, role: this.state.role })
      });
      const data = await resp.json();
      if (data.user) {
        this.state.user = data.user;
        localStorage.setItem('krishna_user', JSON.stringify(data.user));
        this.closeModal('auth-modal');
        this.updateUserRoleUI();
        alert(`Signed in with Google as ${data.user.name} (${data.user.role.toUpperCase()})`);
      }
    } catch (e) {
      alert("Sign-in error: " + e.message);
    }
  },

  async handleSendOtp() {
    const phoneInput = document.getElementById('login-phone');
    const phone = phoneInput?.value.trim();
    if (!phone) return alert("Please enter phone number");

    try {
      const resp = await fetch('/api/auth/otp/send', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ phone })
      });
      const data = await resp.json();
      document.getElementById('otp-verification-dialog').style.display = 'block';
      const debugEl = document.getElementById('otp-demo-hint');
      if (debugEl) debugEl.textContent = `Demo SMS OTP: ${data.debug_otp}`;
    } catch (e) {
      alert("Failed to send OTP: " + e.message);
    }
  },

  async handleVerifyOtp() {
    const phone = document.getElementById('login-phone')?.value.trim();
    const otp = document.getElementById('login-otp-code')?.value.trim();
    if (!otp) return alert("Enter 6-digit verification code");

    try {
      const resp = await fetch('/api/auth/otp/verify', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ phone, otp, role: this.state.role })
      });
      const data = await resp.json();
      if (data.user) {
        this.state.user = data.user;
        localStorage.setItem('krishna_user', JSON.stringify(data.user));
        this.closeModal('auth-modal');
        this.updateUserRoleUI();
      }
    } catch (e) {
      alert(e.message || "Invalid verification OTP");
    }
  },

  async handlePinLogin() {
    const phone = document.getElementById('login-phone')?.value.trim();
    const pin = document.getElementById('login-pin-input')?.value.trim();
    if (!phone || !pin) return alert("Enter phone and passcode PIN");

    try {
      const resp = await fetch('/api/auth/login-pin', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ phone, pin })
      });
      const data = await resp.json();
      if (data.user) {
        this.state.user = data.user;
        localStorage.setItem('krishna_user', JSON.stringify(data.user));
        this.closeModal('auth-modal');
        this.updateUserRoleUI();
      }
    } catch (e) {
      alert("Invalid Phone or Passcode PIN (Default Admin PIN: 1234)");
    }
  },

  signOut() {
    localStorage.removeItem('krishna_user');
    this.state.user = null;
    alert("Signed out successfully.");
    this.updateUserRoleUI();
  },

  // --- 2. Construction Party & GPS Geofencing ---
  async renderSitesTab() {
    const grid = document.getElementById('sites-cards-grid');
    if (!grid) return;

    grid.innerHTML = this.state.sites
      .map(
        (s) => `
      <div class="card">
        <div class="card-title">${s.name}</div>
        <div class="card-subtitle">${s.client_name} • ${s.address}</div>
        <div style="font-size: 0.85rem; margin-bottom: 12px;">
          <div><strong>GPS Center:</strong> ${s.latitude.toFixed(5)}, ${s.longitude.toFixed(5)}</div>
          <div><strong>Geofence Radius:</strong> ${s.radius_meters} meters</div>
          <div><strong>Base Daily Wage:</strong> ₹${s.default_daily_wage}/day</div>
        </div>
        <div style="display: flex; gap: 8px;">
          <button class="btn btn-outline btn-sm" onclick="App.openEditSiteModal('${s.id}')">Edit Geofence</button>
          <button class="btn btn-outline btn-sm" onclick="App.lockCurrentGpsForSite('${s.id}')">Lock Device GPS</button>
          <button class="btn btn-danger btn-sm" onclick="App.deleteSite('${s.id}')">Delete</button>
        </div>
      </div>
    `
      )
      .join('');
  },

  openAddSiteModal() {
    document.getElementById('site-modal-id').value = '';
    document.getElementById('site-modal-name').value = '';
    document.getElementById('site-modal-client').value = '';
    document.getElementById('site-modal-address').value = '';
    document.getElementById('site-modal-wage').value = '750';
    document.getElementById('site-modal-radius').value = '150';

    if (KrishnaGeo.currentPosition) {
      document.getElementById('site-modal-lat').value = KrishnaGeo.currentPosition.latitude.toFixed(6);
      document.getElementById('site-modal-lng').value = KrishnaGeo.currentPosition.longitude.toFixed(6);
    } else {
      document.getElementById('site-modal-lat').value = '28.628000';
      document.getElementById('site-modal-lng').value = '77.364900';
    }

    document.getElementById('site-modal').classList.add('active');
  },

  openEditSiteModal(siteId) {
    const site = this.state.sites.find((s) => s.id === siteId);
    if (!site) return;
    document.getElementById('site-modal-id').value = site.id;
    document.getElementById('site-modal-name').value = site.name;
    document.getElementById('site-modal-client').value = site.client_name;
    document.getElementById('site-modal-address').value = site.address;
    document.getElementById('site-modal-wage').value = site.default_daily_wage;
    document.getElementById('site-modal-lat').value = site.latitude;
    document.getElementById('site-modal-lng').value = site.longitude;
    document.getElementById('site-modal-radius').value = site.radius_meters;
    document.getElementById('site-modal').classList.add('active');
  },

  useCurrentGpsInModal() {
    if (KrishnaGeo.currentPosition) {
      document.getElementById('site-modal-lat').value = KrishnaGeo.currentPosition.latitude.toFixed(6);
      document.getElementById('site-modal-lng').value = KrishnaGeo.currentPosition.longitude.toFixed(6);
    } else {
      alert("GPS coordinates not available yet. Ensure location is enabled.");
    }
  },

  async lockCurrentGpsForSite(siteId) {
    const pos = KrishnaGeo.currentPosition;
    if (!pos) return alert("Current GPS not detected. Please enable device location.");
    const site = this.state.sites.find((s) => s.id === siteId);
    if (!site) return;

    if (confirm(`Lock ${site.name} to device GPS coordinates: ${pos.latitude.toFixed(6)}, ${pos.longitude.toFixed(6)}?`)) {
      site.latitude = pos.latitude;
      site.longitude = pos.longitude;
      await fetch(`/api/sites/${siteId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(site)
      });
      await this.loadInitialData();
      this.updateTelemetryBanner();
    }
  },

  async handleSaveSite() {
    const siteId = document.getElementById('site-modal-id').value;
    const siteData = {
      name: document.getElementById('site-modal-name').value.trim(),
      client_name: document.getElementById('site-modal-client').value.trim(),
      address: document.getElementById('site-modal-address').value.trim(),
      default_daily_wage: parseFloat(document.getElementById('site-modal-wage').value) || 750,
      latitude: parseFloat(document.getElementById('site-modal-lat').value) || 28.628,
      longitude: parseFloat(document.getElementById('site-modal-lng').value) || 77.3649,
      radius_meters: parseFloat(document.getElementById('site-modal-radius').value) || 150
    };

    if (!siteData.name) return alert("Site name required");

    try {
      if (siteId) {
        await fetch(`/api/sites/${siteId}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(siteData)
        });
      } else {
        await fetch('/api/sites', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(siteData)
        });
      }
      this.closeModal('site-modal');
      await this.loadInitialData();
    } catch (e) {
      alert("Error saving site: " + e.message);
    }
  },

  async deleteSite(siteId) {
    if (!confirm("Are you sure you want to delete this construction site?")) return;
    try {
      await fetch(`/api/sites/${siteId}`, { method: 'DELETE' });
      await this.loadInitialData();
    } catch (e) {
      alert("Delete failed: " + e.message);
    }
  },

  // --- 3. Attendance Tracking ---
  async renderAttendanceTab() {
    const container = document.getElementById('attendance-cards-list');
    if (!container) return;

    // Filter workers by current site
    const siteWorkers = this.state.workers.filter(
      (w) => !this.state.selectedSiteId || w.site_id === this.state.selectedSiteId
    );

    // Fetch existing attendance for this date
    let attRecords = {};
    try {
      const resp = await fetch(`/api/attendance?date=${this.state.currentDate}&site_id=${this.state.selectedSiteId}`);
      if (resp.ok) {
        const list = await resp.json();
        list.forEach((a) => (attRecords[a.worker_id] = a));
      }
    } catch (e) {
      console.warn("Could not fetch remote attendance:", e);
    }

    if (siteWorkers.length === 0) {
      container.innerHTML = `<div class="card"><p class="text-muted">No crew assigned to this party. Add workers or assign them in Roster.</p></div>`;
      return;
    }

    container.innerHTML = siteWorkers
      .map((w) => {
        const att = attRecords[w.id] || { status: 'Full Day', overtime_hours: 0, notes: '' };
        const statusClass =
          att.status === 'Full Day'
            ? 'status-full'
            : att.status === 'Half Day'
            ? 'status-half'
            : att.status === 'Overtime'
            ? 'status-ot'
            : att.status === 'Absent'
            ? 'status-absent'
            : 'status-leave';

        return `
        <div class="card attendance-card ${statusClass}" id="att-card-${w.id}">
          <div style="display: flex; justify-content: space-between; align-items: center;">
            <div>
              <div class="card-title">${w.name}</div>
              <div class="card-subtitle">${w.trade} • ₹${w.daily_wage}/day (₹${w.hourly_rate}/hr)</div>
            </div>
            <div style="text-align: right;">
              <span class="badge ${att.verified_gps ? 'badge-success' : 'badge-outline'}" style="font-size: 0.7rem;">
                ${att.verified_gps ? '✓ GPS Verified' : 'Manual Entry'}
              </span>
            </div>
          </div>

          <div class="attendance-status-group">
            <button class="btn-status-pill full ${att.status === 'Full Day' ? 'active' : ''}" onclick="App.setAttendanceStatus('${w.id}', 'Full Day')">Full Day</button>
            <button class="btn-status-pill half ${att.status === 'Half Day' ? 'active' : ''}" onclick="App.setAttendanceStatus('${w.id}', 'Half Day')">Half Day</button>
            <button class="btn-status-pill ot ${att.status === 'Overtime' ? 'active' : ''}" onclick="App.setAttendanceStatus('${w.id}', 'Overtime')">Overtime</button>
            <button class="btn-status-pill absent ${att.status === 'Absent' ? 'active' : ''}" onclick="App.setAttendanceStatus('${w.id}', 'Absent')">Absent</button>
            <button class="btn-status-pill leave ${att.status === 'On Leave' ? 'active' : ''}" onclick="App.setAttendanceStatus('${w.id}', 'On Leave')">Leave</button>
          </div>

          <div class="ot-inputs-row" id="ot-row-${w.id}" style="display: ${att.status === 'Overtime' ? 'flex' : 'none'};">
            <label style="font-size: 0.75rem; font-weight: 600;">OT Hours:</label>
            <input type="number" step="0.5" min="0" max="12" class="form-input" style="width: 80px; padding: 4px 8px;" id="ot-hrs-${w.id}" value="${att.overtime_hours || 2}" />
            <input type="text" placeholder="Shift note (e.g. night slab)" class="form-input" style="flex: 1; padding: 4px 8px;" id="ot-notes-${w.id}" value="${att.notes || ''}" />
          </div>
        </div>
      `;
      })
      .join('');
  },

  setAttendanceStatus(workerId, status) {
    const card = document.getElementById(`att-card-${workerId}`);
    if (!card) return;

    card.querySelectorAll('.btn-status-pill').forEach((btn) => btn.classList.remove('active'));
    const pill = card.querySelector(`.btn-status-pill.${status.toLowerCase().replace(' ', '')}`);
    if (pill) pill.classList.add('active');

    const otRow = document.getElementById(`ot-row-${workerId}`);
    if (otRow) otRow.style.display = status === 'Overtime' ? 'flex' : 'none';

    card.className = `card attendance-card status-${status === 'Full Day' ? 'full' : status === 'Half Day' ? 'half' : status === 'Overtime' ? 'ot' : status === 'Absent' ? 'absent' : 'leave'}`;
  },

  async handleSaveAttendanceBatch() {
    const siteWorkers = this.state.workers.filter(
      (w) => !this.state.selectedSiteId || w.site_id === this.state.selectedSiteId
    );

    const records = [];
    siteWorkers.forEach((w) => {
      const card = document.getElementById(`att-card-${w.id}`);
      if (!card) return;
      let status = 'Full Day';
      if (card.querySelector('.btn-status-pill.full.active')) status = 'Full Day';
      else if (card.querySelector('.btn-status-pill.half.active')) status = 'Half Day';
      else if (card.querySelector('.btn-status-pill.ot.active')) status = 'Overtime';
      else if (card.querySelector('.btn-status-pill.absent.active')) status = 'Absent';
      else if (card.querySelector('.btn-status-pill.leave.active')) status = 'On Leave';

      const otHours = status === 'Overtime' ? parseFloat(document.getElementById(`ot-hrs-${w.id}`)?.value) || 0 : 0;
      const notes = document.getElementById(`ot-notes-${w.id}`)?.value || '';

      records.push({ worker_id: w.id, status, overtime_hours: otHours, notes });
    });

    const payload = {
      site_id: this.state.selectedSiteId,
      date: this.state.currentDate,
      records
    };

    try {
      const resp = await fetch('/api/attendance/batch', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      if (resp.ok) {
        alert(`Attendance for ${records.length} crew members saved for ${this.state.currentDate}`);
      } else {
        throw new Error("Server error");
      }
    } catch (e) {
      // Offline fallback: save to sync queue
      await KrishnaSync.enqueue('attendance', 'batch', payload);
      alert(`Saved locally (Offline Mode). Will sync when connection is live.`);
    }
  },

  markAllFullDay() {
    this.state.workers.forEach((w) => {
      this.setAttendanceStatus(w.id, 'Full Day');
    });
  },

  // --- 4. Crew Roster & Manual Role Configuration ---
  renderRosterTab() {
    const chipsBar = document.getElementById('trade-chips-container');
    const grid = document.getElementById('roster-cards-grid');

    // Dynamic Role Filter Chips
    if (chipsBar) {
      chipsBar.innerHTML = `
        <span class="trade-chip ${!this.state.selectedTrade ? 'active' : ''}" onclick="App.filterTrade('')">All Trades (${this.state.workers.length})</span>
        ${this.state.trades
          .map(
            (t) => `
          <span class="trade-chip ${this.state.selectedTrade === t ? 'active' : ''}" onclick="App.filterTrade('${t}')">${t}</span>
        `
          )
          .join('')}
      `;
    }

    if (grid) {
      const filtered = this.state.workers.filter((w) => {
        const matchesTrade = !this.state.selectedTrade || w.trade === this.state.selectedTrade;
        const matchesSite = !this.state.selectedSiteId || w.site_id === this.state.selectedSiteId;
        return matchesTrade && matchesSite;
      });

      grid.innerHTML = filtered
        .map(
          (w) => `
        <div class="card">
          <div style="display: flex; justify-content: space-between; align-items: flex-start;">
            <div>
              <div class="card-title">${w.name}</div>
              <div class="card-subtitle"><span class="trade-chip active" style="font-size: 0.7rem; padding: 2px 8px;">${w.trade}</span></div>
            </div>
            <span class="badge ${w.status === 'active' ? 'badge-success' : 'badge-danger'}">${w.status}</span>
          </div>

          <div style="font-size: 0.825rem; margin: 10px 0; color: #475569;">
            <div><strong>Daily Wage:</strong> ₹${w.daily_wage} (₹${w.hourly_rate}/hr)</div>
            <div><strong>Phone:</strong> ${w.phone}</div>
            <div><strong>UPI ID:</strong> ${w.upi_id || 'Not set'}</div>
            <div><strong>Bank:</strong> ${w.bank_account ? `${w.bank_account} (${w.ifsc})` : 'Not linked'}</div>
            <div><strong>Emergency:</strong> ${w.emergency_contact || 'None'}</div>
          </div>

          <div style="display: flex; gap: 6px; flex-wrap: wrap;">
            <button class="btn btn-outline btn-sm" onclick="App.openWorkerLedger('${w.id}')">Ledger</button>
            <button class="btn btn-outline btn-sm" onclick="App.openWageSlip('${w.id}')">Wage Slip</button>
            <button class="btn btn-secondary btn-sm" onclick="App.openEditWorkerModal('${w.id}')">Edit</button>
            <button class="btn btn-danger btn-sm" onclick="App.deleteWorker('${w.id}')">Remove</button>
          </div>
        </div>
      `
        )
        .join('');
    }
  },

  filterTrade(trade) {
    this.state.selectedTrade = trade;
    this.renderRosterTab();
  },

  openAddWorkerModal() {
    document.getElementById('worker-modal-id').value = '';
    document.getElementById('worker-modal-name').value = '';
    document.getElementById('worker-modal-phone').value = '';
    document.getElementById('worker-modal-wage').value = '750';
    document.getElementById('worker-modal-trade').value = '';
    document.getElementById('worker-modal-upi').value = '';
    document.getElementById('worker-modal-bank').value = '';
    document.getElementById('worker-modal-ifsc').value = '';
    document.getElementById('worker-modal-emergency').value = '';
    document.getElementById('worker-modal').classList.add('active');
  },

  openEditWorkerModal(workerId) {
    const w = this.state.workers.find((x) => x.id === workerId);
    if (!w) return;
    document.getElementById('worker-modal-id').value = w.id;
    document.getElementById('worker-modal-name').value = w.name;
    document.getElementById('worker-modal-phone').value = w.phone;
    document.getElementById('worker-modal-wage').value = w.daily_wage;
    document.getElementById('worker-modal-trade').value = w.trade;
    document.getElementById('worker-modal-upi').value = w.upi_id || '';
    document.getElementById('worker-modal-bank').value = w.bank_account || '';
    document.getElementById('worker-modal-ifsc').value = w.ifsc || '';
    document.getElementById('worker-modal-emergency').value = w.emergency_contact || '';
    document.getElementById('worker-modal-site').value = w.site_id || '';
    document.getElementById('worker-modal').classList.add('active');
  },

  async handleSaveWorker() {
    const workerId = document.getElementById('worker-modal-id').value;
    const wage = parseFloat(document.getElementById('worker-modal-wage').value) || 700;
    const workerData = {
      name: document.getElementById('worker-modal-name').value.trim(),
      phone: document.getElementById('worker-modal-phone').value.trim(),
      site_id: document.getElementById('worker-modal-site').value,
      daily_wage: wage,
      hourly_rate: Math.round((wage / 8) * 100) / 100,
      trade: document.getElementById('worker-modal-trade').value.trim() || 'General Craftsman',
      upi_id: document.getElementById('worker-modal-upi').value.trim(),
      bank_account: document.getElementById('worker-modal-bank').value.trim(),
      ifsc: document.getElementById('worker-modal-ifsc').value.trim(),
      emergency_contact: document.getElementById('worker-modal-emergency').value.trim(),
      status: 'active'
    };

    if (!workerData.name || !workerData.phone) return alert("Worker Name & Phone required");

    try {
      if (workerId) {
        await fetch(`/api/workers/${workerId}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(workerData)
        });
      } else {
        await fetch('/api/workers', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(workerData)
        });
      }
      this.closeModal('worker-modal');
      await this.loadInitialData();
    } catch (e) {
      // Offline support
      await KrishnaSync.enqueue('worker', workerId ? 'update' : 'create', workerData);
      this.closeModal('worker-modal');
      alert("Worker saved locally (Offline mode).");
    }
  },

  async deleteWorker(workerId) {
    if (!confirm("Are you sure you want to remove this worker from the roster?")) return;
    try {
      await fetch(`/api/workers/${workerId}`, { method: 'DELETE' });
      await this.loadInitialData();
    } catch (e) {
      alert("Delete failed: " + e.message);
    }
  },

  // --- Worker Join Codes & Invites ---
  async openInviteCodesModal() {
    try {
      const resp = await fetch('/api/invites');
      if (resp.ok) {
        this.state.activeInviteCodes = await resp.json();
      }
    } catch (e) {
      console.warn("Failed fetching invites:", e);
    }

    const listEl = document.getElementById('invite-codes-list');
    if (listEl) {
      listEl.innerHTML = this.state.activeInviteCodes
        .map(
          (inv) => `
        <div style="background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 12px; margin-bottom: 8px; display: flex; justify-content: space-between; align-items: center;">
          <div>
            <div style="font-size: 1.1rem; font-weight: 800; color: #d97706; letter-spacing: 1px;">${inv.code}</div>
            <div style="font-size: 0.75rem; color: #64748b;">${inv.role_trade} • ₹${inv.default_daily_wage}/day • ${inv.site_name}</div>
            <div style="font-size: 0.7rem; color: ${inv.status === 'active' ? '#10b981' : '#ef4444'}; font-weight: 600;">Status: ${inv.status.toUpperCase()}</div>
          </div>
          <div>
            ${
              inv.status === 'active'
                ? `<button class="btn btn-outline btn-sm" onclick="navigator.clipboard.writeText('${inv.code}'); alert('Passkey copied: ${inv.code}');">Copy</button>
                   <button class="btn btn-danger btn-sm" onclick="App.revokeInvite('${inv.id}')">Revoke</button>`
                : `<span class="badge badge-outline">Redeemed</span>`
            }
          </div>
        </div>
      `
        )
        .join('');
    }
    document.getElementById('invite-modal').classList.add('active');
  },

  async handleGenerateInvite() {
    const siteId = document.getElementById('invite-site-select').value;
    const trade = document.getElementById('invite-trade-input').value.trim();
    const wage = parseFloat(document.getElementById('invite-wage-input').value) || 750;

    if (!trade) return alert("Please specify the custom role/trade");

    try {
      const resp = await fetch('/api/invites', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ site_id: siteId, role_trade: trade, default_daily_wage: wage })
      });
      if (resp.ok) {
        document.getElementById('invite-trade-input').value = '';
        await this.openInviteCodesModal();
      }
    } catch (e) {
      alert("Error generating invite: " + e.message);
    }
  },

  async revokeInvite(inviteId) {
    try {
      await fetch(`/api/invites/${inviteId}`, { method: 'DELETE' });
      await this.openInviteCodesModal();
    } catch (e) {
      alert("Revoke failed: " + e.message);
    }
  },

  // --- 5. Analytics & Wage Trends Chart ---
  async renderAnalyticsTab() {
    try {
      const resp = await fetch('/api/analytics/wage-trends');
      if (resp.ok) {
        const data = await resp.json();
        KrishnaCharts.init('wageTrendsChart', data.trends || []);

        // Stats Summary
        const totalWages = data.trends.reduce((acc, t) => acc + t.gross_wage, 0);
        const avgConsistency = Math.round(
          data.trends.reduce((acc, t) => acc + t.consistency, 0) / (data.trends.length || 1)
        );
        const totalOtHrs = data.trends.reduce((acc, t) => acc + t.overtime_hours, 0);

        document.getElementById('stat-7day-wage').textContent = `₹${Math.round(totalWages).toLocaleString()}`;
        document.getElementById('stat-7day-consistency').textContent = `${avgConsistency}%`;
        document.getElementById('stat-7day-ot').textContent = `${totalOtHrs} hrs`;
      }
    } catch (e) {
      console.warn("Analytics error:", e);
    }
  },

  // --- 6. Two-Column Worker Financial Ledger ---
  renderLedgerTab() {
    const sel = document.getElementById('ledger-worker-select');
    if (!sel) return;
    const currentVal = sel.value;
    sel.innerHTML = `<option value="">-- Choose Worker --</option>` + this.state.workers
      .map((w) => `<option value="${w.id}" ${w.id === currentVal ? 'selected' : ''}>${w.name} (${w.trade})</option>`)
      .join('');
    if (currentVal) {
      sel.value = currentVal;
      this.openWorkerLedger(currentVal);
    } else if (this.state.workers.length > 0) {
      sel.value = this.state.workers[0].id;
      this.openWorkerLedger(this.state.workers[0].id);
    }
  },

  async openWorkerLedger(workerId) {
    if (!workerId) return;
    try {
      const resp = await fetch(`/api/ledger/${workerId}`);
      if (resp.ok) {
        const data = await resp.json();
        this.state.currentWorkerLedger = data;
        this.renderLedgerModalContent(data);
        const modal = document.getElementById('ledger-modal');
        if (modal) modal.classList.add('active');
      }
    } catch (e) {
      alert("Could not load worker ledger: " + e.message);
    }
  },

  renderLedgerModalContent(data) {
    const worker = data.worker;
    document.getElementById('ledger-worker-name').textContent = `${worker.name} (${worker.trade})`;
    document.getElementById('ledger-worker-phone').textContent = worker.phone;

    document.getElementById('ledger-total-paid').textContent = `₹${data.total_paid.toLocaleString()}`;
    document.getElementById('ledger-total-used').textContent = `₹${data.total_used.toLocaleString()}`;
    document.getElementById('ledger-net-balance').textContent = `₹${data.net_balance.toLocaleString()}`;

    // Status pill
    const statusEl = document.getElementById('ledger-balance-status');
    if (statusEl) {
      statusEl.textContent = data.balance_status;
      statusEl.className = `badge ${data.net_balance > 0 ? 'badge-warning' : data.net_balance === 0 ? 'badge-success' : 'badge-danger'}`;
    }

    // Column 1: Paid to Worker / Earned
    const paidList = document.getElementById('ledger-col-paid-list');
    const paidEntries = data.entries.filter((e) => e.type === 'paid');
    paidList.innerHTML =
      paidEntries.length === 0
        ? `<div class="ledger-entry-item text-muted"><p>No payout / advance records</p></div>`
        : paidEntries
            .map(
              (e) => `
          <div class="ledger-entry-item">
            <div class="ledger-entry-desc">
              <h4>${e.category}</h4>
              <p>${e.remark} • <span class="badge badge-outline">${e.payment_method}</span> ${e.transaction_ref ? `[${e.transaction_ref}]` : ''}</p>
              <p style="color: #94a3b8; font-size: 0.7rem;">${e.date}</p>
            </div>
            <div class="ledger-entry-amount paid">+₹${e.amount}</div>
          </div>
        `
            )
            .join('');

    // Column 2: Worker Paying / Used / Deductions
    const usedList = document.getElementById('ledger-col-used-list');
    const usedEntries = data.entries.filter((e) => e.type === 'used');
    usedList.innerHTML =
      usedEntries.length === 0
        ? `<div class="ledger-entry-item text-muted"><p>No expense / deduction records</p></div>`
        : usedEntries
            .map(
              (e) => `
          <div class="ledger-entry-item">
            <div class="ledger-entry-desc">
              <h4>${e.category}</h4>
              <p>${e.remark} • <span class="badge badge-outline">${e.payment_method}</span> ${e.transaction_ref ? `[${e.transaction_ref}]` : ''}</p>
              <p style="color: #94a3b8; font-size: 0.7rem;">${e.date}</p>
            </div>
            <div class="ledger-entry-amount used">-₹${e.amount}</div>
          </div>
        `
            )
            .join('');
  },

  openAddLedgerEntryModal() {
    if (!this.state.currentWorkerLedger) return;
    document.getElementById('ledger-entry-amount').value = '';
    document.getElementById('ledger-entry-remark').value = '';
    document.getElementById('ledger-entry-ref').value = '';
    document.getElementById('ledger-entry-date').value = new Date().toISOString().split('T')[0];
    document.getElementById('ledger-entry-modal').classList.add('active');
  },

  async handleSaveLedgerEntry() {
    const worker = this.state.currentWorkerLedger.worker;
    const amount = parseFloat(document.getElementById('ledger-entry-amount').value);
    const remark = document.getElementById('ledger-entry-remark').value.trim();
    if (!amount || amount <= 0) return alert("Please enter valid amount");
    if (!remark) return alert("Mandatory remark required for all accounting entries");

    const payload = {
      worker_id: worker.id,
      site_id: worker.site_id,
      type: document.getElementById('ledger-entry-type').value,
      category: document.getElementById('ledger-entry-category').value,
      amount,
      payment_method: document.getElementById('ledger-entry-method').value,
      remark,
      transaction_ref: document.getElementById('ledger-entry-ref').value.trim(),
      date: document.getElementById('ledger-entry-date').value
    };

    try {
      const resp = await fetch('/api/ledger', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      if (resp.ok) {
        this.closeModal('ledger-entry-modal');
        await this.openWorkerLedger(worker.id);
      }
    } catch (e) {
      // Offline fallback
      await KrishnaSync.enqueue('ledger', 'create', payload);
      this.closeModal('ledger-entry-modal');
      alert("Ledger entry stored locally (Offline Mode).");
    }
  },

  // --- 7. Payroll & Online Wage Payments ---
  async renderPayrollTab() {
    const period = this.state.payrollPeriod;
    const siteId = this.state.selectedSiteId;
    const tbody = document.getElementById('payroll-table-body');
    if (!tbody) return;

    try {
      const resp = await fetch(`/api/payroll/calculate?period=${period}&site_id=${siteId}`);
      if (resp.ok) {
        const data = await resp.json();
        document.getElementById('payroll-total-amount').textContent = `₹${data.total_payroll_amount.toLocaleString()}`;
        document.getElementById('payroll-worker-count').textContent = `${data.total_workers} Workers`;

        tbody.innerHTML = data.payroll
          .map(
            (p) => `
          <tr>
            <td>
              <strong>${p.name}</strong>
              <div style="font-size: 0.75rem; color: #64748b;">${p.trade} • ₹${p.daily_wage}/day</div>
            </td>
            <td>${p.full_days} Full | ${p.half_days} Half</td>
            <td>${p.ot_hours} hrs (+₹${p.ot_earnings})</td>
            <td>₹${p.gross_wage}</td>
            <td style="color: #dc2626;">-₹${p.deductions + p.advances_paid}</td>
            <td style="font-weight: 800; color: #059669; font-size: 0.95rem;">₹${p.net_payable}</td>
            <td>
              <div style="display: flex; gap: 6px;">
                ${
                  p.upi_id && p.net_payable > 0
                    ? `<a href="${p.upi_intent}" class="btn btn-upi btn-sm" target="_blank">Pay via UPI</a>`
                    : ''
                }
                <button class="btn btn-primary btn-sm" onclick="App.openDisburseModal('${p.worker_id}', ${p.net_payable})">Disburse</button>
                <button class="btn btn-outline btn-sm" onclick="App.openWageSlip('${p.worker_id}')">Slip</button>
              </div>
            </td>
          </tr>
        `
          )
          .join('');
      }
    } catch (e) {
      console.warn("Payroll calculation error:", e);
    }
  },

  setPayrollPeriod(period) {
    this.state.payrollPeriod = period;
    document.querySelectorAll('.payroll-period-btn').forEach((b) => {
      b.classList.toggle('active', b.dataset.period === period);
    });
    this.renderPayrollTab();
  },

  openDisburseModal(workerId, amount) {
    const worker = this.state.workers.find((w) => w.id === workerId);
    if (!worker) return;
    document.getElementById('disburse-worker-id').value = worker.id;
    document.getElementById('disburse-worker-name').textContent = `${worker.name} (${worker.trade})`;
    document.getElementById('disburse-amount').value = amount;
    document.getElementById('disburse-upi-id').textContent = worker.upi_id || 'Not provided';
    document.getElementById('disburse-bank-info').textContent = worker.bank_account
      ? `A/C: ${worker.bank_account} | IFSC: ${worker.ifsc}`
      : 'No bank account linked';

    // Show UPI intent button
    const upiLinkBtn = document.getElementById('disburse-upi-btn');
    if (worker.upi_id && amount > 0) {
      upiLinkBtn.href = `upi://pay?pa=${worker.upi_id}&pn=${encodeURIComponent(worker.name)}&am=${amount}&cu=INR&tn=Krishna%20Enterprises%20Wage`;
      upiLinkBtn.style.display = 'inline-flex';
    } else {
      upiLinkBtn.style.display = 'none';
    }

    document.getElementById('disburse-modal').classList.add('active');
  },

  async handleConfirmDisburse() {
    const workerId = document.getElementById('disburse-worker-id').value;
    const worker = this.state.workers.find((w) => w.id === workerId);
    const amount = parseFloat(document.getElementById('disburse-amount').value);
    const method = document.getElementById('disburse-method').value;
    const ref = document.getElementById('disburse-ref').value.trim();

    const payload = {
      worker_id: workerId,
      site_id: worker ? worker.site_id : '',
      period_start: 'Current Cycle',
      period_end: new Date().toISOString().split('T')[0],
      gross_wage: amount,
      overtime_pay: 0,
      deductions: 0,
      net_paid: amount,
      payment_method: method,
      transaction_ref: ref || `DISB-${Math.random().toString(36).substring(2, 8).toUpperCase()}`
    };

    try {
      const resp = await fetch('/api/payroll/disburse', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      if (resp.ok) {
        this.closeModal('disburse-modal');
        alert(`Disbursed ₹${amount} to ${worker.name} via ${method}. Digital receipt created!`);
        await this.renderPayrollTab();
      }
    } catch (e) {
      alert("Disbursal recording failed: " + e.message);
    }
  },

  // Printable Wage Slip
  async openWageSlip(workerId) {
    try {
      const resp = await fetch(`/api/payroll/wage-slip/${workerId}`);
      if (resp.ok) {
        const slip = await resp.json();
        document.getElementById('slip-number').textContent = slip.slip_number;
        document.getElementById('slip-date').textContent = slip.generated_at;
        document.getElementById('slip-worker-name').textContent = slip.worker.name;
        document.getElementById('slip-worker-trade').textContent = slip.worker.trade;
        document.getElementById('slip-worker-phone').textContent = slip.worker.phone;
        document.getElementById('slip-site-name').textContent = slip.worker.site_name || 'Krishna Enterprises Site';

        document.getElementById('slip-full-days').textContent = slip.attendance_summary.full_days;
        document.getElementById('slip-half-days').textContent = slip.attendance_summary.half_days;
        document.getElementById('slip-ot-hrs').textContent = slip.attendance_summary.overtime_hours;

        document.getElementById('slip-base-pay').textContent = `₹${slip.earnings.base_pay}`;
        document.getElementById('slip-ot-pay').textContent = `₹${slip.earnings.overtime_pay}`;
        document.getElementById('slip-gross-wage').textContent = `₹${slip.earnings.gross_wage}`;
        document.getElementById('slip-deductions').textContent = `-₹${slip.deductions_and_advances.total_deductions}`;
        document.getElementById('slip-net-payable').textContent = `₹${slip.net_payable}`;

        document.getElementById('wage-slip-modal').classList.add('active');
      }
    } catch (e) {
      alert("Failed loading wage slip: " + e.message);
    }
  },

  printWageSlip() {
    window.print();
  },

  // --- Worker Self-Portal Methods ---
  renderWorkerDashboard() {
    const activeWorker =
      this.state.workers.find((w) => this.state.user && this.state.user.worker_id === w.id) ||
      this.state.workers[0];

    if (!activeWorker) return;

    document.getElementById('w-portal-name').textContent = activeWorker.name;
    document.getElementById('w-portal-trade').textContent = activeWorker.trade;
    document.getElementById('w-portal-wage').textContent = `₹${activeWorker.daily_wage}/day`;
    document.getElementById('w-portal-phone').textContent = activeWorker.phone;

    const site = this.state.sites.find((s) => s.id === activeWorker.site_id) || this.state.sites[0];
    if (site) {
      document.getElementById('w-portal-site-name').textContent = site.name;
      document.getElementById('w-portal-site-addr').textContent = site.address;

      const check = KrishnaGeo.checkGeofence(site.latitude, site.longitude, site.radius_meters);
      const badge = document.getElementById('w-portal-gps-status');
      if (badge) {
        badge.className = `geofence-pill ${check.isInside ? 'inside' : 'outside'}`;
        badge.textContent = check.status;
      }
    }

    // Load worker's own ledger
    this.loadWorkerOwnLedger(activeWorker.id);
  },

  async handleWorkerSelfClockIn() {
    const activeWorker =
      this.state.workers.find((w) => this.state.user && this.state.user.worker_id === w.id) ||
      this.state.workers[0];

    const pos = KrishnaGeo.currentPosition;
    if (!pos) return alert("Acquiring GPS fix... Please ensure location services are enabled.");

    const site = this.state.sites.find((s) => s.id === activeWorker.site_id) || this.state.sites[0];

    try {
      const resp = await fetch('/api/attendance/clock-in', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          worker_id: activeWorker.id,
          site_id: site.id,
          latitude: pos.latitude,
          longitude: pos.longitude,
          notes: 'Worker self clock-in'
        })
      });

      const res = await resp.json();
      if (res.is_inside) {
        alert(`✓ Geofence Verified! Clocked in successfully at ${site.name}.\nDistance from center: ${res.distance_meters}m.`);
      } else {
        alert(`⚠️ Warning: Clock-in recorded as Outside Perimeter!\nYou are ${res.distance_meters}m away from site center (Allowed radius: ${res.allowed_radius}m).`);
      }
    } catch (e) {
      alert("Clock-in error: " + e.message);
    }
  },

  async loadWorkerOwnLedger(workerId) {
    try {
      const resp = await fetch(`/api/ledger/${workerId}`);
      if (resp.ok) {
        const data = await resp.json();
        document.getElementById('w-ledger-paid').textContent = `₹${data.total_paid.toLocaleString()}`;
        document.getElementById('w-ledger-used').textContent = `₹${data.total_used.toLocaleString()}`;
        document.getElementById('w-ledger-balance').textContent = `₹${data.net_balance.toLocaleString()}`;

        const listEl = document.getElementById('w-ledger-entries-list');
        if (listEl) {
          listEl.innerHTML = data.entries
            .map(
              (e) => `
            <div class="ledger-entry-item">
              <div class="ledger-entry-desc">
                <h4>${e.category}</h4>
                <p>${e.remark} • <span class="badge badge-outline">${e.payment_method}</span></p>
                <p style="color: #94a3b8; font-size: 0.7rem;">${e.date}</p>
              </div>
              <div class="ledger-entry-amount ${e.type === 'paid' ? 'paid' : 'used'}">
                ${e.type === 'paid' ? '+' : '-'}₹${e.amount}
              </div>
            </div>
          `
            )
            .join('');
        }
      }
    } catch (e) {
      console.warn("Could not load worker self ledger:", e);
    }
  },

  openWorkerJoinModal() {
    document.getElementById('worker-join-code').value = '';
    document.getElementById('worker-join-name').value = '';
    document.getElementById('worker-join-phone').value = '';
    document.getElementById('worker-join-modal').classList.add('active');
  },

  async handleRedeemJoinCode() {
    const code = document.getElementById('worker-join-code').value.trim();
    const name = document.getElementById('worker-join-name').value.trim();
    const phone = document.getElementById('worker-join-phone').value.trim();
    const upi = document.getElementById('worker-join-upi').value.trim();

    if (!code || !name || !phone) return alert("Passkey, Full Name, and Phone are required.");

    try {
      const resp = await fetch('/api/auth/worker-join', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ code, worker_name: name, phone, upi_id: upi })
      });
      const data = await resp.json();
      if (resp.ok) {
        alert(data.message);
        this.closeModal('worker-join-modal');
        await this.loadInitialData();
        this.switchRole('worker');
      } else {
        alert(data.detail || "Invalid join code");
      }
    } catch (e) {
      alert("Join failed: " + e.message);
    }
  },

  setupEventListeners() {
    // Nav tabs
    document.querySelectorAll('nav.app-nav .nav-tab').forEach((tab) => {
      tab.addEventListener('click', () => this.switchTab(tab.dataset.tab));
    });

    // Site filter in header
    document.getElementById('site-select-header')?.addEventListener('change', (e) => {
      this.state.selectedSiteId = e.target.value;
      this.updateTelemetryBanner();
      this.renderActiveView();
    });

    // Date selector strip
    document.querySelectorAll('.date-btn').forEach((b) => {
      b.addEventListener('click', (e) => {
        document.querySelectorAll('.date-btn').forEach((x) => x.classList.remove('active'));
        e.target.classList.add('active');
        const offset = parseInt(e.target.dataset.offset) || 0;
        const d = new Date();
        d.setDate(d.getDate() - offset);
        this.state.currentDate = d.toISOString().split('T')[0];
        document.getElementById('att-custom-date').value = this.state.currentDate;
        this.renderAttendanceTab();
      });
    });

    document.getElementById('att-custom-date')?.addEventListener('change', (e) => {
      this.state.currentDate = e.target.value;
      document.querySelectorAll('.date-btn').forEach((x) => x.classList.remove('active'));
      this.renderAttendanceTab();
    });
  }
};

window.addEventListener('DOMContentLoaded', () => App.init());
