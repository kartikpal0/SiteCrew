import sqlite3
import os
import contextlib
from typing import Any, Dict, List, Optional

DB_PATH = os.path.join(os.path.dirname(os.path.dirname(__file__)), "krishna_enterprises.db")

@contextlib.contextmanager
def get_db():
    conn = sqlite3.connect(DB_PATH, timeout=20.0)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    try:
        yield conn
    finally:
        conn.close()

def init_db():
    with get_db() as conn:
        cursor = conn.cursor()
        
        # Organization profile
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS org (
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            manager_name TEXT NOT NULL,
            phone TEXT NOT NULL,
            pin TEXT NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
        """)

        # Users (Admins and Workers)
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS users (
            id TEXT PRIMARY KEY,
            role TEXT NOT NULL,
            name TEXT NOT NULL,
            email TEXT,
            phone TEXT,
            pin TEXT,
            worker_id TEXT,
            google_id TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
        """)

        # Construction Sites / Parties
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS sites (
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            client_name TEXT NOT NULL,
            address TEXT NOT NULL,
            default_daily_wage REAL NOT NULL DEFAULT 650.0,
            latitude REAL NOT NULL,
            longitude REAL NOT NULL,
            radius_meters REAL NOT NULL DEFAULT 150.0,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
        """)

        # Crew Roster
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS workers (
            id TEXT PRIMARY KEY,
            site_id TEXT,
            name TEXT NOT NULL,
            phone TEXT NOT NULL,
            daily_wage REAL NOT NULL,
            hourly_rate REAL NOT NULL,
            trade TEXT NOT NULL,
            upi_id TEXT,
            bank_account TEXT,
            ifsc TEXT,
            emergency_contact TEXT,
            status TEXT NOT NULL DEFAULT 'active',
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (site_id) REFERENCES sites(id) ON DELETE SET NULL
        )
        """)

        # Private Worker Join Passkeys
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS invites (
            id TEXT PRIMARY KEY,
            code TEXT UNIQUE NOT NULL,
            site_id TEXT NOT NULL,
            role_trade TEXT NOT NULL,
            default_daily_wage REAL NOT NULL,
            status TEXT NOT NULL DEFAULT 'active',
            redeemed_by_worker_id TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (site_id) REFERENCES sites(id) ON DELETE CASCADE
        )
        """)

        # Attendance Records
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS attendance (
            id TEXT PRIMARY KEY,
            worker_id TEXT NOT NULL,
            site_id TEXT NOT NULL,
            date TEXT NOT NULL,
            status TEXT NOT NULL,
            overtime_hours REAL DEFAULT 0.0,
            overtime_rate REAL DEFAULT 1.5,
            notes TEXT,
            verified_gps INTEGER DEFAULT 0,
            distance_meters REAL DEFAULT 0.0,
            clock_in_lat REAL,
            clock_in_lng REAL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            UNIQUE(worker_id, date),
            FOREIGN KEY (worker_id) REFERENCES workers(id) ON DELETE CASCADE,
            FOREIGN KEY (site_id) REFERENCES sites(id) ON DELETE CASCADE
        )
        """)

        # Two-Column Financial Ledger
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS ledger (
            id TEXT PRIMARY KEY,
            worker_id TEXT NOT NULL,
            site_id TEXT,
            type TEXT NOT NULL,
            category TEXT NOT NULL,
            amount REAL NOT NULL,
            payment_method TEXT NOT NULL,
            remark TEXT NOT NULL,
            transaction_ref TEXT,
            date TEXT NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (worker_id) REFERENCES workers(id) ON DELETE CASCADE
        )
        """)

        # Payroll Wage Disbursal
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS payments (
            id TEXT PRIMARY KEY,
            worker_id TEXT NOT NULL,
            site_id TEXT NOT NULL,
            period_start TEXT NOT NULL,
            period_end TEXT NOT NULL,
            gross_wage REAL NOT NULL,
            overtime_pay REAL NOT NULL,
            deductions REAL NOT NULL,
            net_paid REAL NOT NULL,
            payment_method TEXT NOT NULL,
            transaction_ref TEXT,
            status TEXT NOT NULL DEFAULT 'completed',
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (worker_id) REFERENCES workers(id) ON DELETE CASCADE,
            FOREIGN KEY (site_id) REFERENCES sites(id) ON DELETE CASCADE
        )
        """)

        # Sync Center Audit Log
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS sync_logs (
            id TEXT PRIMARY KEY,
            client_id TEXT NOT NULL,
            entity_type TEXT NOT NULL,
            action TEXT NOT NULL,
            record_id TEXT NOT NULL,
            payload TEXT,
            synced_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            status TEXT NOT NULL DEFAULT 'synced'
        )
        """)

        conn.commit()

# Helper dict conversion
def rows_to_dicts(rows: List[sqlite3.Row]) -> List[Dict[str, Any]]:
    return [dict(r) for r in rows]

def row_to_dict(row: Optional[sqlite3.Row]) -> Optional[Dict[str, Any]]:
    return dict(row) if row else None
