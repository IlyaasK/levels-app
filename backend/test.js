const puppeteer = require('puppeteer');

(async () => {
    let browser;
    try {
        browser = await puppeteer.launch({ 
            headless: true,
            args: ['--no-sandbox', '--disable-setuid-sandbox']
        });
        const page = await browser.newPage();
        await page.goto(`https://boot.dev/u/ilyaask`, { waitUntil: 'networkidle2' });
        
        console.log("Page loaded. Evaluating...");
        
        const data = await page.evaluate(() => {
            const results = {};
            const elements = Array.from(document.querySelectorAll('*'));
            for (let el of elements) {
                if (el.textContent && el.textContent.includes('XP') && el.children.length === 0) {
                    results.xpToken = el.textContent.trim();
                }
            }
            // Better selector: usually there is a specific class or structure for XP.
            const stats = document.body.innerText.match(/(\d{1,3}(?:,\d{3})*)\s*XP/i);
            if (stats) results.regexXp = stats[1];
            
            return results;
        });
        
        console.log("Extracted Data:", data);

    } catch (err) {
        console.error("Boot.dev scrape error:", err);
    } finally {
        if (browser) await browser.close();
    }
})();
