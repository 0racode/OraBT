#!/usr/bin/env bash
# Read-only look at a site. Gentle: one request every 3 seconds, honest user agent.
set -u
UA="PastQ-CBT-research (free educational app; contact via github.com/0racode/OraBT)"
get() { sleep 3; curl -sS -L --max-time 30 -A "$UA" -w '\n[HTTP %{http_code} %{content_type} %{size_download} bytes]\n' "$1"; }
echo "##### robots.txt"; get "$SITE/robots.txt" | head -80
echo; echo "##### homepage: title and internal links"
sleep 3; curl -sS -L --max-time 30 -A "$UA" "$SITE/" -o home.html -w '[HTTP %{http_code} %{size_download} bytes]\n'
grep -o '<title>[^<]*' home.html | head -2
grep -oE 'href="[^"#]+"' home.html | sed 's/href=//' | tr -d '"' | sort -u | head -80
echo; echo "##### terms-like pages"
for p in terms terms-of-use terms-and-conditions terms-conditions privacy copyright disclaimer about; do
  code=$(sleep 3; curl -s -o "t_$p.html" -L --max-time 30 -A "$UA" -w '%{http_code}' "$SITE/$p")
  echo "$p -> $code"
  if [ "$code" = "200" ]; then sed -e 's/<script[^>]*>.*<\/script>//g' -e 's/<[^>]*>/ /g' "t_$p.html" | tr -s ' \n' | head -c 2500; echo; fi
done
for p in $PATHS; do
  echo; echo "##### extra path $p"; sleep 3
  curl -sS -L --max-time 30 -A "$UA" "$SITE$p" -o x.html -w '[HTTP %{http_code} %{size_download} bytes]\n'
  grep -o '<title>[^<]*' x.html | head -1
  head -c 6000 x.html
done
