# Текущий результат SloooPipe — 2026-10-22

APK31 проверен в LDPlayer14/Android14. Repeat/shuffle snapshot после reopen исправлен;
после last deletion движок освобождается, старая mini скрывается при Back. Новый
Play all после empty queue воспроизводит видео и сохраняет паузу. [Доказательства](docs/APK31_DEVICE_CHECKPOINT.md),
[матрица](docs/APK31_ACCEPTANCE.csv).

FAIL: пустой NewPlayerActivity после last deletion сам не закрывается. Причина — null
текущего item приходит до release, а повторное null не эмитится. ZIP32 наблюдает
терминальный engine flow; guards loading/local picker/replacement сохранены.
Новый APK32 ещё не собран/проверен. Production delta — только NewPlayerActivity.kt.

| Часть | Реализовано | Проверено | Осталось |
| --- | --- | --- | --- |
| Интеграция | Общая сессия/режимы/очередь | APK31 reopen settings, partial empty cleanup/restart | ZIP32 closure и остальная matrix |
| Kotlin/архитектура | Coordinator/hosts/flows | Причина порядка событий разобрана; host static PASS | WSL tests/both auth, lifecycle/device retest |
| Upstream reuse | Pinned generic/embedding patches | Generic patch ZIP31 apply-check PASS, ZIP32 не меняет generic | Library/demo/refactor compilation/review |

Host ktlint/diff PASS; локальных Gradle/JVM/build не было. APK31 build log/test XML не
переданы; две серии10+10 APK31 не выполнялись, старые PASS не переносятся. Builder09,
dependencies/signing/auth неизменны. Физический Android16 и слышимый звук не проверены.
Исходники подготовлены к обновлению публичного GitHub; это не стабильный релиз и не приёмка.
PR/сообщения разработчикам не отправлялись. Границы публикации: [SOURCE_PUBLICATION](docs/SOURCE_PUBLICATION.md).
