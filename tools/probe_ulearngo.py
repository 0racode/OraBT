#!/usr/bin/env python3
"""Read-only look at app.ulearngo.com with the owner's own account (written permission from Ulearngo, web app only).
Signs in, opens ONE subject page and reports how it is laid out. Copies no questions. Slow (5 s between page loads)."""
import os, re, sys, time
from playwright.sync_api import sync_playwright

SITE = "https://app.ulearngo.com"
EMAIL, PW = os.environ["ULEARNGO_EMAIL"], os.environ["ULEARNGO_PASSWORD"]
PATHS = (os.environ.get("PATHS") or "/past-questions/biology").split()
seen = []

def mask(t): return t.replace(EMAIL, "<email>").replace(PW, "<password>")

with sync_playwright() as p:
    b = p.chromium.launch()
    ctx = b.new_context(user_agent="PastQ-CBT-research (free educational app; contact via github.com/0racode/OraBT)")
    page = ctx.new_page()
    page.on("response", lambda r: seen.append((r.request.resource_type, r.request.method, r.status, r.url)) if r.request.resource_type in ("xhr", "fetch") else None)
    page.goto(SITE + "/login", wait_until="networkidle"); time.sleep(3)
    page.get_by_text("Continue with Email").first.click(); time.sleep(2)
    page.locator("input[type=email], input[name=email]").first.fill(EMAIL)
    pw = page.locator("input[type=password]").first
    pw.fill(PW)
    page.get_by_role("button", name=re.compile(r"sign in|log in|continue", re.I)).last.click()
    page.wait_for_load_state("networkidle"); time.sleep(5)
    print("AFTER LOGIN URL:", page.url)
    txt = page.inner_text("body")
    print("TEXT:", mask(re.sub(r"\s+", " ", txt))[:600])
    for path in PATHS:
        time.sleep(5)
        seen.clear()
        page.goto(SITE + path, wait_until="networkidle"); time.sleep(3)
        print("\n##### ", path, "->", page.url)
        txt = page.inner_text("body")
        print("TEXT (first 3500 chars):", mask(re.sub(r"[ \t]+", " ", txt))[:3500])
        links = page.eval_on_selector_all("a[href]", "e => e.map(x => x.getAttribute('href'))")
        print("LINKS:", sorted({l for l in links if l.startswith('/') and not l.startswith('/_next')})[:80])
        print("DATA CALLS:")
        for t, m, s, u in seen[:25]:
            print("  ", m, s, re.sub(r"[?&](token|key|sig)[^&]*", "", u)[:200])
    b.close()
