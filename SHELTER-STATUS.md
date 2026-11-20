# Shelter code status

**Rule:** no separate "clean" repository is created until this reaches **zero**.
While any Shelter-derived file remains, the project is a GPLv3 derivative — one repo, one licence,
credit kept.

Check it any time:

```bash
python3 tools/shelter-audit.py /path/to/Shelter-master
```

## Current state (build 21)

| | |
|---|---|
| Java files | 32 |
| Still Shelter-derived | **23** |
| Shelter lines remaining | **3,289** |
| Status | GPLv3 derivative — single repo |

### Per file (highest first)

| Shelter % | Shelter lines | Our lines | File |
|---|---|---|---|
| 80% | 233 | 307 | `ui/AppListAdapter.java` |
| 65% | 313 | 515 | `ui/MainActivity.java` |
| 65% | 233 | 577 | `services/ShelterService.java` |
| 62% | 381 | 737 | `ui/AppListFragment.java` |
| 58% | 47 | 65 | `util/UriForwardProxy.java` |
| 58% | 15 | 36 | `ui/SettingsActivity.java` |
| 57% | 94 | 92 | `util/SettingsManager.java` |
| 52% | 37 | 57 | `services/KillerService.java` |
| 50% | 66 | 102 | `util/ApplicationInfoWrapper.java` |
| 47% | 11 | 19 | `ui/FinalizeActivity.java` |
| 38% | 175 | 825 | `ui/SettingsFragment.java` |
| 35% | 39 | 89 | `util/InstallationProgressListener.java` |
| 35% | 20 | 37 | `util/FileProviderProxy.java` |
| 33% | 71 | 157 | `util/LocalStorageManager.java` |
| 25% | 6 | 27 | `ui/BaseFragment.java` |

Files below 25% (already clean): `AppInspector`, `PermissionSnapshot`, `AppSortHelper`,
`NijatvaSuite`, `ThemeBridge`, `MyWorkDocumentsProvider`, `AutomationReceiver`, `ApiReceiver`,
`WorkModeTile`.

## History

| Build | Shelter lines | Note |
|---|---|---|
| 20 | ~4,910 | before Wave 1 |
| 21 | **3,289** | Wave 1 merged (10 files), `PaymentStubService` deleted |

## Next targets (biggest wins)

1. `AppListAdapter` (80%, 233 lines) — self-contained, easiest large win
2. `MainActivity` (65%) and `ShelterService` (65%) — services first, then UI
3. `DummyActivity` + `AuthenticationUtility` — the cross-profile relay, do them together
