# 104 Linux launcher icon

## 1. Summary
The Linux .deb/.rpm launcher icon was the old purple play-triangle. `packageLinux` passes `packaging/linux` to jpackage as `--resource-dir`, and `packaging/linux/vela.png` there overrides the app image icon. It was never updated when the icon changed to the red sailboat. Replaced it with `docs/icon/vela-icon-1024.png`.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| packaging/linux/vela.png identical to docs/icon/vela-icon-1024.png (md5) | match | verified-by-running |
| Both images viewed (old purple, new red sailboat) | confirmed | verified-by-running |
| Rebuilt package / installed launcher | not done | not verified |

## 3. Files created/changed
- Changed: `packaging/linux/vela.png`
- Created: `docs/reports/104-linux-launcher-icon.md`

## 4. Decisions the spec didn't dictate
- Kept the file as a copy (not a symlink) since jpackage resource-dir copies it. Red sailboat is treated as the current icon (matches Android, desktop window icons, .ico).

## 5. Known issues / unverified assumptions
- Existing installs and `desktopApp/build/linux-packages/vela-0.1.0-1.x86_64.rpm` still carry the old icon until rebuilt. Desktop icon caches may need a refresh.

## 6. Manual test checklist
1. `./gradlew :desktopApp:packageLinux`
2. Reinstall the new rpm/deb (`sudo dnf reinstall <rpm>`).
3. Check the app-menu entry shows the red sailboat; if not, run `gtk-update-icon-cache` or log out/in.
