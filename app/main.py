import math
import os
import random
import string
import uuid
from datetime import datetime, timedelta
from typing import Any, Dict, List, Optional

from fastapi import FastAPI, HTTPException, Query, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field

from app.database import get_db, init_db, row_to_dict, rows_to_dicts
from app.seed import seed_database

app = FastAPI(title="Krishna Enterprises - Construction ERP & Crew Tracker", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Initialize and seed database on startup
@app.on_event("startup")
def startup_event():
    init_db()
    seed_database()

# Geolocation Haversine math
def haversine_meters(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    # shortcut: spherical approximation assumes sea level, upgrade to ellipsoid if high elevation variance
    r = 6371000.0
    p1 = math.radians(lat1)
    p2 = math.radians(lat2)
    dp = math.radians(lat2 - lat1)
    dl = math.radians(lon2 - lon1)
    a = math.sin(dp / 2.0) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2.0) ** 2
    c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))
    return round(r * c, 2)

# --- Pydantic Request Models ---
class GoogleAuthRequest(BaseModel):
    email: str
    name: str
    role: str = "worker" # 'admin' or 'worker'
    google_id: Optional[str] = None

class SendOtpRequest(BaseModel):
    phone: str

class VerifyOtpRequest(BaseModel):
    phone: str
    otp: str
    role: Optional[str] = "worker"

class PinLoginRequest(BaseModel):
    phone: str
    pin: str

class OrgRegisterRequest(BaseModel):
    name: str = "Krishna Enterprises"
    manager_name: str
    phone: str
    pin: str

class WorkerJoinRequest(BaseModel):
    code: str
    worker_name: str
    phone: str
    upi_id: Optional[str] = ""

class SiteCreateRequest(BaseModel):
    name: str
    client_name: str
    address: str
    default_daily_wage: float = 750.0
    latitude: float
    longitude: float
    radius_meters: float = 150.0

class WorkerCreateRequest(BaseModel):
    name: str
    phone: str
    site_id: Optional[str] = None
    daily_wage: float
    hourly_rate: Optional[float] = None
    trade: str
    upi_id: Optional[str] = ""
    bank_account: Optional[str] = ""
    ifsc: Optional[str] = ""
    emergency_contact: Optional[str] = ""
    status: str = "active"

class AttendanceMarkRequest(BaseModel):
    worker_id: str
    site_id: str
    date: str
    status: str
    overtime_hours: float = 0.0
    overtime_rate: float = 1.5
    notes: Optional[str] = ""
    verified_gps: int = 0
    distance_meters: float = 0.0
    clock_in_lat: Optional[float] = None
    clock_in_lng: Optional[float] = None

class BatchAttendanceItem(BaseModel):
    worker_id: str
    status: str
    overtime_hours: float = 0.0
    notes: Optional[str] = ""

class BatchAttendanceRequest(BaseModel):
    site_id: str
    date: str
    records: List[BatchAttendanceItem]

class WorkerClockInRequest(BaseModel):
    worker_id: str
    site_id: str
    latitude: float
    longitude: float
    notes: Optional[str] = "Worker Self Check-In"

class InviteCreateRequest(BaseModel):
    site_id: str
    role_trade: str
    default_daily_wage: float

class LedgerEntryRequest(BaseModel):
    worker_id: str
    site_id: Optional[str] = None
    type: str # 'paid' or 'used'
    category: str
    amount: float
    payment_method: str
    remark: str
    transaction_ref: Optional[str] = ""
    date: str

class PaymentDisbursalRequest(BaseModel):
    worker_id: str
    site_id: str
    period_start: str
    period_end: str
    gross_wage: float
    overtime_pay: float
    deductions: float
    net_paid: float
    payment_method: str
    transaction_ref: Optional[str] = ""

class SyncBatchRequest(BaseModel):
    client_id: str
    changes: List[Dict[str, Any]]

# --- 1. Authentication & Dual-Role Access Endpoints ---

@app.get("/api/org")
def get_org_profile():
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM org LIMIT 1")
        row = cursor.fetchone()
        return row_to_dict(row) or {"name": "Krishna Enterprises", "manager_name": "Rajesh Sharma", "phone": "+919876500000"}

@app.post("/api/auth/register-org")
def register_organization(req: OrgRegisterRequest):
    org_id = f"org-{uuid.uuid4().hex[:8]}"
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute("DELETE FROM org")
        cursor.execute("""
        INSERT INTO org (id, name, manager_name, phone, pin)
        VALUES (?, ?, ?, ?, ?)
        """, (org_id, req.name, req.manager_name, req.phone, req.pin))
        # Ensure admin user matches
        cursor.execute("DELETE FROM users WHERE role = 'admin'")
        cursor.execute("""
        INSERT INTO users (id, role, name, email, phone, pin)
        VALUES (?, 'admin', ?, ?, ?, ?)
        """, (f"user-{uuid.uuid4().hex[:8]}", req.manager_name, f"{req.manager_name.lower().replace(' ', '')}@krishna.firm", req.phone, req.pin))
        conn.commit()
    return {"status": "success", "message": f"Firm {req.name} registered successfully", "org_id": org_id}

@app.post("/api/auth/google")
def auth_google(req: GoogleAuthRequest):
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM users WHERE email = ?", (req.email,))
        user = cursor.fetchone()
        if not user:
            # Auto-provision user
            user_id = f"user-{uuid.uuid4().hex[:8]}"
            worker_id = None
            if req.role == "worker":
                # Find matching worker or create new worker
                cursor.execute("SELECT id FROM workers WHERE name LIKE ? LIMIT 1", (f"%{req.name}%",))
                w = cursor.fetchone()
                if w:
                    worker_id = w["id"]
                else:
                    worker_id = f"wrk-{uuid.uuid4().hex[:6]}"
                    cursor.execute("""
                    INSERT INTO workers (id, name, phone, daily_wage, hourly_rate, trade, upi_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, (worker_id, req.name, "+919000000000", 700.0, 87.5, "Construction Craftsman", f"{req.name.lower().replace(' ','')}@upi"))
            cursor.execute("""
            INSERT INTO users (id, role, name, email, google_id, worker_id)
            VALUES (?, ?, ?, ?, ?, ?)
            """, (user_id, req.role, req.name, req.email, req.google_id or "google-mock-id", worker_id))
            conn.commit()
            cursor.execute("SELECT * FROM users WHERE id = ?", (user_id,))
            user = cursor.fetchone()
        return {"status": "success", "user": row_to_dict(user)}

# In-memory OTP store (6-digit OTP code)
# shortcut: in-memory mock OTP store, upgrade to Redis/SMS Gateway (Twilio/Fast2SMS) for multi-worker scaling
OTP_STORE: Dict[str, str] = {}

@app.post("/api/auth/otp/send")
def send_otp(req: SendOtpRequest):
    otp = "".join(random.choices(string.digits, k=6))
    OTP_STORE[req.phone] = otp
    return {"status": "success", "message": f"OTP sent to {req.phone}", "debug_otp": otp}

@app.post("/api/auth/otp/verify")
def verify_otp(req: VerifyOtpRequest):
    stored = OTP_STORE.get(req.phone)
    # Default fallback demo OTP: 123456
    if req.otp != stored and req.otp != "123456":
        raise HTTPException(status_code=400, detail="Invalid 6-digit verification OTP")
    
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM users WHERE phone = ?", (req.phone,))
        user = cursor.fetchone()
        if not user:
            user_id = f"user-{uuid.uuid4().hex[:8]}"
            cursor.execute("SELECT id FROM workers WHERE phone = ?", (req.phone,))
            w = cursor.fetchone()
            worker_id = w["id"] if w else None
            cursor.execute("""
            INSERT INTO users (id, role, name, phone, worker_id)
            VALUES (?, ?, ?, ?, ?)
            """, (user_id, req.role or "worker", f"Worker ({req.phone[-4:]})", req.phone, worker_id))
            conn.commit()
            cursor.execute("SELECT * FROM users WHERE id = ?", (user_id,))
            user = cursor.fetchone()
        return {"status": "success", "user": row_to_dict(user)}

@app.post("/api/auth/login-pin")
def login_with_pin(req: PinLoginRequest):
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM users WHERE phone = ? AND pin = ?", (req.phone, req.pin))
        user = cursor.fetchone()
        if not user:
            # Check if firm admin PIN matches
            cursor.execute("SELECT * FROM org WHERE phone = ? AND pin = ?", (req.phone, req.pin))
            org = cursor.fetchone()
            if org:
                cursor.execute("SELECT * FROM users WHERE role = 'admin' LIMIT 1")
                user = cursor.fetchone()
        if not user:
            raise HTTPException(status_code=401, detail="Invalid phone number or passcode PIN")
        return {"status": "success", "user": row_to_dict(user)}

@app.post("/api/auth/worker-join")
def redeem_worker_join_code(req: WorkerJoinRequest):
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM invites WHERE code = ? AND status = 'active'", (req.code.upper().strip(),))
        invite = cursor.fetchone()
        if not invite:
            raise HTTPException(status_code=400, detail="Invalid, expired, or already redeemed join passkey")
        
        # Create worker
        worker_id = f"wrk-{uuid.uuid4().hex[:6]}"
        daily_wage = invite["default_daily_wage"]
        hourly_rate = round(daily_wage / 8.0, 2)
        cursor.execute("""
        INSERT INTO workers (id, site_id, name, phone, daily_wage, hourly_rate, trade, upi_id, status)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'active')
        """, (worker_id, invite["site_id"], req.worker_name, req.phone, daily_wage, hourly_rate, invite["role_trade"], req.upi_id))
        
        # Mark invite redeemed
        cursor.execute("UPDATE invites SET status = 'redeemed', redeemed_by_worker_id = ? WHERE id = ?", (worker_id, invite["id"]))
        
        # Provision worker user
        user_id = f"user-{uuid.uuid4().hex[:8]}"
        cursor.execute("""
        INSERT INTO users (id, role, name, phone, worker_id)
        VALUES (?, 'worker', ?, ?, ?)
        """, (user_id, req.worker_name, req.phone, worker_id))
        conn.commit()
        
        cursor.execute("SELECT * FROM workers WHERE id = ?", (worker_id,))
        worker_record = row_to_dict(cursor.fetchone())
        return {"status": "success", "message": f"Successfully joined site as {invite['role_trade']}", "worker": worker_record, "user_id": user_id}

# --- 2. Construction Party & GPS Geofencing Endpoints ---

@app.get("/api/sites")
def list_sites():
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM sites ORDER BY created_at DESC")
        return rows_to_dicts(cursor.fetchall())

@app.post("/api/sites")
def create_site(req: SiteCreateRequest):
    site_id = f"site-{uuid.uuid4().hex[:6]}"
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute("""
        INSERT INTO sites (id, name, client_name, address, default_daily_wage, latitude, longitude, radius_meters)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """, (site_id, req.name, req.client_name, req.address, req.default_daily_wage, req.latitude, req.longitude, req.radius_meters))
        conn.commit()
        cursor.execute("SELECT * FROM sites WHERE id = ?", (site_id,))
        return row_to_dict(cursor.fetchone())

@app.put("/api/sites/{site_id}")
def update_site(site_id: str, req: SiteCreateRequest):
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute("""
        UPDATE sites
        SET name = ?, client_name = ?, address = ?, default_daily_wage = ?, latitude = ?, longitude = ?, radius_meters = ?, updated_at = CURRENT_TIMESTAMP
        WHERE id = ?
        """, (req.name, req.client_name, req.address, req.default_daily_wage, req.latitude, req.longitude, req.radius_meters, site_id))
        conn.commit()
        cursor.execute("SELECT * FROM sites WHERE id = ?", (site_id,))
        return row_to_dict(cursor.fetchone())

@app.delete("/api/sites/{site_id}")
def delete_site(site_id: str):
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute("DELETE FROM sites WHERE id = ?", (site_id,))
        conn.commit()
    return {"status": "success", "deleted_id": site_id}

@app.get("/api/sites/{site_id}/verify-gps")
def verify_site_gps(site_id: str, lat: float = Query(...), lng: float = Query(...)):
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM sites WHERE id = ?", (site_id,))
        site = cursor.fetchone()
        if not site:
            raise HTTPException(status_code=404, detail="Site not found")
        distance = haversine_meters(lat, lng, site["latitude"], site["longitude"])
        is_inside = distance <= site["radius_meters"]
        return {
            "site_id": site_id,
            "site_name": site["name"],
            "site_lat": site["latitude"],
            "site_lng": site["longitude"],
            "radius_meters": site["radius_meters"],
            "device_lat": lat,
            "device_lng": lng,
            "distance_meters": distance,
            "is_inside": is_inside,
            "status": "INSIDE PERIMETER" if is_inside else f"OUTSIDE PERIMETER ({round(distance - site['radius_meters'], 1)}m away)"
        }

# --- 3. Attendance Tracking Endpoints ---

@app.get("/api/attendance")
def get_attendance(date: Optional[str] = None, site_id: Optional[str] = None, worker_id: Optional[str] = None):
    with get_db() as conn:
        cursor = conn.cursor()
        query = """
        SELECT a.*, w.name as worker_name, w.trade as worker_trade, w.daily_wage, s.name as site_name
        FROM attendance a
        JOIN workers w ON a.worker_id = w.id
        LEFT JOIN sites s ON a.site_id = s.id
        WHERE 1=1
        """
        params = []
        if date:
            query += " AND a.date = ?"
            params.append(date)
        if site_id:
            query += " AND a.site_id = ?"
            params.append(site_id)
        if worker_id:
            query += " AND a.worker_id = ?"
            params.append(worker_id)
        query += " ORDER BY a.date DESC, w.name ASC"
        cursor.execute(query, params)
        return rows_to_dicts(cursor.fetchall())

@app.post("/api/attendance")
def mark_attendance(req: AttendanceMarkRequest):
    att_id = f"att-{req.worker_id}-{req.date}"
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute("""
        INSERT INTO attendance (id, worker_id, site_id, date, status, overtime_hours, overtime_rate, notes, verified_gps, distance_meters, clock_in_lat, clock_in_lng)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(worker_id, date) DO UPDATE SET
            status = excluded.status,
            site_id = excluded.site_id,
            overtime_hours = excluded.overtime_hours,
            overtime_rate = excluded.overtime_rate,
            notes = excluded.notes,
            verified_gps = excluded.verified_gps,
            distance_meters = excluded.distance_meters,
            clock_in_lat = excluded.clock_in_lat,
            clock_in_lng = excluded.clock_in_lng
        """, (att_id, req.worker_id, req.site_id, req.date, req.status, req.overtime_hours, req.overtime_rate, req.notes, req.verified_gps, req.distance_meters, req.clock_in_lat, req.clock_in_lng))
        conn.commit()
        cursor.execute("SELECT * FROM attendance WHERE id = ?", (att_id,))
        return row_to_dict(cursor.fetchone())

@app.post("/api/attendance/batch")
def batch_mark_attendance(req: BatchAttendanceRequest):
    with get_db() as conn:
        cursor = conn.cursor()
        for r in req.records:
            att_id = f"att-{r.worker_id}-{req.date}"
            cursor.execute("""
            INSERT INTO attendance (id, worker_id, site_id, date, status, overtime_hours, notes)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(worker_id, date) DO UPDATE SET
                status = excluded.status,
                site_id = excluded.site_id,
                overtime_hours = excluded.overtime_hours,
                notes = excluded.notes
            """, (att_id, r.worker_id, req.site_id, req.date, r.status, r.overtime_hours, r.notes))
        conn.commit()
    return {"status": "success", "count": len(req.records)}

@app.post("/api/attendance/clock-in")
def worker_self_clock_in(req: WorkerClockInRequest):
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM sites WHERE id = ?", (req.site_id,))
        site = cursor.fetchone()
        if not site:
            raise HTTPException(status_code=404, detail="Assigned site not found")
        
        dist = haversine_meters(req.latitude, req.longitude, site["latitude"], site["longitude"])
        is_inside = dist <= site["radius_meters"]
        today_str = datetime.now().strftime("%Y-%m-%d")
        att_id = f"att-{req.worker_id}-{today_str}"
        
        cursor.execute("""
        INSERT INTO attendance (id, worker_id, site_id, date, status, overtime_hours, notes, verified_gps, distance_meters, clock_in_lat, clock_in_lng)
        VALUES (?, ?, ?, ?, ?, 0.0, ?, ?, ?, ?, ?)
        ON CONFLICT(worker_id, date) DO UPDATE SET
            site_id = excluded.site_id,
            verified_gps = excluded.verified_gps,
            distance_meters = excluded.distance_meters,
            clock_in_lat = excluded.clock_in_lat,
            clock_in_lng = excluded.clock_in_lng,
            notes = excluded.notes
        """, (att_id, req.worker_id, req.site_id, today_str, "Full Day" if is_inside else "Absent", req.notes, 1 if is_inside else 0, dist, req.latitude, req.longitude))
        conn.commit()
        return {
            "status": "success",
            "is_inside": is_inside,
            "distance_meters": dist,
            "allowed_radius": site["radius_meters"],
            "clock_in_status": "Full Day" if is_inside else "Geo-Verification Failed (Outside Boundary)",
            "message": "Clock-in recorded within geofence" if is_inside else f"Warning: Device is {round(dist - site['radius_meters'], 1)}m outside site perimeter"
        }

# --- 4. Crew Roster & Manual Role Configuration Endpoints ---

@app.get("/api/workers")
def list_workers(site_id: Optional[str] = None, trade: Optional[str] = None):
    with get_db() as conn:
        cursor = conn.cursor()
        query = """
        SELECT w.*, s.name as site_name
        FROM workers w
        LEFT JOIN sites s ON w.site_id = s.id
        WHERE 1=1
        """
        params = []
        if site_id:
            query += " AND w.site_id = ?"
            params.append(site_id)
        if trade:
            query += " AND w.trade = ?"
            params.append(trade)
        query += " ORDER BY w.name ASC"
        cursor.execute(query, params)
        workers = rows_to_dicts(cursor.fetchall())
        
        # Dynamic trade tags from crew
        cursor.execute("SELECT DISTINCT trade FROM workers WHERE trade IS NOT NULL AND trade != '' ORDER BY trade ASC")
        trades = [r["trade"] for r in cursor.fetchall()]
        return {"workers": workers, "trades": trades}

@app.post("/api/workers")
def create_worker(req: WorkerCreateRequest):
    worker_id = f"wrk-{uuid.uuid4().hex[:6]}"
    hourly_rate = req.hourly_rate or round(req.daily_wage / 8.0, 2)
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute("""
        INSERT INTO workers (id, site_id, name, phone, daily_wage, hourly_rate, trade, upi_id, bank_account, ifsc, emergency_contact, status)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (worker_id, req.site_id, req.name, req.phone, req.daily_wage, hourly_rate, req.trade, req.upi_id, req.bank_account, req.ifsc, req.emergency_contact, req.status))
        conn.commit()
        cursor.execute("SELECT * FROM workers WHERE id = ?", (worker_id,))
        return row_to_dict(cursor.fetchone())

@app.put("/api/workers/{worker_id}")
def update_worker(worker_id: str, req: WorkerCreateRequest):
    hourly_rate = req.hourly_rate or round(req.daily_wage / 8.0, 2)
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute("""
        UPDATE workers
        SET site_id = ?, name = ?, phone = ?, daily_wage = ?, hourly_rate = ?, trade = ?, upi_id = ?, bank_account = ?, ifsc = ?, emergency_contact = ?, status = ?
        WHERE id = ?
        """, (req.site_id, req.name, req.phone, req.daily_wage, hourly_rate, req.trade, req.upi_id, req.bank_account, req.ifsc, req.emergency_contact, req.status, worker_id))
        conn.commit()
        cursor.execute("SELECT * FROM workers WHERE id = ?", (worker_id,))
        return row_to_dict(cursor.fetchone())

@app.delete("/api/workers/{worker_id}")
def delete_worker(worker_id: str):
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute("DELETE FROM workers WHERE id = ?", (worker_id,))
        conn.commit()
    return {"status": "success", "deleted_id": worker_id}

@app.get("/api/invites")
def list_invites():
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute("""
        SELECT i.*, s.name as site_name, w.name as redeemed_by_worker_name
        FROM invites i
        JOIN sites s ON i.site_id = s.id
        LEFT JOIN workers w ON i.redeemed_by_worker_id = w.id
        ORDER BY i.created_at DESC
        """)
        return rows_to_dicts(cursor.fetchall())

@app.post("/api/invites")
def create_invite(req: InviteCreateRequest):
    invite_id = f"inv-{uuid.uuid4().hex[:6]}"
    code = f"SC-{random.randint(1000, 9999)}"
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute("""
        INSERT INTO invites (id, code, site_id, role_trade, default_daily_wage, status)
        VALUES (?, ?, ?, ?, ?, 'active')
        """, (invite_id, code, req.site_id, req.role_trade, req.default_daily_wage))
        conn.commit()
        cursor.execute("SELECT i.*, s.name as site_name FROM invites i JOIN sites s ON i.site_id = s.id WHERE i.id = ?", (invite_id,))
        return row_to_dict(cursor.fetchone())

@app.delete("/api/invites/{invite_id}")
def revoke_invite(invite_id: str):
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute("UPDATE invites SET status = 'revoked' WHERE id = ?", (invite_id,))
        conn.commit()
    return {"status": "success", "revoked_id": invite_id}

# --- 5. Analytics & Data Visualization Endpoints ---

@app.get("/api/analytics/wage-trends")
def get_wage_trends():
    with get_db() as conn:
        cursor = conn.cursor()
        today = datetime.now().date()
        daily_trends = []
        
        for i in range(6, -1, -1):
            d = (today - timedelta(days=i)).strftime("%Y-%m-%d")
            cursor.execute("""
            SELECT a.status, a.overtime_hours, a.overtime_rate, w.daily_wage, w.hourly_rate
            FROM attendance a
            JOIN workers w ON a.worker_id = w.id
            WHERE a.date = ?
            """, (d,))
            rows = cursor.fetchall()
            
            present_count = 0
            half_day_count = 0
            absent_count = 0
            leave_count = 0
            overtime_hours = 0.0
            gross_wage = 0.0
            
            for r in rows:
                dw = r["daily_wage"]
                hr = r["hourly_rate"]
                st = r["status"]
                ot = r["overtime_hours"] or 0.0
                rate = r["overtime_rate"] or 1.5
                
                if st == "Full Day":
                    present_count += 1
                    gross_wage += dw
                elif st == "Overtime":
                    present_count += 1
                    overtime_hours += ot
                    gross_wage += dw + (ot * hr * rate)
                elif st == "Half Day":
                    half_day_count += 1
                    gross_wage += (dw / 2.0)
                elif st == "Absent":
                    absent_count += 1
                elif st == "On Leave":
                    leave_count += 1
            
            total_active = present_count + half_day_count + absent_count + leave_count
            consistency = round(((present_count + (half_day_count * 0.5)) / total_active * 100.0), 1) if total_active > 0 else 0.0
            
            daily_trends.append({
                "date": d,
                "label": (today - timedelta(days=i)).strftime("%a %d %b"),
                "gross_wage": round(gross_wage, 2),
                "overtime_hours": round(overtime_hours, 1),
                "present_count": present_count,
                "half_day_count": half_day_count,
                "absent_count": absent_count,
                "leave_count": leave_count,
                "total_crew": total_active,
                "consistency": consistency
            })
            
        return {"trends": daily_trends}

# --- 6. Two-Column Worker Financial Ledger Endpoints ---

@app.get("/api/ledger/{worker_id}")
def get_worker_ledger(worker_id: str):
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM workers WHERE id = ?", (worker_id,))
        worker = cursor.fetchone()
        if not worker:
            raise HTTPException(status_code=404, detail="Worker not found")
        
        cursor.execute("""
        SELECT * FROM ledger
        WHERE worker_id = ?
        ORDER BY date DESC, created_at DESC
        """, (worker_id,))
        entries = rows_to_dicts(cursor.fetchall())
        
        total_paid = sum(e["amount"] for e in entries if e["type"] == "paid")
        total_used = sum(e["amount"] for e in entries if e["type"] == "used")
        net_balance = round(total_paid - total_used, 2)
        
        return {
            "worker": row_to_dict(worker),
            "entries": entries,
            "total_paid": round(total_paid, 2),
            "total_used": round(total_used, 2),
            "net_balance": net_balance,
            "balance_status": "Payable by Worker" if net_balance > 0 else ("Recoverable / Clean" if net_balance == 0 else "Due to Worker")
        }

@app.post("/api/ledger")
def add_ledger_entry(req: LedgerEntryRequest):
    entry_id = f"led-{uuid.uuid4().hex[:6]}"
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute("""
        INSERT INTO ledger (id, worker_id, site_id, type, category, amount, payment_method, remark, transaction_ref, date)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (entry_id, req.worker_id, req.site_id, req.type, req.category, req.amount, req.payment_method, req.remark, req.transaction_ref, req.date))
        conn.commit()
        cursor.execute("SELECT * FROM ledger WHERE id = ?", (entry_id,))
        return row_to_dict(cursor.fetchone())

@app.delete("/api/ledger/{entry_id}")
def delete_ledger_entry(entry_id: str):
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute("DELETE FROM ledger WHERE id = ?", (entry_id,))
        conn.commit()
    return {"status": "success", "deleted_id": entry_id}

# --- 7. Payroll & Online Wage Payments Endpoints ---

@app.get("/api/payroll/calculate")
def calculate_payroll(period: str = "7days", site_id: Optional[str] = None):
    # Determine date range
    today = datetime.now().date()
    if period == "7days":
        start_date = today - timedelta(days=7)
    elif period == "14days":
        start_date = today - timedelta(days=14)
    elif period == "30days":
        start_date = today - timedelta(days=30)
    elif period == "this_month":
        start_date = today.replace(day=1)
    else:
        start_date = today - timedelta(days=7)
    
    start_str = start_date.strftime("%Y-%m-%d")
    today_str = today.strftime("%Y-%m-%d")
    
    with get_db() as conn:
        cursor = conn.cursor()
        worker_query = "SELECT * FROM workers WHERE status = 'active'"
        w_params = []
        if site_id:
            worker_query += " AND site_id = ?"
            w_params.append(site_id)
        cursor.execute(worker_query, w_params)
        workers = rows_to_dicts(cursor.fetchall())
        
        payroll_list = []
        total_payroll_amount = 0.0
        
        for w in workers:
            w_id = w["id"]
            cursor.execute("""
            SELECT status, overtime_hours, overtime_rate
            FROM attendance
            WHERE worker_id = ? AND date >= ? AND date <= ?
            """, (w_id, start_str, today_str))
            att_records = cursor.fetchall()
            
            full_days = sum(1 for a in att_records if a["status"] in ["Full Day", "Overtime"])
            half_days = sum(1 for a in att_records if a["status"] == "Half Day")
            total_ot_hours = sum(a["overtime_hours"] or 0.0 for a in att_records)
            
            daily_wage = w["daily_wage"]
            hourly_rate = w["hourly_rate"]
            base_earnings = (full_days * daily_wage) + (half_days * (daily_wage / 2.0))
            ot_earnings = total_ot_hours * hourly_rate * 1.5
            gross_wage = round(base_earnings + ot_earnings, 2)
            
            # Fetch ledger deductions and advances
            cursor.execute("""
            SELECT type, category, amount FROM ledger
            WHERE worker_id = ? AND date >= ? AND date <= ?
            """, (w_id, start_str, today_str))
            ledgers = cursor.fetchall()
            advances_paid = sum(l["amount"] for l in ledgers if l["type"] == "paid" and "Advance" in l["category"])
            deductions = sum(l["amount"] for l in ledgers if l["type"] == "used")
            
            net_payable = max(0.0, round(gross_wage - deductions - advances_paid, 2))
            total_payroll_amount += net_payable
            
            # UPI Deep link for instant disbursal
            # upi://pay?pa=...&pn=...&am=...&cu=INR&tn=...
            upi_intent = ""
            if w.get("upi_id"):
                pa = w["upi_id"]
                pn = w["name"].replace(" ", "%20")
                tn = f"Krishna%20Enterprises%20Wage%20{start_str}%20to%20{today_str}"
                upi_intent = f"upi://pay?pa={pa}&pn={pn}&am={net_payable}&cu=INR&tn={tn}"
            
            payroll_list.append({
                "worker_id": w_id,
                "name": w["name"],
                "phone": w["phone"],
                "trade": w["trade"],
                "site_id": w["site_id"],
                "daily_wage": daily_wage,
                "hourly_rate": hourly_rate,
                "upi_id": w.get("upi_id") or "",
                "bank_account": w.get("bank_account") or "",
                "ifsc": w.get("ifsc") or "",
                "full_days": full_days,
                "half_days": half_days,
                "ot_hours": total_ot_hours,
                "base_earnings": base_earnings,
                "ot_earnings": round(ot_earnings, 2),
                "gross_wage": gross_wage,
                "advances_paid": advances_paid,
                "deductions": deductions,
                "net_payable": net_payable,
                "upi_intent": upi_intent
            })
            
        return {
            "period": period,
            "start_date": start_str,
            "end_date": today_str,
            "total_workers": len(payroll_list),
            "total_payroll_amount": round(total_payroll_amount, 2),
            "payroll": payroll_list
        }

@app.post("/api/payroll/disburse")
def disburse_payment(req: PaymentDisbursalRequest):
    pay_id = f"pay-{uuid.uuid4().hex[:6]}"
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute("""
        INSERT INTO payments (id, worker_id, site_id, period_start, period_end, gross_wage, overtime_pay, deductions, net_paid, payment_method, transaction_ref, status)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'completed')
        """, (pay_id, req.worker_id, req.site_id, req.period_start, req.period_end, req.gross_wage, req.overtime_pay, req.deductions, req.net_paid, req.payment_method, req.transaction_ref))
        
        # Add to worker ledger as Salary Payout
        led_id = f"led-{uuid.uuid4().hex[:6]}"
        cursor.execute("""
        INSERT INTO ledger (id, worker_id, site_id, type, category, amount, payment_method, remark, transaction_ref, date)
        VALUES (?, ?, ?, 'paid', 'Salary Payout', ?, ?, ?, ?, ?)
        """, (led_id, req.worker_id, req.site_id, req.net_paid, req.payment_method, f"Disbursed wages for {req.period_start} to {req.period_end}", req.transaction_ref, datetime.now().strftime("%Y-%m-%d")))
        
        conn.commit()
        return {"status": "success", "payment_id": pay_id, "message": f"Payment ₹{req.net_paid} disbursed via {req.payment_method}"}

@app.get("/api/payroll/wage-slip/{worker_id}")
def get_wage_slip(worker_id: str, start_date: Optional[str] = None, end_date: Optional[str] = None):
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM org LIMIT 1")
        org = row_to_dict(cursor.fetchone())
        
        cursor.execute("""
        SELECT w.*, s.name as site_name, s.address as site_address
        FROM workers w
        LEFT JOIN sites s ON w.site_id = s.id
        WHERE w.id = ?
        """, (worker_id,))
        worker = row_to_dict(cursor.fetchone())
        if not worker:
            raise HTTPException(status_code=404, detail="Worker not found")
        
        today = datetime.now().date()
        s_date = start_date or (today - timedelta(days=7)).strftime("%Y-%m-%d")
        e_date = end_date or today.strftime("%Y-%m-%d")
        
        cursor.execute("""
        SELECT * FROM attendance
        WHERE worker_id = ? AND date >= ? AND date <= ?
        ORDER BY date ASC
        """, (worker_id, s_date, e_date))
        att_rows = rows_to_dicts(cursor.fetchall())
        
        full_days = sum(1 for a in att_rows if a["status"] in ["Full Day", "Overtime"])
        half_days = sum(1 for a in att_rows if a["status"] == "Half Day")
        ot_hours = sum(a["overtime_hours"] or 0.0 for a in att_rows)
        
        daily_wage = worker["daily_wage"]
        hourly_rate = worker["hourly_rate"]
        base_earnings = (full_days * daily_wage) + (half_days * (daily_wage / 2.0))
        ot_earnings = round(ot_hours * hourly_rate * 1.5, 2)
        gross_wage = round(base_earnings + ot_earnings, 2)
        
        # Deductions & advances from ledger
        cursor.execute("""
        SELECT * FROM ledger
        WHERE worker_id = ? AND date >= ? AND date <= ?
        ORDER BY date ASC
        """, (worker_id, s_date, e_date))
        ledger_items = rows_to_dicts(cursor.fetchall())
        
        deductions = sum(l["amount"] for l in ledger_items if l["type"] == "used")
        advances = sum(l["amount"] for l in ledger_items if l["type"] == "paid" and "Advance" in l["category"])
        net_payable = max(0.0, round(gross_wage - deductions - advances, 2))
        
        return {
            "firm": org,
            "worker": worker,
            "period": {"start_date": s_date, "end_date": e_date},
            "attendance_summary": {
                "full_days": full_days,
                "half_days": half_days,
                "overtime_hours": ot_hours,
                "records": att_rows
            },
            "earnings": {
                "daily_wage": daily_wage,
                "hourly_rate": hourly_rate,
                "base_pay": base_earnings,
                "overtime_pay": ot_earnings,
                "gross_wage": gross_wage
            },
            "deductions_and_advances": {
                "advances_given": advances,
                "expenses_and_deductions": deductions,
                "total_deductions": round(deductions + advances, 2),
                "items": ledger_items
            },
            "net_payable": net_payable,
            "slip_number": f"WS-{worker_id.upper()}-{today.strftime('%Y%m%d')}",
            "generated_at": datetime.now().strftime("%d-%b-%Y %I:%M %p")
        }

# --- 8. Offline-First Storage & Sync Center Endpoints ---

@app.post("/api/sync/batch")
def sync_batch(req: SyncBatchRequest):
    reconciled_count = 0
    with get_db() as conn:
        cursor = conn.cursor()
        for item in req.changes:
            entity = item.get("entity")
            action = item.get("action")
            payload = item.get("payload", {})
            rec_id = payload.get("id") or str(uuid.uuid4())
            
            if entity == "attendance":
                cursor.execute("""
                INSERT INTO attendance (id, worker_id, site_id, date, status, overtime_hours, overtime_rate, notes, verified_gps, distance_meters, clock_in_lat, clock_in_lng)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(worker_id, date) DO UPDATE SET
                    status = excluded.status,
                    site_id = excluded.site_id,
                    overtime_hours = excluded.overtime_hours,
                    notes = excluded.notes,
                    verified_gps = excluded.verified_gps,
                    distance_meters = excluded.distance_meters
                """, (rec_id, payload.get("worker_id"), payload.get("site_id"), payload.get("date"), payload.get("status"), payload.get("overtime_hours", 0.0), payload.get("overtime_rate", 1.5), payload.get("notes", ""), payload.get("verified_gps", 0), payload.get("distance_meters", 0.0), payload.get("clock_in_lat"), payload.get("clock_in_lng")))
                reconciled_count += 1
            elif entity == "ledger":
                cursor.execute("""
                INSERT INTO ledger (id, worker_id, site_id, type, category, amount, payment_method, remark, transaction_ref, date)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    amount = excluded.amount,
                    remark = excluded.remark
                """, (rec_id, payload.get("worker_id"), payload.get("site_id"), payload.get("type"), payload.get("category"), payload.get("amount"), payload.get("payment_method"), payload.get("remark"), payload.get("transaction_ref"), payload.get("date")))
                reconciled_count += 1
            elif entity == "worker":
                cursor.execute("""
                INSERT INTO workers (id, site_id, name, phone, daily_wage, hourly_rate, trade, upi_id, bank_account, ifsc, emergency_contact, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    name = excluded.name,
                    daily_wage = excluded.daily_wage,
                    trade = excluded.trade
                """, (rec_id, payload.get("site_id"), payload.get("name"), payload.get("phone"), payload.get("daily_wage"), payload.get("hourly_rate"), payload.get("trade"), payload.get("upi_id"), payload.get("bank_account"), payload.get("ifsc"), payload.get("emergency_contact"), payload.get("status", "active")))
                reconciled_count += 1
            
            # Log audit
            cursor.execute("""
            INSERT INTO sync_logs (id, client_id, entity_type, action, record_id, payload, status)
            VALUES (?, ?, ?, ?, ?, ?, 'synced')
            """, (str(uuid.uuid4()), req.client_id, entity, action, rec_id, str(payload)))

        conn.commit()
    return {"status": "success", "client_id": req.client_id, "reconciled_records": reconciled_count}

@app.get("/api/sync/status")
def sync_status():
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT COUNT(*) FROM sync_logs")
        total_syncs = cursor.fetchone()[0]
        
        cursor.execute("SELECT * FROM sync_logs ORDER BY synced_at DESC LIMIT 20")
        recent_logs = rows_to_dicts(cursor.fetchall())
        
        return {
            "server_time": datetime.now().isoformat(),
            "total_sync_events": total_syncs,
            "status": "online",
            "recent_logs": recent_logs
        }

# Mount static folder
ROOT_DIR = os.path.dirname(os.path.dirname(__file__))
STATIC_DIR = os.path.join(ROOT_DIR, "static")
if os.path.exists(STATIC_DIR):
    app.mount("/static", StaticFiles(directory=STATIC_DIR), name="static")

@app.get("/")
@app.get("/index.html")
def serve_index():
    root_index = os.path.join(ROOT_DIR, "index.html")
    if os.path.exists(root_index):
        return FileResponse(root_index)
    static_index = os.path.join(STATIC_DIR, "index.html")
    if os.path.exists(static_index):
        return FileResponse(static_index)
    return {"message": "Krishna Enterprises API is online"}

@app.get("/portal")
@app.get("/erp")
def serve_portal():
    index_file = os.path.join(STATIC_DIR, "index.html")
    return FileResponse(index_file)

@app.get("/about.html")
def serve_about():
    p = os.path.join(ROOT_DIR, "about.html")
    return FileResponse(p) if os.path.exists(p) else FileResponse(os.path.join(STATIC_DIR, "index.html"))

@app.get("/services.html")
def serve_services():
    p = os.path.join(ROOT_DIR, "services.html")
    return FileResponse(p) if os.path.exists(p) else FileResponse(os.path.join(STATIC_DIR, "index.html"))

@app.get("/gallery.html")
def serve_gallery():
    p = os.path.join(ROOT_DIR, "gallery.html")
    return FileResponse(p) if os.path.exists(p) else FileResponse(os.path.join(STATIC_DIR, "index.html"))

@app.get("/contact.html")
def serve_contact():
    p = os.path.join(ROOT_DIR, "contact.html")
    return FileResponse(p) if os.path.exists(p) else FileResponse(os.path.join(STATIC_DIR, "index.html"))

@app.get("/sw.js")
def serve_service_worker():
    sw_file = os.path.join(STATIC_DIR, "sw.js")
    if os.path.exists(sw_file):
        return FileResponse(sw_file, media_type="application/javascript")
    return JSONResponse(status_code=404, content={"message": "Not found"})
