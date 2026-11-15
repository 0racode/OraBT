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
    page.goto(SITE + "/login", wait_until="domcontentloaded", timeout=60000); time.sleep(6)
    print("LOGIN PAGE TEXT:", re.sub(r"\s+", " ", page.inner_text("body"))[:500])
    print("BUTTONS:", page.eval_on_selector_all("button,a[role=button],[role=button]", "e => e.map(x => (x.innerText||'').trim().slice(0,40))"))
    print("INPUTS:", page.eval_on_selector_all("input", "e => e.map(x => x.type+'/'+x.name+'/'+x.placeholder)"))
    try:
        page.get_by_text("Continue with Email").first.click(timeout=8000); time.sleep(2)
    except Exception as e:
        print("no email button click:", str(e)[:100])
    print("INPUTS AFTER:", page.eval_on_selector_all("input", "e => e.map(x => x.type+'/'+x.name+'/'+x.placeholder)"))
    page.locator("input[type=email], input[name=email]").first.fill(EMAIL)
    pw = page.locator("input[type=password]").first
    pw.fill(PW)
    page.get_by_role("button", name=re.compile(r"sign in|log in|continue", re.I)).last.click()
    time.sleep(10)
    print("AFTER LOGIN URL:", page.url)
    txt = page.inner_text("body")
    print("TEXT:", mask(re.sub(r"\s+", " ", txt))[:600])
    for path in PATHS:
        time.sleep(5)
        seen.clear()
        page.goto(SITE + path, wait_until="domcontentloaded", timeout=60000); time.sleep(8)
        print("\n##### ", path, "->", page.url)
        txt = page.inner_text("body")
        print("TEXT (first 3500 chars):", mask(re.sub(r"[ \t]+", " ", txt))[:3500])
        links = page.eval_on_selector_all("a[href]", "e => e.map(x => x.getAttribute('href'))")
        print("LINKS:", sorted({l for l in links if l.startswith('/') and not l.startswith('/_next')})[:80])
        print("DATA CALLS:")
        for t, m, s, u in seen[:25]:
            print("  ", m, s, re.sub(r"[?&](token|key|sig)[^&]*", "", u)[:200])
    b.close()
