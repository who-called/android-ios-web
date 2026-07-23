package main

import (
	"bytes"
	"context"
	"crypto/md5"
	"crypto/rand"
	"encoding/hex"
	"flag"
	"fmt"
	"io"
	"mime/multipart"
	"net/http"
	"os"
	"strconv"
	"time"

	"github.com/jackc/pgx/v5"
)

// cmdPull performs an incremental, signed sync against the SIA community
// endpoint and ingests each delta directly into sia_seed — resuming from the
// per-region high-water version stored in sia_sync, so nothing is re-fetched.
//
// Mirrors the app's own loop: request `/get-database/cached?_dbVer=N`, decode
// the gzipped MTZD it returns, ingest (upsert + tombstones), advance N to the
// slice's version, repeat until the server stops returning data.
//
// NOTE: this calls a third-party proprietary service. Use only with proper
// authorization; data enters as source='sia' (traceable / purgeable).
func cmdPull(args []string) {
	fs := flag.NewFlagSet("pull", flag.ExitOnError)
	dsn := fs.String("dsn", os.Getenv("DATABASE_URL"), "Postgres DSN (or $DATABASE_URL)")
	baseURL := fs.String("base-url", "https://aapi.shouldianswer.net/srvapp/get-database/cached", "endpoint")
	region := fs.String("region", "WW", "region marker")
	country := fs.String("country", "US", "_country field")
	appVer := fs.String("app-ver", "10802730", "_appVer field")
	from := fs.Int("from", -1, "start version (default: resume from sia_sync)")
	floor := fs.Int("floor", 3357, "min start version when none known (the bundled baseline; 0/ODD has no delta)")
	maxRounds := fs.Int("max-rounds", 50, "safety cap on delta rounds")
	batch := fs.Int("batch", 200_000, "upsert batch size")
	_ = fs.Parse(args)

	if *dsn == "" {
		fmt.Fprintln(os.Stderr, "pull: --dsn or $DATABASE_URL required")
		os.Exit(2)
	}
	ctx := context.Background()
	conn, err := pgx.Connect(ctx, normalizeDSN(*dsn))
	must(err)
	defer conn.Close(ctx)

	ver := *from
	if ver < 0 {
		ver = currentVersion(ctx, conn, *region)
	}
	if ver <= 0 {
		// 0 / unknown has no computable delta (server returns ODD); start from
		// the bundled baseline so a fresh DB bootstraps to current.
		ver = *floor
	}
	fmt.Fprintf(os.Stderr, "[pull] region=%s starting from version=%d\n", *region, ver)

	client := &http.Client{Timeout: 120 * time.Second}
	for round := 0; round < *maxRounds; round++ {
		body, ctype, err := fetchSlice(ctx, client, *baseURL, ver, *appVer, *country)
		must(err)
		// Control codes (NC / ODD / OAP) come back as text/html, not octet-stream.
		if !bytes.HasPrefix([]byte(ctype), []byte("application/octet-stream")) {
			fmt.Fprintf(os.Stderr, "[pull] server control response %q at version=%d → stop\n",
				trim(body, 16), ver)
			break
		}
		res, err := DecodeSlice(bytes.NewReader(body))
		if err != nil {
			fmt.Fprintf(os.Stderr, "[pull] decode (partial ok): %v\n", err)
		}
		up, rm := ingestDecoded(ctx, conn, res, *batch)
		fmt.Fprintf(os.Stderr, "[pull] round %d: %s v%d upserted=%d removed=%d\n",
			round, res.Header.Magic, res.Header.Version, up, rm)
		if int(res.Header.Version) <= ver { // no forward progress → done
			fmt.Fprintln(os.Stderr, "[pull] no version advance → up to date")
			break
		}
		ver = int(res.Header.Version)
	}
	fmt.Fprintf(os.Stderr, "[pull] done at version=%d\n", ver)
}

func currentVersion(ctx context.Context, conn *pgx.Conn, region string) int {
	var v int
	err := conn.QueryRow(ctx, `SELECT db_ver FROM sia_sync WHERE region=$1`, region).Scan(&v)
	if err != nil {
		return 0
	}
	return v
}

// fetchSlice issues the signed multipart request for one version. The checksum
// is MD5(concat(field values in send order) + salt); since the same field order
// is used for both the body and the checksum, the server validates by consistency.
func fetchSlice(ctx context.Context, c *http.Client, base string, ver int, appVer, country string) ([]byte, string, error) {
	const salt = "saltandmira2"
	fields := [][2]string{
		{"_appId", randUUID()},
		{"_device", "generic"},
		{"_model", "Pixel"},
		{"_manufacturer", "Google"},
		{"_api", "33"},
		{"_appFamily", "SIA-NEXT"},
		{"_appVer", appVer},
		{"_dbVer", strconv.Itoa(ver)},
		{"_country", country},
	}
	concat := ""
	for _, f := range fields {
		concat += f[1]
	}
	sum := md5.Sum([]byte(concat + salt))
	fields = append(fields, [2]string{"_checksum", hex.EncodeToString(sum[:])})

	var buf bytes.Buffer
	mw := multipart.NewWriter(&buf)
	for _, f := range fields {
		_ = mw.WriteField(f[0], f[1])
	}
	mw.Close()

	url := fmt.Sprintf("%s?_dbVer=%d", base, ver)
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, &buf)
	if err != nil {
		return nil, "", err
	}
	req.Header.Set("Content-Type", mw.FormDataContentType())
	req.Header.Set("User-Agent", "okhttp/3.12")
	resp, err := c.Do(req)
	if err != nil {
		return nil, "", err
	}
	defer resp.Body.Close()
	data, err := io.ReadAll(resp.Body)
	return data, resp.Header.Get("Content-Type"), err
}

func randUUID() string {
	var b [16]byte
	_, _ = rand.Read(b[:])
	b[6] = (b[6] & 0x0f) | 0x40
	b[8] = (b[8] & 0x3f) | 0x80
	return fmt.Sprintf("%x-%x-%x-%x-%x", b[0:4], b[4:6], b[6:8], b[8:10], b[10:16])
}

func trim(b []byte, n int) string {
	if len(b) > n {
		b = b[:n]
	}
	return string(b)
}
