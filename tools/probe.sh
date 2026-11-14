#!/usr/bin/env bash
# Read-only look at a site. Gentle: one request every 3 seconds, honest user agent.
set -u
UA="PastQ-CBT-research (free educational app; contact via github.com/0racode/OraBT)"
strip() { sed -e 's/<script[^>]*>[^<]*<\/script>//g' -e 's/<style[^>]*>[^<]*<\/style>//g' -e 's/<[^>]*>/ /g' "$1" | tr -s ' \t\n' ' '; }
for p in $PATHS; do
  echo; echo "##### $p"; sleep 3
  curl -sS -L --max-time 40 -A "$UA" "$SITE$p" -o x.html -w '[HTTP %{http_code} %{content_type} %{size_download} bytes]\n'
  grep -o '<title>[^<]*' x.html | head -1
  echo "--- text (first ${TEXTLEN:-3500} chars)"; strip x.html | head -c "${TEXTLEN:-3500}"; echo
  echo "--- internal links"; grep -oE 'href="/[^"#]*"' x.html | sed 's/href=//' | tr -d '"' | grep -v '^/_nuxt' | sort -u | head -60
  if [ -n "${DUMP_NUXT:-}" ]; then
    echo "--- embedded data (__NUXT_DATA__ / window.__NUXT__), first ${NUXTLEN:-5000} chars"
    python3 - <<'PY'
import re,os
h=open('x.html',encoding='utf-8',errors='replace').read()
m=re.search(r'<script[^>]*id="__NUXT_DATA__"[^>]*>(.*?)</script>',h,re.S) or re.search(r'window\.__NUXT__\s*=\s*(.*?)</script>',h,re.S)
print((m.group(1) if m else 'none found')[:int(os.environ.get('NUXTLEN','5000'))])
PY
  fi
  if [ -n "${SNIPPET:-}" ]; then
    echo "--- raw HTML around the answer (svg removed)"
    python3 - <<'PY'
import re
h=open('x.html',encoding='utf-8',errors='replace').read()
h=re.sub(r'<svg.*?</svg>','',h,flags=re.S); h=re.sub(r'<path[^>]*>','',h)
import os
needle=os.environ.get('NEEDLE','Correct Option')
i=h.find(needle)
print(h[max(0,i-int(os.environ.get('BEFORE','600'))):i+int(os.environ.get('AFTER','2600'))] if i>=0 else needle+' not found')
PY
  fi
  echo "--- api-like strings"; grep -oE 'https?://[a-zA-Z0-9./_-]*(api|json|graphql)[a-zA-Z0-9./_?=&-]*' x.html | sort -u | head -15
done
