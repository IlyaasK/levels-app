const r = /TOTAL\s*EARNED[^0-9a-z]*(\d{1,3}(?:,\d{3})*)\s*XP/i;
console.log(r.exec("TOTAL EARNED 9071 XP"));
