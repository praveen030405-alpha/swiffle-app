# Android conversion evidence preview

Open PDF to Markdown and choose **Inspect PDF, Excel or CSV**. Choose **Verify and export evidence** to save the bundle as a ZIP. The regular PDF converter remains available in the same screen. Processing uses local streams and temporary files. No remote reader is invoked.

The preview records raw PDF text-layer positions, explicit XLSX cells (including stored formulas and cache records), and UTF-8 CSV fields. A missing XLSX cell is implicit. An explicit empty cell, a zero-length text value, numeric zero and text zero have different representations. CSV fields retain their text spelling. Its preview treats unquoted empties as empty and quoted empties as zero-length text. A source cell bearing a literal n/a is retained as text.

Each shard is Markdown containing JSON Lines. Reopen the ZIP to inspect index.md, cert.json, shards and data/cells.ndjson. Round-trip PASS applies to extracted records. The certificate explicitly marks source fidelity unverified and the reader benchmark unmeasured. A verifier with the original source can detect a changed source, shard or sidecar against the certificate. An attacker who regenerates the entire bundle can replace an unsigned certificate.

The PDF evidence view records positions and text. Column and section reconstruction, page stitching, dual extraction, stamp classification and scan certification are pending. A page without extractable text stops the preview.

XLSX inspection reads compressed worksheet parts one at a time, uses disk-backed shared strings and retains a single worksheet row while writing. PDF capture retains one page of positions. These are design bounds, not measured device memory caps. Stored values may differ from Excel's displayed formatting. Calculation results are cached values. Excel date systems, format rendering, rich workbook metadata, XLS binary support, independent extraction, formula recalculation and Parquet are pending. Charts, pivots, images and VBA are outside the preview and are not represented as cells.

Source regression inputs needed for certification include the original GSTR1_Return_SMPL_2025_10.pdf and independently checked sheet and page coordinates. Synthetic boundary and mutation tests do not replace these fixtures. No production fidelity PASS is issued.
