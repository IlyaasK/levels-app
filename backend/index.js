require('dotenv').config();
const express = require('express');
const axios = require('axios');
const cheerio = require('cheerio');
const cors = require('cors');
const fs = require('fs');
const path = require('path');
const { wrapper } = require('axios-cookiejar-support');
const { CookieJar } = require('tough-cookie');

const app = express();
app.use(cors());
app.use(express.json());

const dbPath = path.join(__dirname, 'db.json');

// Initialize internal cache
let statsCache = {
    bootdev: { total: 0, dailyGained: 0, minutes: 0 },
    mathacademy: { total: 0, dailyGained: 0 },
    totalDailyXp: 0,
    lastPolled: null,
    status: 'Initializing...'
};

// Safe DB reading
function readDb() {
    if (!fs.existsSync(dbPath)) return { bootdev: null, mathacademy: null };
    try {
        return JSON.parse(fs.readFileSync(dbPath, 'utf8'));
    } catch {
        return { bootdev: null, mathacademy: null };
    }
}

// Write DB
function writeDb(db) {
    fs.writeFileSync(dbPath, JSON.stringify(db, null, 2));
}

// Logic to process a newly fetched absolute XP total for Boot.dev
function processDailyStats(service, latestTotal) {
    if (latestTotal === null || latestTotal === undefined || isNaN(latestTotal)) return 0;

    let db = readDb();
    const today = new Date().toISOString().split('T')[0];
    
    if (!db[service]) {
        db[service] = { lastDate: today, startOfDayTotal: latestTotal, currentTotal: latestTotal };
    }
    
    if (db[service].lastDate !== today) {
        db[service].startOfDayTotal = db[service].currentTotal || latestTotal;
        db[service].lastDate = today;
    }
    
    db[service].currentTotal = latestTotal;
    writeDb(db);
    
    return Math.max(0, latestTotal - db[service].startOfDayTotal);
}

// Polling routines
async function scrapeBootdev() {
    const user = process.env.BOOTDEV_USERNAME;
    if (!user) throw new Error('BOOTDEV_USERNAME missing in .env');
    
    const { data } = await axios.get(`https://boot.dev/u/${user}`);
    const $ = cheerio.load(data);
    const pageText = $('body').text();
    const xpMatch = pageText.match(/([0-9,]+)\s*XP/i);
    
    if (xpMatch) return parseInt(xpMatch[1].replace(/,/g, ''), 10);
    
    const completedCourses = pageText.match(/(\d+)\s*Courses Completed/i);
    if (completedCourses) return parseInt(completedCourses[1], 10) * 1000;
    
    throw new Error('Could not extract XP from Boot.dev');
}

async function scrapeMathAcademy() {
    const email = process.env.MATHACADEMY_EMAIL;
    const password = process.env.MATHACADEMY_PASSWORD;
    if (!email || !password) throw new Error('MathAcademy credentials missing in .env');

    const jar = new CookieJar();
    const client = wrapper(axios.create({ jar, withCredentials: true }));

    const loginPage = await client.get('https://mathacademy.com/login');
    const $login = cheerio.load(loginPage.data);
    const formData = new URLSearchParams();
    
    $login('form[action="/login"] input[type="hidden"]').each((_, el) => {
        formData.append($login(el).attr('name'), $login(el).attr('value'));
    });
    formData.append('usernameOrEmail', email);
    formData.append('password', password);
    formData.append('submit', 'LOGIN');

    const loginRes = await client.post('https://mathacademy.com/login', formData.toString(), {
        headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'Referer': 'https://mathacademy.com/login' },
        maxRedirects: 5
    });

    const $dash = cheerio.load(loginRes.data);
    if ($dash('form[action="/login"]').length > 0) throw new Error('MathAcademy Login failed');

    // Natively extract the "Today" XP from the dashboard.
    let todayXp = 0;
    
    // Convert DOM to clean spaced text flow
    const pageText = $dash('body').text().replace(/\s+/g, ' ');
    
    // Look for the left sidebar "TODAY [bar] X/Y XP" or "TODAY X XP"
    const exactMatch = pageText.match(/TODAY[^0-9a-z]*(\d+)(?:\/\d+)?\s*XP/i);
    
    if (exactMatch) {
        todayXp = parseInt(exactMatch[1].replace(/,/g, ''), 10);
    } else {
        // Broad Fallback
        const fallback = pageText.match(/today[^0-9]{0,20}([0-9,]+)/i);
        if (fallback) todayXp = parseInt(fallback[1].replace(/,/g, ''), 10);
    }
    
    let totalXp = 0;
    const totalMatch = pageText.match(/TOTAL\s*EARNED[^0-9a-z]*([0-9,]+)\s*XP/i);
    if (totalMatch) {
        totalXp = parseInt(totalMatch[1].replace(/,/g, ''), 10);
    } else {
        const genericMatch = pageText.match(/([0-9,]+)\s*XP/i);
        if (genericMatch) totalXp = parseInt(genericMatch[1].replace(/,/g, ''), 10);
    }
    
    return { today: todayXp, total: totalXp };
}

async function pollStats() {
    console.log(`[${new Date().toISOString()}] Polling data...`);
    
    try {
        let bXp = 0;
        try { bXp = await scrapeBootdev(); } catch (e) { console.error(e.message); }
        const bDaily = processDailyStats('bootdev', bXp);

        let mDaily = 0;
        let mTotal = 0;
        try { 
            const mStats = await scrapeMathAcademy(); 
            mDaily = mStats.today;
            mTotal = mStats.total;
        } catch (e) { console.error(e.message); }

        const calc2Hours = mTotal > 0 ? ((15404 - mTotal) / 60).toFixed(1) : "0.0";
        const goHours = bXp > 0 ? ((1030383 - bXp) / 1800).toFixed(1) : "0.0";

        statsCache = {
            bootdev: { total: bXp, dailyGained: bDaily, minutes: (bDaily / 30).toFixed(2), goHours },
            mathacademy: { total: mTotal, dailyGained: mDaily, calc2Hours },
            totalDailyXp: bDaily + mDaily,
            lastPolled: new Date().toISOString(),
            status: 'Active'
        };
        console.log(`[${new Date().toISOString()}] Polled successfully.`);
    } catch (err) {
        console.error("Critical poll error:", err);
        statsCache.status = 'Error during polling';
    }
}

// Endpoint to retrieve stats
app.get('/api/stats', (req, res) => res.json(statsCache));
app.post('/api/force-poll', async (req, res) => {
    await pollStats();
    res.json(statsCache);
});

// Run hourly (3600000 ms)
setInterval(pollStats, 3600000);
// Initial poll
pollStats();

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => console.log(`Backend hourly polling agent active on port ${PORT}`));
