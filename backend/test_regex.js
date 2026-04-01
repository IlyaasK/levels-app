const pageText = "AP Calculus BC 20% Estimated completion is July, 2050 TOTAL EARNED 9071 XP Today 1/100 XP This Week 1 XP";
const totalMatch = pageText.match(/TOTAL\s*EARNED[^0-9a-z]*(\d{1,3}(?:,\d{3})*)\s*XP/i);
console.log("totalMatch:", totalMatch);

const fallback = pageText.match(/(\d{1,3}(?:,\d{3})*)\s*XP/i);
console.log("fallback:", fallback);
