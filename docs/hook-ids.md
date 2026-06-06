# Hook ID Log

Tracking the MSYS command ids each feature depends on, per Messenger version. Goal: log
enough versions to see whether the ids follow a predictable pattern (so a future update can
be anticipated instead of reverse-engineered each time).

**How to capture a value:** see `maintenance.md` §4 — open the in-app **Debug Console**,
perform the action, read the `id=` for the matching method. Append a row here every time you
verify a value on a new version.

---

## No Seen — `MailboxSDKJNI.dispatchVOOOO` (n=5)

| Messenger version | versionCode | command id | verified | date | notes |
|---|---|---|---|---|---|
| v82 (legacy base) | — | 81 | yes | — | original |
| 562.0.0.53.83 | 342412030 | 83 | yes | 2026-06-06 | found via Debug Console |

## No Typing — `MailboxSDKJNI.dispatchVOOOZ` (n=5)

| Messenger version | versionCode | command id | verified | date | notes |
|---|---|---|---|---|---|
| v82 (legacy base) | — | 88 | assumed | — | from original code |
| 562.0.0.53.83 | 342412030 | 90 | pending | 2026-06-06 | captured as `dispatchVOOOZ id=90`; confirm typing is blocked |

---

## Observations / pattern notes

- From v82 → v562, **both** ids shifted by **+2** (seen 81→83, typing 88→90). Consistent
  with Meta inserting/reordering 2 MSYS procedures ahead of both. This is a hint, not a law
  — ids are generated procedure indices, so the shift can differ between versions and
  between procedures. More data points needed before trusting any formula.
- The `dispatch…` **method names are stable** (signature-encoded); only the ids move. If a
  method name ever changes, it means an argument signature changed — log that separately.
- Working hypothesis to test as we log more: ids in the same "neighborhood" tend to shift
  by the same delta within a single version bump. If that holds, finding one id (e.g. seen)
  lets us predict the delta for the other.

## Unidentified signals seen on v562 (for reference)

Captured while using the app; not yet mapped to a feature. Logged in case they become
useful (e.g. read-elsewhere, delivery receipts):

| method | id | n | context when seen |
|---|---|---|---|
| dispatchVO | 23 | 2 | reading a message |
| dispatchVOOOOO | 64 | 7 | reading a message |

---

## Adding a row

When a new Messenger version ships:
1. Build + install (see `maintenance.md` §4.1).
2. Debug Console → Clear → do the action → read the `id`.
3. Add a row to the matching table above with version, versionCode, id, date.
4. Update the constant in the feature file and the table in `maintenance.md` §3.
