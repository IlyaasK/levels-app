# Levels Tracker App

This project consists of an Android Homescreen Widget and a Go-based backend scraper to track your lifetime learning progress continuously, even from low-resource VPS nodes.

## Android App (Widget)
A native Android widget is provided to display your daily stats alongside your hours-remaining for major milestones (Go and Calc2).

**Building the APK without Android Studio:**
We've provided a GitHub Actions workflow. Simply commit everything to `main` and look in your repo's Actions tab. Upon completion, you can download the `.apk` directly onto your phone!

*(Remember to edit `API_URL` inside `android/app/src/main/java/com/levelsapp/StatsWidgetProvider.kt` to point to your VPS IP before committing!)*

---

## Backend Deployment Instructions (Cheap Linux VPS)

The Node.js backend has been completely rewritten into a lightweight Go application located in `backend-go/`. It consumes less than 20MB of RAM and compiles to a single executable binary, making it perfectly suited for extreme entry-level VPS computing.

**1. Clone the repository onto your VPS:**
```bash
git clone <your-repo-url>
cd levels-app/backend-go
```

**2. Setup your environment variables:**
Create a `.env` file inside `backend-go` with your credentials:
```env
BOOTDEV_USERNAME=your_username
MATHACADEMY_EMAIL=your_email
MATHACADEMY_PASSWORD=your_password
PORT=3000
```

**3. Install Go (Skip if already installed)**
On Ubuntu/Debian:
```bash
sudo apt update && sudo apt install golang -y
```

**4. Compile the binary:**
```bash
go build -o levels-backend
```

**5. Start the backend in the background:**
```bash
nohup ./levels-backend >/dev/null 2>&1 &
```

> **Pro-Tip**: Since it’s a compiled binary, you don’t need NPM, Node.js, pm2, or containerization. It will run endlessly and natively update your database file (`backend-go/db.json`).

Your server is now actively polling hourly and serving stats for your Android widget via `http://YOUR_VPS_IP:3000/api/stats`!
