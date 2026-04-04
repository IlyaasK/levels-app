package main

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"net/http/cookiejar"
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/PuerkitoBio/goquery"
	"github.com/joho/godotenv"
)

type DBState struct {
	Bootdev     *ServiceState `json:"bootdev"`
	Mathacademy *ServiceState `json:"mathacademy"`
}

type ServiceState struct {
	LastDate        string `json:"lastDate"`
	StartOfDayTotal int    `json:"startOfDayTotal"`
	CurrentTotal    int    `json:"currentTotal"`
}

type StatsBootdev struct {
	Total       int    `json:"total"`
	DailyGained int    `json:"dailyGained"`
	Minutes     string `json:"minutes"`
	GoHours     string `json:"goHours"`
}

type StatsMathacademy struct {
	Total       int    `json:"total"`
	DailyGained int    `json:"dailyGained"`
	Calc2Hours  string `json:"calc2Hours"`
}

type StatsCache struct {
	Bootdev      *StatsBootdev     `json:"bootdev"`
	Mathacademy  *StatsMathacademy `json:"mathacademy"`
	TotalDailyXp int               `json:"totalDailyXp"`
	LastPolled   string            `json:"lastPolled"`
	Status       string            `json:"status"`
}

var (
	cacheMutex sync.RWMutex
	statsCache = StatsCache{
		Status: "Initializing...",
	}
	dbPath string
)

func init() {
	err := godotenv.Load("../backend/.env")
	if err != nil {
		godotenv.Load(".env")
	}

	ex, err := os.Executable()
	if err != nil {
		panic(err)
	}
	exPath := filepath.Dir(ex)
	dbPath = filepath.Join(exPath, "db.json")

	// If running with go run, put db payload in current dir
	if strings.Contains(exPath, "go-build") || strings.Contains(exPath, "Temp") {
		cwd, _ := os.Getwd()
		dbPath = filepath.Join(cwd, "db.json")
	}
}

func readDb() DBState {
	var db DBState
	data, err := os.ReadFile(dbPath)
	if err != nil {
		return db
	}
	json.Unmarshal(data, &db)
	return db
}

func writeDb(db DBState) {
	data, _ := json.MarshalIndent(db, "", "  ")
	os.WriteFile(dbPath, data, 0644)
}

func processDailyStats(service string, latestTotal int) int {
	if latestTotal <= 0 {
		return 0
	}

	db := readDb()
	today := time.Now().Format("2006-01-02")

	var state *ServiceState
	switch service {
	case "bootdev":
		if db.Bootdev == nil {
			db.Bootdev = &ServiceState{}
		}
		state = db.Bootdev
	case "mathacademy":
		if db.Mathacademy == nil {
			db.Mathacademy = &ServiceState{}
		}
		state = db.Mathacademy
	}

	if state.LastDate == "" {
		state.LastDate = today
		state.StartOfDayTotal = latestTotal
		state.CurrentTotal = latestTotal
	}

	if state.LastDate != today {
		if state.CurrentTotal > 0 {
			state.StartOfDayTotal = state.CurrentTotal
		} else {
			state.StartOfDayTotal = latestTotal
		}
		state.LastDate = today
	}

	state.CurrentTotal = latestTotal
	writeDb(db)

	diff := latestTotal - state.StartOfDayTotal
	if diff < 0 {
		return 0
	}
	return diff
}

func scrapeBootdev() (int, error) {
	user := os.Getenv("BOOTDEV_USERNAME")
	if user == "" {
		return 0, fmt.Errorf("BOOTDEV_USERNAME missing")
	}

	resp, err := http.Get(fmt.Sprintf("https://boot.dev/u/%s", user))
	if err != nil {
		return 0, err
	}
	defer resp.Body.Close()

	doc, err := goquery.NewDocumentFromReader(resp.Body)
	if err != nil {
		return 0, err
	}

	pageText := doc.Find("body").Text()

	reXP := regexp.MustCompile(`([0-9,]+)\s*XP`)
	matches := reXP.FindStringSubmatch(pageText)
	if len(matches) > 1 {
		clean := strings.ReplaceAll(matches[1], ",", "")
		if xp, err := strconv.Atoi(clean); err == nil {
			return xp, nil
		}
	}

	return 0, fmt.Errorf("could not extract XP from Boot.dev")
}

func scrapeMathAcademy() (int, int, error) {
	email := os.Getenv("MATHACADEMY_EMAIL")
	password := os.Getenv("MATHACADEMY_PASSWORD")
	if email == "" || password == "" {
		return 0, 0, fmt.Errorf("MathAcademy credentials missing")
	}

	jar, _ := cookiejar.New(nil)
	client := &http.Client{
		Jar: jar,
		CheckRedirect: func(req *http.Request, via []*http.Request) error {
			if len(via) >= 5 {
				return http.ErrUseLastResponse
			}
			return nil
		},
	}

	// GET login page to get hidden inputs (CSRF tokens etc)
	resp, err := client.Get("https://mathacademy.com/login")
	if err != nil {
		return 0, 0, err
	}
	doc, err := goquery.NewDocumentFromReader(resp.Body)
	resp.Body.Close()
	if err != nil {
		return 0, 0, err
	}

	formData := url.Values{}
	doc.Find("form[action=\"/login\"] input[type=\"hidden\"]").Each(func(i int, s *goquery.Selection) {
		name, _ := s.Attr("name")
		val, _ := s.Attr("value")
		formData.Add(name, val)
	})
	formData.Add("usernameOrEmail", email)
	formData.Add("password", password)
	formData.Add("submit", "LOGIN")

	req, _ := http.NewRequest("POST", "https://mathacademy.com/login", strings.NewReader(formData.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	req.Header.Set("Referer", "https://mathacademy.com/login")

	resp, err = client.Do(req)
	if err != nil {
		return 0, 0, err
	}
	defer resp.Body.Close()

	// Clean out script/style, get just text
	doc2, _ := goquery.NewDocumentFromReader(resp.Body)
	cleanText := doc2.Find("body").Text()

	// simplify whitespace
	reSpaces := regexp.MustCompile(`\s+`)
	cleanText = reSpaces.ReplaceAllString(cleanText, " ")

	todayXp := 0
	totalXp := 0

	reTodayExact := regexp.MustCompile(`(?i)TODAY[^0-9a-z]*(\d+)(?:\/\d+)?\s*XP`)
	if m := reTodayExact.FindStringSubmatch(cleanText); m != nil {
		todayXp, _ = strconv.Atoi(strings.ReplaceAll(m[1], ",", ""))
	} else {
		reTodayFB := regexp.MustCompile(`(?i)today[^0-9]{0,20}([0-9,]+)`)
		if m := reTodayFB.FindStringSubmatch(cleanText); m != nil {
			todayXp, _ = strconv.Atoi(strings.ReplaceAll(m[1], ",", ""))
		}
	}

	reTotal := regexp.MustCompile(`(?i)TOTAL\s*EARNED[^0-9a-z]*([0-9,]+)\s*XP`)
	if m := reTotal.FindStringSubmatch(cleanText); m != nil {
		totalXp, _ = strconv.Atoi(strings.ReplaceAll(m[1], ",", ""))
	} else {
		reGeneric := regexp.MustCompile(`(?i)(?:^|\s)([0-9,]+)\s*XP`)
		if m := reGeneric.FindStringSubmatch(cleanText); m != nil {
			totalXp, _ = strconv.Atoi(strings.ReplaceAll(m[1], ",", ""))
		}
	}

	return todayXp, totalXp, nil
}

func pollStats() {
	log.Printf("Polling data...")

	bXp, err := scrapeBootdev()
	if err != nil {
		log.Printf("Bootdev err: %v", err)
	}
	bDaily := processDailyStats("bootdev", bXp)

	mDaily, mTotal, err := scrapeMathAcademy()
	if err != nil {
		log.Printf("MathAcademy err: %v", err)
	}

	calc2Hours := "0.0"
	if mTotal > 0 {
		calc2Hours = fmt.Sprintf("%.1f", float64(15404-mTotal)/60.0)
	}

	goHours := "0.0"
	if bXp > 0 {
		goHours = fmt.Sprintf("%.1f", float64(1030383-bXp)/1800.0)
	}
	
	bMins := fmt.Sprintf("%.2f", float64(bDaily)/30.0)

	cacheMutex.Lock()
	statsCache = StatsCache{
		Bootdev: &StatsBootdev{
			Total:       bXp,
			DailyGained: bDaily,
			Minutes:     bMins,
			GoHours:     goHours,
		},
		Mathacademy: &StatsMathacademy{
			Total:       mTotal,
			DailyGained: mDaily,
			Calc2Hours:  calc2Hours,
		},
		TotalDailyXp: bDaily + mDaily,
		LastPolled:   time.Now().Format(time.RFC3339),
		Status:       "Active",
	}
	cacheMutex.Unlock()

	log.Printf("Polled successfully.")
}

func enableCORS(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type")
		if r.Method == "OPTIONS" {
			w.WriteHeader(http.StatusOK)
			return
		}
		next(w, r)
	}
}

func statsHandler(w http.ResponseWriter, r *http.Request) {
	cacheMutex.RLock()
	data, _ := json.Marshal(statsCache)
	cacheMutex.RUnlock()

	w.Header().Set("Content-Type", "application/json")
	w.Write(data)
}

func forcePollHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}
	pollStats()
	statsHandler(w, r)
}

func main() {
	if os.Getenv("CLI_MODE") == "true" {
		pollStats()
		
		cacheMutex.RLock()
		data, _ := json.Marshal(statsCache)
		cacheMutex.RUnlock()
		
		os.WriteFile(filepath.Join(filepath.Dir(dbPath), "stats.json"), data, 0644)
		log.Println("CLI execution complete. stats.json created.")
		return
	}

	go func() {
		pollStats()
		for {
			time.Sleep(1 * time.Hour)
			pollStats()
		}
	}()

	http.HandleFunc("/api/stats", enableCORS(statsHandler))
	http.HandleFunc("/api/force-poll", enableCORS(forcePollHandler))

	port := os.Getenv("PORT")
	if port == "" {
		port = "8181"
	}

	log.Printf("Backend hourly polling agent (Go) active on port %s", port)
	log.Fatal(http.ListenAndServe(":"+port, nil))
}
