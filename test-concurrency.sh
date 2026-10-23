#!/usr/bin/env bash
set -e

BASE_URL="http://localhost:8080/api/v1"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${YELLOW}=== ۱. ایجاد حساب‌های تستی اولیه از طریق REST API ===${NC}"
curl -s -o /dev/null -X POST "$BASE_URL/accounts" -H "Content-Type: application/json" -d '{"accountId":"ACC-TEST-A","initialBalance":10000}' || true
curl -s -o /dev/null -X POST "$BASE_URL/accounts" -H "Content-Type: application/json" -d '{"accountId":"ACC-TEST-B","initialBalance":10000}' || true
curl -s -o /dev/null -X POST "$BASE_URL/accounts" -H "Content-Type: application/json" -d '{"accountId":"ACC-IDEMP","initialBalance":1000}' || true
curl -s -o /dev/null -X POST "$BASE_URL/accounts" -H "Content-Type: application/json" -d '{"accountId":"ACC-DEBIT-RACE","initialBalance":1000}' || true

echo -e "${GREEN}حساب‌ها با موفقیت مقداردهی شدند.${NC}"

echo -e "\n${YELLOW}=== ۲. تست Idempotency تحت ۵۰ درخواست موازی همزمان ===${NC}"
IDEMP_TX="TX-IDEMP-$(date +%s%N)"
for i in {1..50}; do
  curl -s -o /dev/null -X POST "$BASE_URL/accounts/ACC-IDEMP/credit" \
       -H "Content-Type: application/json" \
       -d "{\"amount\": 500, \"transactionId\": \"$IDEMP_TX\"}" &
done
wait

BAL_IDEMP=$(curl -s "$BASE_URL/accounts/ACC-IDEMP/balance" | grep -o '"balance":[0-9]*' | cut -d: -f2)
if [ "$BAL_IDEMP" -eq 1500 ]; then
  echo -e "${GREEN}✓ Idempotency موفق: موجودی نهایی دقیقاً 1500 شد (اثر تراکنش تنها ۱ بار اعمال شد).${NC}"
else
  echo -e "${RED}✗ خطای Idempotency: موجودی نهایی $BAL_IDEMP است!${NC}"
fi

echo -e "\n${YELLOW}=== ۳. تست رقابت همزمان برای برداشت وجه (Debit Race Condition) ===${NC}"
for i in {1..20}; do
  curl -s -o /dev/null -X POST "$BASE_URL/accounts/ACC-DEBIT-RACE/debit" \
       -H "Content-Type: application/json" \
       -d "{\"amount\": 100, \"transactionId\": \"TX-DEB-$i-$(date +%s%N)\"}" &
done
wait

BAL_DEBIT=$(curl -s "$BASE_URL/accounts/ACC-DEBIT-RACE/balance" | grep -o '"balance":[0-9]*' | cut -d: -f2)
if [ "$BAL_DEBIT" -eq 0 ]; then
  echo -e "${GREEN}✓ Concurrency Debit موفق: موجودی نهایی 0 شد و موجودی منفی رخ نداد.${NC}"
else
  echo -e "${RED}✗ خطای Debit: موجودی نهایی $BAL_DEBIT است!${NC}"
fi

echo -e "\n${YELLOW}=== ۴. تست انتقال دوطرفه همزمان بدون بن‌بست (Deadlock Prevention) ===${NC}"
for i in {1..50}; do
  curl -s -o /dev/null -X POST "$BASE_URL/transfers" \
       -H "Content-Type: application/json" \
       -d "{\"sourceAccountId\": \"ACC-TEST-A\", \"destinationAccountId\": \"ACC-TEST-B\", \"amount\": 50, \"transactionId\": \"TX-A2B-$i-$(date +%s%N)\"}" &

  curl -s -o /dev/null -X POST "$BASE_URL/transfers" \
       -H "Content-Type: application/json" \
       -d "{\"sourceAccountId\": \"ACC-TEST-B\", \"destinationAccountId\": \"ACC-TEST-A\", \"amount\": 50, \"transactionId\": \"TX-B2A-$i-$(date +%s%N)\"}" &
done
wait

BAL_A=$(curl -s "$BASE_URL/accounts/ACC-TEST-A/balance" | grep -o '"balance":[0-9]*' | cut -d: -f2)
BAL_B=$(curl -s "$BASE_URL/accounts/ACC-TEST-B/balance" | grep -o '"balance":[0-9]*' | cut -d: -f2)
TOTAL=$((BAL_A + BAL_B))

if [ "$TOTAL" -eq 20000 ] && [ "$BAL_A" -eq 10000 ] && [ "$BAL_B" -eq 10000 ]; then
  echo -e "${GREEN}✓ انتقال همزمان موفق: بن‌بست رخ نداد و پایستگی مجموع سرمایه تضمین شد ($TOTAL).${NC}"
else
  echo -e "${RED}✗ خطای Transfer: A=$BAL_A, B=$BAL_B, Total=$TOTAL${NC}"
fi

echo -e "\n${YELLOW}=== ۵. بررسی تاریخچه تراکنش‌های حساب (Transaction History) ===${NC}"
TX_COUNT=$(curl -s "$BASE_URL/accounts/ACC-TEST-A/transactions" | grep -o '"transactionId"' | wc -l)
echo -e "${GREEN}✓ تعداد تراکنش‌های ثبت‌شده برای حساب ACC-TEST-A: $TX_COUNT رکورد.${NC}"
