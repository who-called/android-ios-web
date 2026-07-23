package main

import (
	"bufio"
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"os"
	"strconv"
	"time"

	"github.com/jackc/pgx/v5"
)

// cmdIngest loads decoded NDJSON records into the internal sia_seed table.
//
// Strategy: COPY the batch into a TEMP staging table (fast binary-ish path),
// then a single INSERT ... ON CONFLICT upsert into sia_seed. This keeps the
// 11M-row load to a handful of round-trips.
func cmdIngest(args []string) {
	fs := flag.NewFlagSet("ingest", flag.ExitOnError)
	in := fs.String("in", "-", "input NDJSON path ('-' = stdin)")
	dsn := fs.String("dsn", os.Getenv("DATABASE_URL"), "Postgres DSN (or $DATABASE_URL)")
	batch := fs.Int("batch", 200_000, "rows per COPY+upsert flush")
	_ = fs.Parse(args)

	if *dsn == "" {
		fmt.Fprintln(os.Stderr, "ingest: --dsn or $DATABASE_URL required")
		os.Exit(2)
	}

	var r *os.File
	if *in == "-" {
		r = os.Stdin
	} else {
		var err error
		r, err = os.Open(*in)
		must(err)
		defer r.Close()
	}

	ctx := context.Background()
	conn, err := pgx.Connect(ctx, normalizeDSN(*dsn))
	must(err)
	defer conn.Close(ctx)

	now := time.Now().UTC()
	sc := bufio.NewScanner(r)
	sc.Buffer(make([]byte, 1<<20), 1<<20)

	rows := make([][]any, 0, *batch)
	dels := make([]string, 0, 4096)
	var total, removed int64
	var maxVer int
	region := ""

	flush := func() {
		if len(rows) > 0 {
			must(upsertSeed(ctx, conn, rows))
			total += int64(len(rows))
			rows = rows[:0]
		}
		if len(dels) > 0 {
			n := deleteSeed(ctx, conn, dels)
			removed += n
			dels = dels[:0]
		}
		fmt.Fprintf(os.Stderr, "[ingest] upserted=%d removed=%d\n", total, removed)
	}

	for sc.Scan() {
		var o outRecord
		if err := json.Unmarshal(sc.Bytes(), &o); err != nil {
			continue
		}
		if int(o.Version) > maxVer {
			maxVer = int(o.Version)
		}
		if region == "" && o.Region != "" {
			region = o.Region
		}
		if o.Del {
			dels = append(dels, strconv.FormatUint(o.Phone, 10))
		} else {
			rows = append(rows, []any{
				strconv.FormatUint(o.Phone, 10),
				o.Pos, o.Neg, o.Neu, o.CategoryID, o.Category, int(o.Version), now,
			})
		}
		if len(rows) >= *batch || len(dels) >= *batch {
			flush()
		}
	}
	must(sc.Err())
	flush()

	if region != "" && maxVer > 0 {
		must(recordSync(ctx, conn, region, maxVer))
	}
	fmt.Fprintf(os.Stderr, "[ingest] done: upserted=%d removed=%d region=%s version=%d\n",
		total, removed, region, maxVer)
}

// ingestDecoded upserts a freshly decoded slice (records + tombstones) and
// advances the per-region version. Shared by `pull`. Returns (upserted, removed).
func ingestDecoded(ctx context.Context, conn *pgx.Conn, res *DecodeResult, batch int) (int64, int64) {
	now := time.Now().UTC()
	rows := make([][]any, 0, batch)
	var up, rm int64
	for _, r := range res.Records {
		rows = append(rows, []any{
			strconv.FormatUint(r.Phone, 10),
			r.Pos, r.Neg, r.Neu, r.CategoryID, CategoryName(r.CategoryID), int(res.Header.Version), now,
		})
		if len(rows) >= batch {
			must(upsertSeed(ctx, conn, rows))
			up += int64(len(rows))
			rows = rows[:0]
		}
	}
	if len(rows) > 0 {
		must(upsertSeed(ctx, conn, rows))
		up += int64(len(rows))
	}
	if len(res.Deleted) > 0 {
		phones := make([]string, len(res.Deleted))
		for i, p := range res.Deleted {
			phones[i] = strconv.FormatUint(p, 10)
		}
		rm = deleteSeed(ctx, conn, phones)
	}
	if res.Header.Region != "" && res.Header.Version > 0 {
		must(recordSync(ctx, conn, res.Header.Region, int(res.Header.Version)))
	}
	return up, rm
}

// deleteSeed removes tombstoned phones from sia_seed (delta deletions).
func deleteSeed(ctx context.Context, conn *pgx.Conn, phones []string) int64 {
	tag, err := conn.Exec(ctx, `DELETE FROM sia_seed WHERE phone = ANY($1)`, phones)
	must(err)
	return tag.RowsAffected()
}

// recordSync advances the per-region high-water version (monotonic).
func recordSync(ctx context.Context, conn *pgx.Conn, region string, ver int) error {
	_, err := conn.Exec(ctx, `
		INSERT INTO sia_sync (region, db_ver, synced_at) VALUES ($1, $2, now())
		ON CONFLICT (region) DO UPDATE SET
			db_ver = GREATEST(sia_sync.db_ver, EXCLUDED.db_ver), synced_at = now()`,
		region, ver)
	return err
}

var seedCols = []string{"phone", "pos", "neg", "neu", "category_id", "category", "db_ver", "imported_at"}

func upsertSeed(ctx context.Context, conn *pgx.Conn, rows [][]any) error {
	tx, err := conn.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx)

	if _, err := tx.Exec(ctx, `
		CREATE TEMP TABLE _seed_stage (
			phone text, pos int, neg int, neu int,
			category_id int, category text, db_ver int, imported_at timestamptz
		) ON COMMIT DROP`); err != nil {
		return err
	}
	if _, err := tx.CopyFrom(ctx, pgx.Identifier{"_seed_stage"}, seedCols, pgx.CopyFromRows(rows)); err != nil {
		return fmt.Errorf("copy: %w", err)
	}
	// Latest version wins; only overwrite when the incoming slice is newer.
	if _, err := tx.Exec(ctx, `
		INSERT INTO sia_seed (phone, pos, neg, neu, category_id, category, db_ver, imported_at)
		SELECT phone, pos, neg, neu, category_id, category, db_ver, imported_at FROM _seed_stage
		ON CONFLICT (phone) DO UPDATE SET
			pos = EXCLUDED.pos, neg = EXCLUDED.neg, neu = EXCLUDED.neu,
			category_id = EXCLUDED.category_id, category = EXCLUDED.category,
			db_ver = EXCLUDED.db_ver, imported_at = EXCLUDED.imported_at
		WHERE EXCLUDED.db_ver >= sia_seed.db_ver`); err != nil {
		return fmt.Errorf("upsert: %w", err)
	}
	return tx.Commit(ctx)
}

// normalizeDSN strips Prisma-style query params pgx doesn't understand
// (e.g. ?schema=public) so the same DATABASE_URL works for both.
func normalizeDSN(dsn string) string {
	if i := indexOf(dsn, "?schema="); i >= 0 {
		return dsn[:i]
	}
	return dsn
}

func indexOf(s, sub string) int {
	for i := 0; i+len(sub) <= len(s); i++ {
		if s[i:i+len(sub)] == sub {
			return i
		}
	}
	return -1
}
