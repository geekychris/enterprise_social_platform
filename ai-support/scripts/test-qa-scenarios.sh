#!/bin/bash
###############################################################################
# AI Support QA Test Suite
# Tests question answering across all knowledge sets
###############################################################################

BASE="${1:-http://localhost:8090}"
PASS=0
FAIL=0
TOTAL=0

GREEN="\033[0;32m"
RED="\033[0;31m"
CYAN="\033[0;36m"
BOLD="\033[1m"
RESET="\033[0m"

section() { echo ""; echo -e "${BOLD}${CYAN}═══ $1 ═══${RESET}"; }
test_name() { TOTAL=$((TOTAL+1)); echo -n "  [$TOTAL] $1 ... "; }
pass() { PASS=$((PASS+1)); echo -e "${GREEN}PASS${RESET}"; }
fail() { FAIL=$((FAIL+1)); echo -e "${RED}FAIL${RESET} ($1)"; }

ask() {
  local ks_id=$1
  local question=$2
  curl -s -H "Content-Type: application/json" \
    -d "{\"knowledgeSetId\":$ks_id,\"question\":\"$question\"}" \
    "$BASE/api/qa/ask"
}

check_answer() {
  local result="$1"
  local expected_words="$2"
  local min_confidence="${3:-0.5}"

  local confidence=$(echo "$result" | python3 -c "import sys,json; print(json.load(sys.stdin).get('confidence',0))" 2>/dev/null)
  local answer=$(echo "$result" | python3 -c "import sys,json; print(json.load(sys.stdin).get('answer','').lower())" 2>/dev/null)

  # Check confidence
  if python3 -c "exit(0 if $confidence >= $min_confidence else 1)" 2>/dev/null; then
    # Check expected words
    local found=true
    for word in $expected_words; do
      if ! echo "$answer" | grep -qi "$word"; then
        found=false
        break
      fi
    done
    if [ "$found" = true ]; then
      echo -n "(conf=${confidence}) "
      pass
      return 0
    else
      fail "expected words '$expected_words' not in answer"
      return 1
    fi
  else
    fail "confidence ${confidence} below ${min_confidence}"
    return 1
  fi
}

section "API Health"

test_name "AI Support app is running"
HEALTH=$(curl -s "$BASE/api/health" 2>&1)
if echo "$HEALTH" | grep -q '"status":"UP"'; then
  pass
else
  fail "app not running"
  echo "Cannot continue - app is not available"
  exit 1
fi

test_name "Ollama is available"
if echo "$HEALTH" | grep -q '"ollamaAvailable":true'; then
  pass
else
  fail "ollama offline"
  echo "Cannot continue - Ollama needed for QA"
  exit 1
fi

test_name "Knowledge sets exist"
SETS=$(curl -s "$BASE/api/knowledge/sets" | python3 -c "import sys,json; print(len(json.load(sys.stdin)))" 2>/dev/null)
if [ "$SETS" -ge 4 ] 2>/dev/null; then
  echo -n "($SETS sets) "
  pass
else
  fail "expected >=4 sets, got $SETS"
fi

section "Amiga Knowledge (KS 1)"

test_name "What models of Amiga exist?"
RESULT=$(ask 1 "What different Amiga models are there?")
check_answer "$RESULT" "a500 a1200" 0.5

test_name "How to fix capacitors?"
RESULT=$(ask 1 "How do I replace bad capacitors on my Amiga?")
check_answer "$RESULT" "capacitor" 0.6

test_name "What is WinUAE?"
RESULT=$(ask 1 "What is WinUAE and how do I use it?")
check_answer "$RESULT" "emulat" 0.5

test_name "How to add memory?"
RESULT=$(ask 1 "How can I upgrade the RAM on my Amiga 500?")
check_answer "$RESULT" "memory" 0.5

test_name "What is Deluxe Paint?"
RESULT=$(ask 1 "What is Deluxe Paint?")
check_answer "$RESULT" "paint" 0.5

section "Gowin FPGA Knowledge (KS 2)"

test_name "What is Gowin FPGA?"
RESULT=$(ask 2 "What is a Gowin FPGA?")
check_answer "$RESULT" "fpga" 0.5

test_name "How to program Tang Nano 9K?"
RESULT=$(ask 2 "How do I program the Tang Nano 9K board?")
check_answer "$RESULT" "tang" 0.5

test_name "What is a CST file?"
RESULT=$(ask 2 "What is a CST pin constraint file?")
check_answer "$RESULT" "pin" 0.5

section "Atari 8-bit Knowledge (KS 3)"

test_name "What Atari models exist?"
RESULT=$(ask 3 "What Atari 8-bit computer models are there?")
check_answer "$RESULT" "800" 0.5

test_name "What is FujiNet?"
RESULT=$(ask 3 "What is FujiNet?")
check_answer "$RESULT" "fujinet" 0.5

test_name "How to connect to modern TV?"
RESULT=$(ask 3 "How do I connect my Atari to a modern TV?")
check_answer "$RESULT" "video" 0.4

section "Cross-Knowledge-Set Routing"

test_name "Route Amiga question to correct KS"
ROUTE=$(curl -s -H "Content-Type: application/json" \
  -d '{"question":"How do I install a PiStorm in my Amiga 500?"}' \
  "$BASE/api/qa/route")
BEST_KS=$(echo "$ROUTE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d[0].get('name','') if d else '')" 2>/dev/null)
if echo "$BEST_KS" | grep -qi "amiga"; then
  pass
else
  fail "expected Amiga, got '$BEST_KS'"
fi

test_name "Route FPGA question to correct KS"
ROUTE=$(curl -s -H "Content-Type: application/json" \
  -d '{"question":"How do I synthesize a Verilog design for the Tang Nano?"}' \
  "$BASE/api/qa/route")
BEST_KS=$(echo "$ROUTE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d[0].get('name','') if d else '')" 2>/dev/null)
if echo "$BEST_KS" | grep -qi "gowin\|fpga"; then
  pass
else
  fail "expected Gowin FPGA, got '$BEST_KS'"
fi

test_name "Route Atari question to correct KS"
ROUTE=$(curl -s -H "Content-Type: application/json" \
  -d '{"question":"How do I use SIO2SD with my Atari 800XL?"}' \
  "$BASE/api/qa/route")
BEST_KS=$(echo "$ROUTE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d[0].get('name','') if d else '')" 2>/dev/null)
if echo "$BEST_KS" | grep -qi "atari"; then
  pass
else
  fail "expected Atari, got '$BEST_KS'"
fi

section "Search APIs"

test_name "Lexical search works"
RESULT=$(curl -s "$BASE/api/search/lexical/1?q=capacitor&topK=3")
COUNT=$(echo "$RESULT" | python3 -c "import sys,json; print(len(json.load(sys.stdin)))" 2>/dev/null)
if [ "$COUNT" -ge 1 ] 2>/dev/null; then
  echo -n "($COUNT results) "
  pass
else
  fail "no results"
fi

test_name "Semantic search works"
RESULT=$(curl -s "$BASE/api/search/semantic/1?q=how+to+fix+audio+problems&topK=3")
COUNT=$(echo "$RESULT" | python3 -c "import sys,json; print(len(json.load(sys.stdin)))" 2>/dev/null)
if [ "$COUNT" -ge 1 ] 2>/dev/null; then
  echo -n "($COUNT results) "
  pass
else
  fail "no results"
fi

test_name "Hybrid search works"
RESULT=$(curl -s "$BASE/api/search/hybrid/2?q=tang+nano&topK=3")
if echo "$RESULT" | grep -q '"lexical"'; then
  pass
else
  fail "unexpected response format"
fi

section "Solutions Queue"

test_name "Solutions stats endpoint"
RESULT=$(curl -s "$BASE/api/solutions/stats")
if echo "$RESULT" | grep -q '"pending"'; then
  pass
else
  fail "unexpected response"
fi

# Summary
echo ""
echo -e "${BOLD}${CYAN}═══════════════════════════════════════${RESET}"
echo -e "${BOLD}${CYAN}  TEST SUMMARY${RESET}"
echo -e "${BOLD}${CYAN}═══════════════════════════════════════${RESET}"
echo ""
echo -e "  Total:  ${BOLD}$TOTAL${RESET}"
echo -e "  ${GREEN}Passed: $PASS${RESET}"
echo -e "  ${RED}Failed: $FAIL${RESET}"
echo ""
if [ $FAIL -eq 0 ]; then
  echo -e "  ${GREEN}${BOLD}All tests passed!${RESET}"
else
  echo -e "  ${RED}${BOLD}$FAIL test(s) failed.${RESET}"
fi
