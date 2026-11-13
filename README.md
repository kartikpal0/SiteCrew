# Krishna Enterprises — Construction ERP, GPS Geofencing & Payroll Platform

A construction contractor management web application designed for **Krishna Enterprises** to manage multi-party construction sites, geofenced crew attendance, manual trade configurations, two-column financial ledgers, automated payroll disbursals, and offline-first on-device synchronization.

---

## 🏗️ Core System Capabilities

### 1. Authentication & Dual-Role Access
* **One-Tap Google Authentication**: Seamless "Continue with Google" sign-in for Contractor Admins (`rajesh.sharma@krishnaenterprises.com`) and Workers (`ramesh.kumar@krishna.work`) or custom Google email sign-in.
* **Mobile Number & SMS/OTP Login**: 6-digit verification code dialog, Admin passcode PIN sign-in (PIN: `1234`), and phone authentication for returning workers.
* **Organization Registration**: Manage firm profile (Firm: *Krishna Enterprises*, Manager: *Rajesh Sharma*, Phone: `+91 98765 00000`, Passcode PIN: `1234`).
* **Private Worker Join Codes**: Admins generate unique, single-use passkeys (e.g. `SC-8421`, `SC-9134`) linked to a site and agreed daily wage. Workers redeem codes to join a construction party.
* **Portal Switching & Sign-Out**: Instant toggle between Admin Console and Worker Self-Service Portal, with full sign-out in header.

### 2. Construction Party & GPS Geofencing
* **Multi-Site Management**: Full CRUD for construction parties (e.g. *Krishna Heights Phase 1*, *Ganga View Residency*, *Krishna Commercial Plaza*) with client name, site address, and default daily wage.
* **GPS Perimeter & Coordinate Locking**: Manual latitude/longitude coordinate entry, configurable geofence radius (in meters), and one-tap **"Lock Current GPS"** coordinate locking via device Geolocation API.
* **Live GPS Telemetry Banner**: Displays real-time device coordinates alongside site center coordinates, perimeter status pill, and quick-edit access.
* **Distance & Verification Audit**: Uses the Haversine distance formula to verify whether clock-ins occur inside or outside the site boundary.

### 3. Attendance Tracking
* **Admin Attendance Console**: Mark crew daily attendance as Full Day, Half Day, Overtime (with custom overtime hours input and shift notes), Absent, or On Leave.
* **Batch Roll-Call**: One-tap "Mark All Full Day" and batch submission for the active party.
* **Date Selector Strip**: Interactive date strip (Today, Yesterday, 2 Days Ago, Date Picker) to browse and record historical muster rolls.
* **Worker Self Check-In**: Workers clock in directly from their mobile portal with live GPS verification against their assigned site's geofence.

### 4. Crew Roster & Manual Role Configuration
* **Crew Roster CRUD**: Worker profile management (Name, Phone, Daily Wage, Auto-calculated Hourly Rate, UPI ID, Bank Account, IFSC, Emergency Contact, Status).
* **Manual Trade / Job Entry**: Admins type custom role titles (e.g. *Master Mason*, *Steel Barbender*, *Shuttering Carpenter*, *Site Electrician*, *Welder & Fabricator*).
* **Dynamic Role Filter Chips**: Interactive filter chips automatically populated from the active crew's unique trade titles.
* **Invite Passkey Management**: View, copy, share, and revoke unredeemed join passkeys.

### 5. Analytics & Data Visualization
* **Interactive Wage Trends & Consistency Chart**: Canvas-rendered 7-day gross wage curve (smooth Bezier curve with emerald gradient area fill) and daily attendance consistency stacked bars.
* **Interactive Touch / Scrub Inspection**: Hover or scrub data points to inspect daily gross wages, overtime hours, present/half-day/absent counts, and consistency percentages.

### 6. Two-Column Worker Financial Ledger
* **Dual-Column Accounting**:
  * **Column 1 — Paid to Worker / Earned (+)**: Tracks salary payouts, advances given, overtime pay, and allowances.
  * **Column 2 — Worker Paying / Used (-)**: Tracks tools/materials purchased, fuel/site expenses, advance repayments, and deductions.
* **Mandatory Remarks & Payment Tagging**: Mandatory explanatory remarks for every transaction, tagged with payment channel (*Cash*, *UPI*, *Bank Transfer*, *Company Petty Cash*) and date.
* **Automated Net Balance**: Computes total paid, total used/deducted, and net balance with color-coded status badges (*Payable by Worker*, *Clean Balance*, *Due to Worker*).
* **Dual Access**: Available to Admins from worker cards and to Workers under "My Financial Ledger".

### 7. Payroll & Online Wage Payments
* **Automated Payroll Engine**: Computes net earnings across date ranges (*Past 7 Days*, *Past 14 Days*, *Past 30 Days*, *This Month*) factoring in full days, half days, and 1.5x overtime rates.
* **Online Wage Disbursal**: Disburse via UPI Transfer with instant `upi://pay` deep-link intents (Google Pay, PhonePe, Paytm, BHIM), Direct Bank Transfer, Card, or Cash with transaction reference tracking and digital receipts.
* **Detailed Wage Slips & Audit Export**: Printable/exportable itemized wage slips (`window.print()` formatted) featuring firm header, site info, attendance breakdown, and authorized signature section.

### 8. Offline-First Storage & Sync Center
* **Local Persistence (IndexedDB + SQLite)**: Saves sites, workers, invites, attendance, ledger entries, and sync queues locally on-device so the app works seamlessly without internet connectivity.
* **Live Connectivity Badge**: Top-bar live status indicator showing Live (Green), Offline (Orange), and pending record counter.
* **Sync Engine & Audit Log**: Sync Center screen displaying network state, pending sync queue, manual sync trigger, and chronological reconciliation logs.

---

## 🚀 Getting Started

### Prerequisites
* Python 3.10+ (FastAPI, Uvicorn, Pydantic, Pytest, HTTPX)
* Any modern web browser (Chrome, Edge, Firefox, Safari)

### Quick Run
Double-click `start.bat` or run:
```bash
python run.py
```
Open your browser at `http://localhost:8000`.

### Running Tests
Run the comprehensive test suite verifying Haversine math, dual-role auth, attendance calculations, two-column ledger balances, and payroll calculations:
```bash
pytest -v
```

---

## 🛠️ Architecture
```
krishna-enterprises/
├── app/
│   ├── database.py       # SQLite connection with context manager & table definitions
│   ├── main.py           # FastAPI REST API endpoints & static file serving
│   └── seed.py           # Pre-seeded firm data (Krishna Enterprises, sites, crew, ledger)
├── static/
│   ├── index.html        # Dual-role responsive UI (Admin Console & Worker Portal)
│   ├── css/
│   │   └── style.css     # Industrial construction aesthetic & printable wage slip styles
│   ├── js/
│   │   ├── db.js         # IndexedDB offline store & sync queue
│   │   ├── geo.js        # GPS Geolocation & Haversine distance engine
│   │   ├── charts.js     # Interactive HTML5 Canvas wage & consistency chart
│   │   ├── sync.js       # Offline sync engine & reconciliation log
│   │   └── app.js        # Main UI controller & state management
│   └── sw.js             # Service Worker for PWA asset caching
├── tests/
│   └── test_app.py       # Pytest business logic test suite
├── requirements.txt
├── start.bat
└── README.md
```
