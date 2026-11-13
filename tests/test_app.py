import math
import pytest
from app.main import (
    haversine_meters,
    get_org_profile,
    list_sites,
    create_site,
    verify_site_gps,
    create_invite,
    redeem_worker_join_code,
    list_workers,
    mark_attendance,
    add_ledger_entry,
    get_worker_ledger,
    calculate_payroll,
    get_wage_slip,
    SiteCreateRequest,
    InviteCreateRequest,
    WorkerJoinRequest,
    AttendanceMarkRequest,
    LedgerEntryRequest
)
from app.database import init_db
from app.seed import seed_database

@pytest.fixture(scope="session", autouse=True)
def setup_test_db():
    init_db()
    seed_database()

def test_haversine_math():
    lat1, lon1 = 28.6280, 77.3649
    # Same point distance should be zero
    assert haversine_meters(lat1, lon1, lat1, lon1) == 0.0
    
    # 100 meters away approx
    lat2, lon2 = 28.6289, 77.3649
    d = haversine_meters(lat1, lon1, lat2, lon2)
    assert 90.0 < d < 110.0

def test_get_org_and_sites():
    org = get_org_profile()
    assert "Krishna Enterprises" in org["name"]
    assert org["manager_name"] == "Rajesh Sharma"

    sites = list_sites()
    assert len(sites) >= 2
    assert any("Krishna Heights" in s["name"] for s in sites)

def test_verify_gps_geofence():
    sites = list_sites()
    site = sites[0]
    site_id = site["id"]
    
    # Inside perimeter
    inside_res = verify_site_gps(site_id=site_id, lat=site["latitude"], lng=site["longitude"])
    assert inside_res["is_inside"] is True
    assert inside_res["distance_meters"] == 0.0

    # Outside perimeter
    outside_res = verify_site_gps(site_id=site_id, lat=site["latitude"] + 0.08, lng=site["longitude"] + 0.08)
    assert outside_res["is_inside"] is False
    assert outside_res["distance_meters"] > site["radius_meters"]

def test_worker_join_passkey_workflow():
    sites = list_sites()
    site_id = sites[0]["id"]
    
    # Generate single-use passkey
    inv_req = InviteCreateRequest(
        site_id=site_id,
        role_trade="Senior Welder",
        default_daily_wage=950.0
    )
    invite = create_invite(inv_req)
    assert invite["code"].startswith("SC-")
    
    # Redeem passkey
    join_req = WorkerJoinRequest(
        code=invite["code"],
        worker_name="Kailash Chand",
        phone="+919999888877",
        upi_id="kailash@upi"
    )
    res = redeem_worker_join_code(join_req)
    assert res["status"] == "success"
    assert res["worker"]["name"] == "Kailash Chand"
    assert res["worker"]["trade"] == "Senior Welder"
    assert res["worker"]["daily_wage"] == 950.0

def test_attendance_and_overtime_math():
    workers_resp = list_workers()
    worker = workers_resp["workers"][0]
    
    att_req = AttendanceMarkRequest(
        worker_id=worker["id"],
        site_id=worker["site_id"],
        date="2026-11-20",
        status="Overtime",
        overtime_hours=3.0,
        overtime_rate=1.5,
        notes="Slab casting extra hours",
        verified_gps=1,
        distance_meters=20.0
    )
    res = mark_attendance(att_req)
    assert res["status"] == "Overtime"
    assert res["overtime_hours"] == 3.0

def test_two_column_financial_ledger():
    workers_resp = list_workers()
    worker = workers_resp["workers"][0]
    worker_id = worker["id"]
    
    # Column 1 (Paid / Advance)
    add_ledger_entry(LedgerEntryRequest(
        worker_id=worker_id,
        site_id=worker["site_id"],
        type="paid",
        category="Advance Given",
        amount=2500.0,
        payment_method="UPI",
        remark="Chhath festival advance",
        transaction_ref="UPI-771122",
        date="2026-11-20"
    ))

    # Column 2 (Worker Paying / Used: Tool expense)
    add_ledger_entry(LedgerEntryRequest(
        worker_id=worker_id,
        site_id=worker["site_id"],
        type="used",
        category="Tools/Materials",
        amount=400.0,
        payment_method="Cash",
        remark="Mason spirit level and line dori",
        transaction_ref="BILL-009",
        date="2026-11-20"
    ))

    # Review balance
    summary = get_worker_ledger(worker_id)
    assert summary["total_paid"] >= 2500.0
    assert summary["total_used"] >= 400.0
    assert summary["net_balance"] == round(summary["total_paid"] - summary["total_used"], 2)

def test_payroll_generation_and_wage_slips():
    payroll = calculate_payroll(period="7days")
    assert payroll["total_workers"] > 0
    assert payroll["total_payroll_amount"] >= 0
    
    # Check itemized wage slip
    worker_id = payroll["payroll"][0]["worker_id"]
    slip = get_wage_slip(worker_id)
    assert "Krishna Enterprises" in slip["firm"]["name"]
    assert slip["slip_number"].startswith(f"WS-{worker_id.upper()}")
    assert slip["net_payable"] >= 0
