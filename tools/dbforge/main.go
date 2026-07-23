// dbforge — who-called database forge.
//
// Reconstitutes the SIA community database (and our own sources) into a single
// harmonized shape, and builds the warm-up artifacts shipped in the apps.
//
// Subcommands:
//
//	decode   Decode MTZF/MTZD slices (bundled assets dir, or a single gzipped
//	         endpoint dump) into NDJSON records — parallel across files.
//
// (ingest / snapshot land in follow-up commits.)
package main

import (
	"bufio"
	"encoding/json"
	"flag"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"sort"
	"sync"
	"sync/atomic"
)

func main() {
	if len(os.Args) < 2 {
		usage()
		os.Exit(2)
	}
	switch os.Args[1] {
	case "decode":
		cmdDecode(os.Args[2:])
	case "ingest":
		cmdIngest(os.Args[2:])
	case "pull":
		cmdPull(os.Args[2:])
	case "snapshot":
		cmdSnapshot(os.Args[2:])
	default:
		usage()
		os.Exit(2)
	}
}

func usage() {
	fmt.Fprintln(os.Stderr, "usage: dbforge <decode> [flags]")
}

// outRecord is one NDJSON line: a decoded number plus slice provenance, or a
// tombstone (Del=true) marking a phone deleted in a delta slice.
type outRecord struct {
	Phone      uint64 `json:"phone"`
	Pos        int    `json:"pos"`
	Neg        int    `json:"neg"`
	Neu        int    `json:"neu"`
	CategoryID int    `json:"category_id"`
	Category   string `json:"category"`
	Version    uint32 `json:"version"`
	Region     string `json:"region"`
	Del        bool   `json:"del,omitempty"`
}

func cmdDecode(args []string) {
	fs := flag.NewFlagSet("decode", flag.ExitOnError)
	dir := fs.String("dir", "", "directory of MTZF slice files (parallel)")
	file := fs.String("file", "", "single MTZF/MTZD file (raw or gzipped; '-' = stdin)")
	out := fs.String("out", "-", "output NDJSON path ('-' = stdout)")
	workers := fs.Int("workers", runtime.NumCPU(), "parallel decode workers (dir mode)")
	_ = fs.Parse(args)

	if (*dir == "") == (*file == "") {
		fmt.Fprintln(os.Stderr, "decode: provide exactly one of --dir or --file")
		os.Exit(2)
	}

	w := os.Stdout
	if *out != "-" {
		f, err := os.Create(*out)
		must(err)
		defer f.Close()
		w = f
	}
	bw := bufio.NewWriterSize(w, 1<<20)
	defer bw.Flush()

	// Single writer goroutine owns the output; workers feed it via a channel.
	lines := make(chan []byte, 4096)
	var writeWG sync.WaitGroup
	writeWG.Add(1)
	var written int64
	go func() {
		defer writeWG.Done()
		for b := range lines {
			bw.Write(b)
			bw.WriteByte('\n')
			written++
		}
	}()

	enc := func(r Record, h SliceHeader) []byte {
		b, _ := json.Marshal(outRecord{
			Phone: r.Phone, Pos: r.Pos, Neg: r.Neg, Neu: r.Neu,
			CategoryID: r.CategoryID, Category: CategoryName(r.CategoryID),
			Version: h.Version, Region: h.Region,
		})
		return b
	}
	encDel := func(phone uint64, h SliceHeader) []byte {
		b, _ := json.Marshal(outRecord{Phone: phone, Version: h.Version, Region: h.Region, Del: true})
		return b
	}

	var files int64
	var deleted int64

	if *file != "" {
		var f *os.File
		if *file == "-" {
			f = os.Stdin
		} else {
			var err error
			f, err = os.Open(*file)
			must(err)
			defer f.Close()
		}
		res, err := DecodeSlice(f)
		if err != nil {
			// Tolerate truncated single-file probes: emit what was decoded.
			fmt.Fprintf(os.Stderr, "[decode] partial: %v\n", err)
		}
		for _, r := range res.Records {
			lines <- enc(r, res.Header)
		}
		for _, p := range res.Deleted {
			lines <- encDel(p, res.Header)
		}
		atomic.AddInt64(&deleted, int64(len(res.Deleted)))
		atomic.AddInt64(&files, 1)
		fmt.Fprintf(os.Stderr, "[decode] %s ver=%d region=%s records=%d deleted=%d\n",
			res.Header.Magic, res.Header.Version, res.Header.Region,
			len(res.Records), len(res.Deleted))
	} else {
		paths := listSlices(*dir)
		fmt.Fprintf(os.Stderr, "[decode] %d slice files, %d workers\n", len(paths), *workers)
		jobs := make(chan string, len(paths))
		for _, p := range paths {
			jobs <- p
		}
		close(jobs)
		var wg sync.WaitGroup
		for i := 0; i < *workers; i++ {
			wg.Add(1)
			go func() {
				defer wg.Done()
				for p := range jobs {
					f, err := os.Open(p)
					if err != nil {
						fmt.Fprintf(os.Stderr, "[decode] open %s: %v\n", p, err)
						continue
					}
					res, err := DecodeSlice(f)
					f.Close()
					if err != nil {
						fmt.Fprintf(os.Stderr, "[decode] %s: %v\n", filepath.Base(p), err)
						continue
					}
					for _, r := range res.Records {
						lines <- enc(r, res.Header)
					}
					for _, p := range res.Deleted {
						lines <- encDel(p, res.Header)
					}
					atomic.AddInt64(&deleted, int64(len(res.Deleted)))
					atomic.AddInt64(&files, 1)
				}
			}()
		}
		wg.Wait()
	}

	close(lines)
	writeWG.Wait()
	fmt.Fprintf(os.Stderr, "[decode] done: files=%d records=%d deleted=%d\n",
		atomic.LoadInt64(&files), written, atomic.LoadInt64(&deleted))
}

// listSlices returns the data_slice_*.dat payload files (excluding the
// info/list index files), sorted for deterministic runs.
func listSlices(dir string) []string {
	matches, err := filepath.Glob(filepath.Join(dir, "data_slice_*.dat"))
	must(err)
	out := matches[:0]
	for _, m := range matches {
		base := filepath.Base(m)
		if base == "data_slice_info.dat" || base == "data_slice_list.dat" {
			continue
		}
		out = append(out, m)
	}
	sort.Strings(out)
	return out
}

func must(err error) {
	if err != nil {
		fmt.Fprintln(os.Stderr, "error:", err)
		os.Exit(1)
	}
}
