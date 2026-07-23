package main

import (
	"context"
	"crypto/sha256"
	"database/sql"
	"encoding/hex"
	"encoding/json"
	"flag"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"time"

	"github.com/jackc/pgx/v5"
	_ "modernc.org/sqlite"
)

// cmdSnapshot builds the warm-up artifacts shipped in the apps so the DB is
// never empty at launch:
//
//	warmup.sqlite        plain SQLite (table scored_numbers) for Android
//	warmup_ios.json      ScoredNumbersFile JSON for iOS (sorted by phone)
//	manifest.json        {dbVer, serverTime, count, sha256{...}}
//
// Source = the unified `numbers` table, filtered to the apps' interest
// (block + warn) — exactly what GET /lists returns on a full sync.
func cmdSnapshot(args []string) {
	fs := flag.NewFlagSet("snapshot", flag.ExitOnError)
	dsn := fs.String("dsn", os.Getenv("DATABASE_URL"), "Postgres DSN (or $DATABASE_URL)")
	outDir := fs.String("out", "./dist", "output directory")
	prefix := fs.String("prefix", "", "only phones starting with this country prefix, e.g. 33 (FR)")
	blockOnly := fs.Bool("block-only", false, "warm-up with block status only (smallest bundle)")
	_ = fs.Parse(args)
	if *dsn == "" {
		fmt.Fprintln(os.Stderr, "snapshot: --dsn or $DATABASE_URL required")
		os.Exit(2)
	}
	must(os.MkdirAll(*outDir, 0o755))

	ctx := context.Background()
	conn, err := pgx.Connect(ctx, normalizeDSN(*dsn))
	must(err)
	defer conn.Close(ctx)

	// dbVer = current SIA high-water (apps send it back; 0 if none yet).
	var dbVer int
	_ = conn.QueryRow(ctx, `SELECT COALESCE(MAX(db_ver),0) FROM sia_sync`).Scan(&dbVer)

	statuses := "('block','warn')"
	if *blockOnly {
		statuses = "('block')"
	}
	// Country scoping: SIA is worldwide; an app (esp. iOS Call Directory) ships
	// only its market. $1 LIKE prefix% uses the PK index when prefix is set.
	q := fmt.Sprintf(`SELECT phone, status, spam_score, category, source
		FROM numbers WHERE status IN %s AND ($1 = '' OR phone LIKE $1 || '%%')`, statuses)
	rows, err := conn.Query(ctx, q, *prefix)
	must(err)
	var recs []snapRec
	for rows.Next() {
		var r snapRec
		must(rows.Scan(&r.Phone, &r.Status, &r.SpamScore, &r.Category, &r.Source))
		recs = append(recs, r)
	}
	rows.Close()
	// Numeric phone order (both apps binary-search on it).
	sort.Slice(recs, func(i, j int) bool {
		a, _ := strconv.ParseInt(recs[i].Phone, 10, 64)
		b, _ := strconv.ParseInt(recs[j].Phone, 10, 64)
		return a < b
	})
	fmt.Fprintf(os.Stderr, "[snapshot] %d active numbers, dbVer=%d\n", len(recs), dbVer)

	serverTime := time.Now().UTC().Format(time.RFC3339)
	sqlitePath := filepath.Join(*outDir, "warmup.sqlite")
	iosPath := filepath.Join(*outDir, "warmup_ios.json")

	// 1) Android SQLite — columns mirror the Room entity (scored_numbers).
	buildSQLite(sqlitePath, recs, serverTime)

	// 2) iOS ScoredNumbersFile JSON.
	iosFile := struct {
		ServerTime string    `json:"serverTime"`
		Numbers    []snapRec `json:"numbers"`
	}{serverTime, recs}
	iosBytes, err := json.Marshal(iosFile)
	must(err)
	must(os.WriteFile(iosPath, iosBytes, 0o644))

	// 3) Manifest with integrity hashes.
	manifest := map[string]any{
		"dbVer":      dbVer,
		"serverTime": serverTime,
		"count":      len(recs),
		"sha256": map[string]string{
			"warmup.sqlite":   sha256File(sqlitePath),
			"warmup_ios.json": sha256Bytes(iosBytes),
		},
	}
	mb, _ := json.MarshalIndent(manifest, "", "  ")
	must(os.WriteFile(filepath.Join(*outDir, "manifest.json"), mb, 0o644))
	fmt.Fprintf(os.Stderr, "[snapshot] wrote %s, %s, manifest.json\n", sqlitePath, iosPath)
}

// snapRec is one warm-up entry (mirrors iOS ScoredNumber JSON keys).
type snapRec struct {
	Phone     string `json:"phone"`
	Status    string `json:"status"`
	SpamScore int    `json:"spamScore"`
	Category  string `json:"category"`
	Source    string `json:"source"`
}

func buildSQLite(path string, recs []snapRec, serverTime string) {
	_ = os.Remove(path)
	db, err := sql.Open("sqlite", path)
	must(err)
	defer db.Close()
	_, err = db.Exec(`
		CREATE TABLE scored_numbers (
			phone TEXT PRIMARY KEY NOT NULL,
			status TEXT NOT NULL,
			spamScore INTEGER NOT NULL,
			category TEXT NOT NULL,
			updatedAt INTEGER NOT NULL
		)`)
	must(err)

	tx, err := db.Begin()
	must(err)
	stmt, err := tx.Prepare(`INSERT INTO scored_numbers (phone,status,spamScore,category,updatedAt) VALUES (?,?,?,?,?)`)
	must(err)
	ts := time.Now().UnixMilli()
	for _, r := range recs {
		_, err := stmt.Exec(r.Phone, r.Status, r.SpamScore, r.Category, ts)
		must(err)
	}
	stmt.Close()
	must(tx.Commit())
}

func sha256File(path string) string {
	b, err := os.ReadFile(path)
	must(err)
	return sha256Bytes(b)
}

func sha256Bytes(b []byte) string {
	h := sha256.Sum256(b)
	return hex.EncodeToString(h[:])
}
