require('dotenv').config({ path: __dirname + '/.env' });
const axios = require('axios');
const cheerio = require('cheerio');
const { wrapper } = require('axios-cookiejar-support');
const { CookieJar } = require('tough-cookie');

async function scrape() {
    const email = process.env.MATHACADEMY_EMAIL;
    const password = process.env.MATHACADEMY_PASSWORD;
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
    const pageText = $dash('body').text().replace(/\s+/g, ' ');
    console.log(pageText);
}
scrape();
