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

---

## Auto-Updating the Backend (CI/CD)

We have included a GitHub Action (`deploy-backend.yml`) that will automatically SSH into your VPS, pull the latest code, recompile the Go binary, and silently restart it whenever you push changes to the `backend-go/` folder! 

To enable this:
1. Go to your GitHub Repository -> **Settings** -> **Secrets and variables** -> **Actions**
2. Add the following **New repository secrets**:
   - `VPS_HOST`: Your server IP (e.g. `77.42.82.186`)
   - `VPS_USER`: Your SSH username (e.g. `root` or `ubuntu`)
   - `VPS_SSH_KEY`: The raw text of your private SSH key (e.g. `~/.ssh/id_rsa`) that you use to log into the VPS.
3. Make sure the absolute path inside the `.github/workflows/deploy-backend.yml` script (`cd ~/levels-app/backend-go`) matches exactly where you originally cloned it on your server.
