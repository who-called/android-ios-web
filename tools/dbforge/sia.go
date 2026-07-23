package main

import (
	"bufio"
	"compress/gzip"
	"encoding/binary"
	"fmt"
	"io"
)

// SIA community-database binary format (reverse-engineered from Should I Answer?
// v1.8.273). A "slice" is a self-contained block of aggregated number ratings.
//
// Header:
//   magic   [4]byte   "MTZF" (full slice) | "MTZD" (delta)
//   flag    byte
//   version uint32 LE  (== _dbVer this slice brings you to)
//   region  [2]byte    e.g. "WW"
//   _       uint32 LE  (unused)
//   count   uint32 LE  (number of records)
// Records (count × 13 bytes):
//   phone   uint64 LE  (E.164 without '+', country code included)
//   pos     byte       POSITIVE votes
//   neg     byte       NEGATIVE votes
//   neu     byte       NEUTRAL votes
//   _       byte       reserved (always 0 observed)
//   catID   byte       category id (enum f, 0..18)
// Footer:
//   "CP" + deletedCount uint32 LE + deletedCount × uint64 LE (tombstoned phones)
//   "MTZEND"

const recordSize = 13

// Record is one aggregated number as stored by SIA.
type Record struct {
	Phone      uint64 `json:"phone"`
	Pos        int    `json:"pos"`
	Neg        int    `json:"neg"`
	Neu        int    `json:"neu"`
	CategoryID int    `json:"category_id"`
}

// SliceHeader carries the slice-level metadata.
type SliceHeader struct {
	Magic   string
	Version uint32
	Region  string
	Count   uint32
	IsDelta bool
}

// DecodeResult is the full decoded slice.
type DecodeResult struct {
	Header  SliceHeader
	Records []Record
	Deleted []uint64
}

// siaCategory maps SIA category ids (enum f) to who-called category strings.
var siaCategory = map[int]string{
	0:  "unknown",        // NONE
	1:  "telemarketing",  // TELEMARKETER
	2:  "telemarketing",  // DEBT_COLLECTOR
	3:  "robocall",       // SILENT_CALL
	4:  "telemarketing",  // NUISANCE_CALL
	5:  "telemarketing",  // UNSOLICITED_CALL
	6:  "telemarketing",  // CALL_CENTRE
	7:  "other",          // FAX_MACHINE
	8:  "legit",          // NON_PROFIT
	9:  "telemarketing",  // POLITICAL
	10: "scam",           // SCAM
	11: "other",          // PRANK
	12: "other",          // SMS
	13: "telemarketing",  // SURVEY
	14: "unknown",        // OTHER
	15: "legit",          // FINANCE_SERVICE
	16: "legit",          // COMPANY
	17: "legit",          // SERVICE
	18: "robocall",       // ROBOCALL
}

// CategoryName returns the who-called category for a SIA category id.
func CategoryName(id int) string {
	if name, ok := siaCategory[id]; ok {
		return name
	}
	return "unknown"
}

// maybeGunzip wraps r in a gzip reader when the stream starts with the gzip
// magic, otherwise returns the original stream. The endpoint serves gzip; the
// bundled asset slices are raw.
func maybeGunzip(r io.Reader) (io.Reader, error) {
	br := bufio.NewReader(r)
	magic, err := br.Peek(2)
	if err != nil && err != io.EOF {
		return nil, err
	}
	if len(magic) == 2 && magic[0] == 0x1f && magic[1] == 0x8b {
		return gzip.NewReader(br)
	}
	return br, nil
}

// DecodeSlice reads one MTZF/MTZD slice (raw or gzipped) from r.
func DecodeSlice(r io.Reader) (*DecodeResult, error) {
	in, err := maybeGunzip(r)
	if err != nil {
		return nil, fmt.Errorf("gunzip: %w", err)
	}
	br := bufio.NewReaderSize(in, 1<<20)

	var hdr [15]byte // magic(4)+flag(1)+ver(4)+region(2)+unused(4)
	if _, err := io.ReadFull(br, hdr[:]); err != nil {
		return nil, fmt.Errorf("read header: %w", err)
	}
	magic := string(hdr[0:4])
	if magic != "MTZF" && magic != "MTZD" {
		return nil, fmt.Errorf("bad magic %q", magic)
	}
	version := binary.LittleEndian.Uint32(hdr[5:9])
	region := string(hdr[9:11])

	var cnt [4]byte
	if _, err := io.ReadFull(br, cnt[:]); err != nil {
		return nil, fmt.Errorf("read count: %w", err)
	}
	count := binary.LittleEndian.Uint32(cnt[:])

	res := &DecodeResult{
		Header: SliceHeader{
			Magic: magic, Version: version, Region: region,
			Count: count, IsDelta: magic == "MTZD",
		},
		Records: make([]Record, 0, count),
	}

	buf := make([]byte, recordSize)
	for i := uint32(0); i < count; i++ {
		if _, err := io.ReadFull(br, buf); err != nil {
			// Return what we decoded so far (partial/truncated stream, e.g. a
			// capped endpoint probe) alongside the error so callers can choose.
			return res, fmt.Errorf("record %d/%d: %w", i, count, err)
		}
		res.Records = append(res.Records, Record{
			Phone:      binary.LittleEndian.Uint64(buf[0:8]),
			Pos:        int(buf[8]),
			Neg:        int(buf[9]),
			Neu:        int(buf[10]),
			CategoryID: int(buf[12]),
		})
	}

	// Footer: "CP" + deletedCount + ids, then "MTZEND". Tolerate truncation
	// (partial endpoint downloads) by returning what we have.
	var cp [2]byte
	if _, err := io.ReadFull(br, cp[:]); err != nil {
		return res, nil
	}
	if string(cp[:]) == "CP" {
		var dc [4]byte
		if _, err := io.ReadFull(br, dc[:]); err == nil {
			del := binary.LittleEndian.Uint32(dc[:])
			res.Deleted = make([]uint64, 0, del)
			var d [8]byte
			for i := uint32(0); i < del; i++ {
				if _, err := io.ReadFull(br, d[:]); err != nil {
					break
				}
				res.Deleted = append(res.Deleted, binary.LittleEndian.Uint64(d[:]))
			}
		}
	}
	return res, nil
}
