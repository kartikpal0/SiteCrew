import uuid
from datetime import datetime, timedelta
from app.database import get_db, init_db

def seed_database():
    init_db()
    with get_db() as conn:
        cursor = conn.cursor()
        
        # Check if already seeded
        cursor.execute("SELECT COUNT(*) FROM org")
        if cursor.fetchone()[0] > 0:
            return

        # 1. Organization
        org_id = "org-krishna-01"
        cursor.execute("""
        INSERT INTO org (id, name, manager_name, phone, pin)
        VALUES (?, ?, ?, ?, ?)
        """, (org_id, "Krishna Enterprises", "Rajesh Sharma", "+919876500000", "1234"))

        # Admin user
        cursor.execute("""
        INSERT INTO users (id, role, name, email, phone, pin)
        VALUES (?, ?, ?, ?, ?, ?)
        """, ("user-admin-01", "admin", "Rajesh Sharma (Admin)", "admin@krishnaenterprises.com", "+919876500000", "1234"))

        # 2. Construction Sites / Parties
        sites = [
            ("site-01", "Krishna Heights Phase 1", "DLF Infrastructure", "Plot B-14, Sector 62, Noida, UP", 750.0, 28.6280, 77.3649, 150.0),
            ("site-02", "Ganga View Residency", "Apex Builders Pvt Ltd", "Tower C, Ahinsa Khand 2, Indirapuram, Ghaziabad", 700.0, 28.6415, 77.3712, 200.0),
            ("site-03", "Krishna Commercial Plaza", "Vardhman Group", "NH-24 Expressway Crossing, Greater Noida West", 800.0, 28.6012, 77.4320, 250.0)
        ]
        cursor.executemany("""
        INSERT INTO sites (id, name, client_name, address, default_daily_wage, latitude, longitude, radius_meters)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """, sites)

        # 3. Crew Roster with Manual Trade / Job Entry
        # hourly_rate = daily_wage / 8
        workers = [
            ("wrk-01", "site-01", "Ramesh Kumar", "+919876543210", 850.0, 106.25, "Master Mason", "ramesh@oksbi", "30981245678", "SBIN0001234", "+919811122233", "active"),
            ("wrk-02", "site-01", "Suresh Yadav", "+919876543211", 750.0, 93.75, "Steel Barbender", "suresh@okaxis", "40981245679", "UTIB0000567", "+919822233344", "active"),
            ("wrk-03", "site-01", "Manoj Tiwari", "+919876543212", 800.0, 100.0, "Shuttering Carpenter", "manoj@ybl", "50981245680", "PUNB0007890", "+919833344455", "active"),
            ("wrk-04", "site-02", "Vijay Pratap", "+919876543213", 800.0, 100.0, "Site Electrician", "vijay@paytm", "60981245681", "HDFC0001122", "+919844455566", "active"),
            ("wrk-05", "site-02", "Sonu Chauhan", "+919876543214", 600.0, 75.0, "General Helper", "sonu@upi", "70981245682", "ICIC0003344", "+919855566677", "active"),
            ("wrk-06", "site-03", "Amit Bind", "+919876543215", 750.0, 93.75, "Plumbing Technician", "amit@oksbi", "80981245683", "SBIN0005566", "+919866677788", "active")
        ]
        cursor.executemany("""
        INSERT INTO workers (id, site_id, name, phone, daily_wage, hourly_rate, trade, upi_id, bank_account, ifsc, emergency_contact, status)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, workers)

        # Worker user logins
        for w in workers:
            cursor.execute("""
            INSERT INTO users (id, role, name, email, phone, pin, worker_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, (f"user-{w[0]}", "worker", w[2], f"{w[2].lower().replace(' ', '')}@krishna.work", w[3], "1111", w[0]))

        # 4. Invite Codes (Passkeys)
        invites = [
            ("inv-01", "SC-8421", "site-01", "Welder & Fabricator", 850.0, "active", None),
            ("inv-02", "SC-9134", "site-01", "Tiles Mistri", 900.0, "active", None),
            ("inv-03", "SC-3052", "site-02", "Tower Crane Operator", 1100.0, "active", None),
            ("inv-04", "SC-7719", "site-03", "Concrete Vibrator Operator", 700.0, "active", None)
        ]
        cursor.executemany("""
        INSERT INTO invites (id, code, site_id, role_trade, default_daily_wage, status, redeemed_by_worker_id)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """, invites)

        # 5. Past 7 Days Attendance Records
        today = datetime.now().date()
        attendance_seed = []
        statuses = ["Full Day", "Full Day", "Overtime", "Full Day", "Half Day", "Full Day", "Full Day"]
        
        for day_offset in range(7, -1, -1):
            d = (today - timedelta(days=day_offset)).strftime("%Y-%m-%d")
            for idx, w in enumerate(workers):
                st = statuses[(idx + day_offset) % len(statuses)]
                ot_hrs = 2.0 if st == "Overtime" else 0.0
                site_id = w[1]
                attendance_seed.append((
                    f"att-{w[0]}-{d}",
                    w[0],
                    site_id,
                    d,
                    st,
                    ot_hrs,
                    1.5,
                    "Night slab casting shift" if ot_hrs > 0 else "Regular site shift",
                    1,
                    24.5,
                    28.6281,
                    77.3650
                ))

        cursor.executemany("""
        INSERT INTO attendance (id, worker_id, site_id, date, status, overtime_hours, overtime_rate, notes, verified_gps, distance_meters, clock_in_lat, clock_in_lng)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, attendance_seed)

        # 6. Two-Column Financial Ledger Entries
        # Column 1 = 'paid' (Earned/Paid to Worker: advances, overtime pay, bonus)
        # Column 2 = 'used' (Worker Paying / Used: materials, diesel, advance repayment, equipment deductions)
        ledger_entries = [
            ("led-01", "wrk-01", "site-01", "paid", "Advance Given", 2000.0, "UPI", "Emergency family medical advance", "UPI78491024", (today - timedelta(days=5)).strftime("%Y-%m-%d")),
            ("led-02", "wrk-01", "site-01", "used", "Advance Repayment", 1000.0, "Cash", "Deduction against weekly payout", "REF-W1-DED", (today - timedelta(days=1)).strftime("%Y-%m-%d")),
            ("led-03", "wrk-01", "site-01", "used", "Tools/Materials", 450.0, "Cash", "Bought trowels & level pipe for plaster", "BILL-NOIDA-55", (today - timedelta(days=3)).strftime("%Y-%m-%d")),
            
            ("led-04", "wrk-02", "site-01", "paid", "Advance Given", 1500.0, "Cash", "Festival advance for Chhath Puja", "CASH-DISB-09", (today - timedelta(days=6)).strftime("%Y-%m-%d")),
            ("led-05", "wrk-02", "site-01", "used", "Fuel/Site Expense", 350.0, "Cash", "Generator diesel for rebar cutter", "PETROL-PUMP-88", (today - timedelta(days=2)).strftime("%Y-%m-%d")),
            
            ("led-06", "wrk-03", "site-01", "paid", "Overtime Pay", 800.0, "Bank Transfer", "Sunday emergency column beam shuttering", "NEFT903411", (today - timedelta(days=4)).strftime("%Y-%m-%d")),
            ("led-07", "wrk-04", "site-02", "paid", "Advance Given", 1200.0, "UPI", "Conduit wire advance payment", "UPI4512903", (today - timedelta(days=3)).strftime("%Y-%m-%d")),
            ("led-08", "wrk-05", "site-02", "used", "Tools/Materials", 200.0, "Company Petty Cash", "Safety gloves & helmets purchased", "PETTY-014", (today - timedelta(days=2)).strftime("%Y-%m-%d"))
        ]
        cursor.executemany("""
        INSERT INTO ledger (id, worker_id, site_id, type, category, amount, payment_method, remark, transaction_ref, date)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, ledger_entries)

        # 7. Initial Sync Log
        cursor.execute("""
        INSERT INTO sync_logs (id, client_id, entity_type, action, record_id, payload, status)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """, (str(uuid.uuid4()), "server-init", "system", "seed", "all", "Pre-seeded Krishna Enterprises initial roster & party database", "synced"))

        conn.commit()

if __name__ == "__main__":
    seed_database()
    print("Krishna Enterprises database seeded successfully.")
