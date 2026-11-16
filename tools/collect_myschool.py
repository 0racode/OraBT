#!/usr/bin/env python3
"""Polite collector for myschool.ng past questions (permission: written, free educational app, credit + link).

Reads the public question pages one at a time, with a delay between requests and an honest User-Agent.
Writes JSON lines to out/myschool.jsonl. Keeps the picture URL of a question (field "image") when the picture sits
between the question and its options; skips questions whose options are pictures. Stops after MAX_MINUTES.
IDS_FILE (lines "subject id") collects just those questions and writes only the ones that have a picture.
"""
import json, os, re, sys, time, urllib.parse
import requests
from bs4 import BeautifulSoup

SITE = "https://myschool.ng"
UA = "PastQ-CBT-research (free educational app; contact via github.com/0racode/OraBT)"
DELAY = float(os.environ.get("DELAY", "3.5"))
MAX_MINUTES = float(os.environ.get("MAX_MINUTES", "300"))
IDS_FILE = os.environ.get("IDS_FILE", "")
SUBJECTS = os.environ.get("SUBJECTS", "").split()
TYPES = os.environ.get("EXAM_TYPES", "jamb").split()
YEARS = os.environ.get("YEARS", "").split()
MAX_PAGES = int(os.environ.get("MAX_PAGES", "40"))
START = time.time()
S = requests.Session(); S.headers["User-Agent"] = UA
last = [0.0]
stats = {"with_image": 0, "classroom_imgs_on_pages": 0, "requests": 0, "questions": 0, "skipped_image": 0, "skipped_options": 0, "errors": 0}

def out_of_time():
    return (time.time() - START) / 60 > MAX_MINUTES

def get(url):
    wait = DELAY - (time.time() - last[0])
    if wait > 0: time.sleep(wait)
    for attempt in range(4):
        try:
            last[0] = time.time(); stats["requests"] += 1
            r = S.get(url, timeout=40)
            if r.status_code == 200: return r.text
            if r.status_code in (404, 410): return None
            print(f"  HTTP {r.status_code} for {url}", flush=True)
        except requests.RequestException as e:
            print(f"  error {e} for {url}", flush=True)
        stats["errors"] += 1
        time.sleep(10 * (attempt + 1))
    return None

def list_ids(subject, etype, year):
    ids, seen = [], set()
    for page in range(1, MAX_PAGES + 1):
        q = {"exam_type": etype, "exam_year": year}
        if page > 1: q["page"] = page
        html = get(f"{SITE}/classroom/{subject}?{urllib.parse.urlencode(q)}")
        if html is None: break
        new = [i for i in re.findall(rf'/classroom/{re.escape(subject)}/(\d+)\?', html) if i not in seen]
        if not new: break
        for i in new: seen.add(i); ids.append(i)
        if out_of_time(): break
    return ids

def find_images(soup):
    """Picture(s) belonging to the question: stored under /storage/classroom/ and placed before the first option."""
    first = None
    for sp in soup.select("span.uppercase.font-medium"):
        if sp.get_text(strip=True).lower() == "a": first = sp; break
    found = list(reversed(first.find_all_previous("img"))) if first else soup.find_all("img")
    urls = []
    for im in found:
        m = re.search(r"(?:https://myschool\.ng)?/storage/classroom/[^\s,\"']+", (im.get("src") or "") + " " + (im.get("srcset") or ""))
        if m:
            u = urllib.parse.urljoin(SITE, m.group(0))
            if u not in urls: urls.append(u)
    return urls

def parse(html, subject, qid):
    soup = BeautifulSoup(html, "html.parser")
    h1 = soup.find("h1")
    if not h1: return None
    if h1.find("img") or "<img" in str(h1): stats["skipped_image"] += 1; return None
    stem = h1.get_text("\n", strip=True)
    opts = {}
    for sp in soup.select("span.uppercase.font-medium"):
        letter = sp.get_text(strip=True).lower()
        p = sp.find_next("p")
        if letter in "abcde" and len(letter) == 1 and p:
            if p.find("img"): stats["skipped_image"] += 1; return None
            opts.setdefault(letter, p.get_text(" ", strip=True))
    co = soup.find(string=re.compile(r"Correct Option"))
    ans = None
    if co:
        el = co.find_parent("div")
        sp = el.find_next("span", class_=re.compile("uppercase")) if el else None
        ans = sp.get_text(strip=True).lower() if sp else None
    tag = soup.find("a", href=re.compile(r"exam_type=.*exam_year="))
    etype = year = ""
    if tag:
        m = re.search(r"exam_type=([a-z\-]+).*exam_year=(\d+)", tag["href"])
        if m: etype, year = m.group(1), m.group(2)
    exp = ""
    for p in soup.select("p.text-tx_secondary"):
        t = p.get_text(" ", strip=True)
        if t: exp = t; break
    if not stem or ans not in list(opts) or len(opts) < 4:
        return None
    if len(opts) > 5 or any(l not in "abcde" for l in opts):
        stats["skipped_options"] += 1; return None
    pics = find_images(soup)
    stats["classroom_imgs_on_pages"] += len([1 for im in soup.find_all("img") if "/storage/classroom/" in ((im.get("src") or "") + (im.get("srcset") or ""))])
    if pics: stats["with_image"] += 1
    return dict(src="myschool", src_id=qid, subject=subject, exam_type=etype, year=year, question=stem,
                a=opts["a"], b=opts["b"], c=opts["c"], d=opts["d"], e=opts.get("e", ""), answer=ans, explanation=exp,
                image="\n".join(pics))

os.makedirs("out", exist_ok=True)
done = set()
if os.path.exists("out/myschool.jsonl"):
    for line in open("out/myschool.jsonl"): done.add(json.loads(line)["src_id"])
with open("out/myschool.jsonl", "a") as f:
    if IDS_FILE:
        pairs = [l.split() for l in open(IDS_FILE) if len(l.split()) == 2]
        for subject, qid in pairs:
            if qid in done or out_of_time(): continue
            html = get(f"{SITE}/classroom/{subject}/{qid}")
            rec = parse(html, subject, qid) if html else None
            if rec and rec["image"]:
                f.write(json.dumps(rec, ensure_ascii=False) + "\n"); f.flush()
                stats["questions"] += 1; done.add(qid)
    else:
      for subject in SUBJECTS:
        for etype in TYPES:
            for year in YEARS:
                if out_of_time(): break
                ids = list_ids(subject, etype, year)
                print(f"{subject} {etype} {year}: {len(ids)} questions listed", flush=True)
                for qid in ids:
                    if qid in done or out_of_time(): continue
                    html = get(f"{SITE}/classroom/{subject}/{qid}")
                    rec = parse(html, subject, qid) if html else None
                    if rec:
                        f.write(json.dumps(rec, ensure_ascii=False) + "\n"); f.flush()
                        stats["questions"] += 1; done.add(qid)
print("DONE", json.dumps(stats), f"{(time.time()-START)/60:.1f} min", flush=True)
