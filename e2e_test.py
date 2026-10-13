#!/usr/bin/env python3
"""
End-to-end test for the Ledgerline expense tracker.
Exercises every major feature through real HTTP requests:
signup/login, CRUD, filters, budgets (+BudgetExceededException warning),
undo delete, CSV export/import, backup/restore, categories, reports,
currency + theme preferences, logout.

Run:  python3 e2e_test.py  (app must be running on localhost:8080)
"""
import http.client, urllib.parse, re, sys

BASE = "localhost:8080"
PASS = 0
FAIL = 0
JAR = {}  # cookie jar

def check(name, cond, detail=""):
    global PASS, FAIL
    if cond:
        PASS += 1
        print(f"  ok   {name}")
    else:
        FAIL += 1
        print(f"  FAIL {name} {detail}")

def req(method, path, body=None, headers=None):
    conn = http.client.HTTPConnection(BASE, timeout=15)
    h = dict(headers or {})
    if JAR:
        h["Cookie"] = "; ".join(f"{k}={v}" for k, v in JAR.items())
    data = None
    if body is not None:
        data = urllib.parse.urlencode(body).encode()
        h["Content-Type"] = "application/x-www-form-urlencoded"
    conn.request(method, path, body=data, headers=h)
    resp = conn.getresponse()
    content = resp.read().decode("utf-8", "replace")
    for sc in (resp.getheader("Set-Cookie") or "").split(","):
        m = re.match(r"\s*([^=;]+)=([^;]*)", sc)
        if m:
            JAR[m.group(1)] = m.group(2)
    loc = resp.getheader("Location")
    conn.close()
    return resp.status, content, loc

def get(path):
    return req("GET", path)

def post(path, body):
    return req("POST", path, body)

print("== 1. landing page ==")
s, c, _ = get("/")
check("landing loads", s == 200 and "Ledgerline" in c, f"status={s}")

print("== 2. signup ==")
import time
EMAIL = f"e2e-{int(time.time())}@example.com"
s, c, loc = post("/signup", {"name": "Test User", "email": EMAIL, "password": "secret123"})
check("signup redirects to dashboard", s in (302, 303) and loc and loc.startswith("/dashboard"), f"status={s} loc={loc}")
check("session cookie set", "ET_SESSION" in JAR)
s, c, _ = get("/dashboard")
check("dashboard greets user", s == 200 and "Test User" in c, f"status={s}")

print("== 3. add transactions ==")
s, c, loc = post("/transactions", {"type": "expense", "amount": "250", "currency": "INR",
    "date": "2026-10-10", "category_id": "", "note": "Lunch", "payment_method": "UPI"})
check("add expense redirects", s in (302, 303), f"status={s}")
s, c, loc = post("/transactions", {"type": "income", "amount": "5000", "currency": "INR",
    "date": "2026-10-11", "category_id": "", "note": "Stipend", "payment_method": "Bank transfer"})
check("add income redirects", s in (302, 303), f"status={s}")
s, c, _ = get("/transactions")
check("both transactions listed", "Lunch" in c and "Stipend" in c)

print("== 4. validation ==")
s, c, _ = post("/transactions", {"type": "expense", "amount": "abc", "currency": "INR",
    "date": "2026-10-12", "category_id": "", "note": "", "payment_method": ""})
check("bad amount re-renders form with error", s == 200 and "not a valid number" in c, f"status={s}")
s, c, _ = post("/transactions", {"type": "expense", "amount": "-5", "currency": "INR",
    "date": "2026-10-12", "category_id": "", "note": "", "payment_method": ""})
check("negative amount rejected", s == 200 and "greater than zero" in c)

print("== 5. categories ==")
s, c, _ = get("/categories")
check("default categories seeded", all(k in c for k in ["Food", "Travel", "Rent"]), f"status={s}")
s, c, loc = post("/categories", {"name": "Subscriptions", "kind": "expense"})
check("custom category created", s in (302, 303), f"status={s}")
s, c, _ = get("/categories")
check("custom category listed", "Subscriptions" in c)
m = re.search(r'name="id" value="(\d+)"', c)  # first delete button's category id

print("== 6. budgets + BudgetExceededException warning ==")
# find Food category id from the add-transaction form
s, c, _ = get("/transactions/new")
mfood = re.search(r'<option value="(\d+)">Food</option>', c)
food_id = mfood.group(1) if mfood else ""
check("food category option present", bool(food_id))
s, c, loc = post("/budgets", {"category_id": food_id, "month": "2026-10", "amount": "100"})
check("budget saved", s in (302, 303), f"status={s}")
# this expense (200) exceeds the 100 budget -> warning expected
s, c, loc = post("/transactions", {"type": "expense", "amount": "200", "currency": "INR",
    "date": "2026-10-12", "category_id": food_id, "note": "Groceries", "payment_method": "Cash"})
check("over-budget expense still saved, with warning",
      s in (302, 303) and loc and "warn" in (loc or ""), f"loc={loc}")
s, c, _ = get("/budgets?month=2026-10")
check("budget board shows over-budget state", "Over budget" in c or "budget-over" in c)

print("== 7. edit + delete + undo ==")
s, c, _ = get("/transactions")
mid = re.search(r'/transactions/(\d+)/edit', c).group(1)
s, c, _ = get(f"/transactions/{mid}/edit")
check("edit form loads", s == 200 and "Edit transaction" in c)
s, c, loc = post(f"/transactions/{mid}", {"type": "expense", "amount": "275", "currency": "INR",
    "date": "2026-10-10", "category_id": "", "note": "Lunch updated", "payment_method": "UPI"})
check("transaction updated", s in (302, 303), f"status={s}")
s, c, _ = get("/transactions")
check("updated note visible", "Lunch updated" in c)
s, c, loc = post(f"/transactions/{mid}/delete", {})
check("delete redirects", s in (302, 303), f"status={s}")
s, c, _ = get("/transactions")
check("deleted transaction gone", "Lunch updated" not in c)
s, c, loc = post("/transactions/undo", {})
check("undo redirects", s in (302, 303), f"status={s}")
s, c, _ = get("/transactions")
check("undo restored the transaction", "Lunch updated" in c)

print("== 8. search / filter / sort ==")
s, c, _ = get("/transactions?q=Groceries")
check("search finds note", "Groceries" in c and "Stipend" not in c)
s, c, _ = get("/transactions?type=income")
check("type filter works", "Stipend" in c and "Groceries" not in c)
s, c, _ = get("/transactions?sort=amount_desc")
check("sort param accepted", s == 200)

print("== 9. dashboard charts ==")
s, c, _ = get("/dashboard")
check("dashboard has donut chart", "<svg" in c and "Spending by category" in c)
check("dashboard has trend chart", "Monthly trend" in c)
check("dashboard shows totals", "Income" in c and "Balance" in c)

print("== 10. reports ==")
s, c, _ = get("/reports?from=2026-10-01&to=2026-10-31")
check("reports load", s == 200 and "Avg spend / day" in c, f"status={s}")

print("== 11. CSV export / import ==")
s, c, _ = get("/settings/export.csv")
check("csv export downloads", s == 200 and c.startswith("type,amount,currency,date,category,note,payment_method"), f"status={s}")
csv_in = "type,amount,currency,date,category,note,payment_method\nexpense,99,INR,2026-10-13,Food,CSV Test,UPI\nincome,10,USD,2026-10-13,,CSV Dollars,Card\nbadrow\n"
s, c, loc = post("/settings/import", {"csv": csv_in})
check("csv import redirects", s in (302, 303), f"status={s} loc={loc}")
s, c, _ = get("/transactions?q=CSV+Test")
check("imported row present", "CSV Test" in c)
s, c, _ = get("/transactions?q=CSV+Dollars")
check("USD row imported", "CSV Dollars" in c)

print("== 12. backup & restore ==")
s, c, loc = post("/settings/backup", {})
check("backup created", s in (302, 303) and loc and "success" in (loc or ""), f"loc={loc}")
s, c, _ = get("/settings")
m = re.search(r'name="file" value="(user_\d+_[^"]+\.ser)"', c)
check("backup listed on settings", bool(m), "no backup file found")
# delete everything, then restore
s, c, _ = get("/transactions")
ids = re.findall(r'/transactions/(\d+)/delete', c)
for tid in ids:
    post(f"/transactions/{tid}/delete", {})
s, c, _ = get("/transactions")
check("all transactions deleted", "CSV Test" not in c)
s, c, loc = post("/settings/restore", {"file": m.group(1)})
check("restore redirects", s in (302, 303), f"status={s}")
s, c, _ = get("/transactions")
check("restore brought data back", "CSV Test" in c and "Stipend" in c)

print("== 13. preferences (currency + theme) ==")
s, c, loc = post("/settings/preferences", {"currency": "USD", "theme": "dark"})
check("preferences saved", s in (302, 303), f"status={s}")
s, c, _ = get("/dashboard")
check("dashboard in dark theme", 'data-theme="dark"' in c)
check("totals shown in USD", "$" in c)

print("== 14. XSS escaping ==")
s, c, loc = post("/transactions", {"type": "expense", "amount": "10", "currency": "INR",
    "date": "2026-10-13", "category_id": "", "note": "<script>alert(1)</script>", "payment_method": ""})
s, c, _ = get("/transactions?q=%3Cscript%3E")
check("script tag escaped", "<script>alert(1)</script>" not in c and "&lt;script&gt;" in c)

print("== 15. per-user isolation ==")
JAR.clear()  # drop session
s, c, loc = get("/dashboard")
check("logged-out dashboard redirects to login", s in (302, 303) and loc and "/login" in (loc or ""), f"status={s} loc={loc}")
s, c, loc = post("/login", {"email": EMAIL, "password": "wrong"})
check("wrong password rejected", s == 200 and "Wrong email or password" in c)

print("== 16. logout ==")
post("/login", {"email": EMAIL, "password": "secret123"})
s, c, loc = get("/logout")
check("logout redirects", s in (302, 303), f"status={s}")
s, c, loc = get("/dashboard")
check("after logout, dashboard requires login", s in (302, 303), f"status={s}")

print(f"\nRESULT: {PASS} passed, {FAIL} failed")
sys.exit(1 if FAIL else 0)
