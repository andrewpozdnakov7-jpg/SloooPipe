# Текущий результат SloooPipe — 2026-10-22

APK32 прошёл широкую проверку в LDPlayer14/Android14: 20 queue cycles, обычный Play,
реальные repeat/auto-next, queue mutations, quality, background/popup/PiP, notification,
local video/audio, consent и saved queue restoration. Звук подтвердил пользователь.
[Полные доказательства](docs/APK32_DEVICE_CHECKPOINT.md), [матрица](docs/APK32_ACCEPTANCE.csv).

Два дефекта остаются в APK32: постоянная подпись скорости «1x» и старая мини-панель после notification Close
при просмотре другой detail page/stale consent. ZIP33 исправляет их в исходниках;
его APK ещё не собран/не проверен. Новые PASS не переносятся на ZIP33 автоматически.

Production changes: отдельное сохранённое overlay ownership в detail fragment и actual
speed label в generic NewPlayer UI. Checkstyle/diff PASS; TopUI24 legacy ktlint findings,
новых ошибок нет. Cumulative generic patch дополнен пропущенными logic/test файлами,
содержит13 файлов/7 contract cases и применён к свежему official pin с Hilt.
Это не upstream compilation/tests или принятие PR.

Gradle/сборка/JVM выполняются на внешнем WSL. Полные XML/counts и обе auth-конфигурации
не предоставлены. Остальная матрица, physical Android16 и новая APK33 regression нужны
до приёмки. Ключи, зависимости и builder09 не менялись. В GitHub main опубликован код
ZIP33 с обновлённой документацией; выданный архив ZIP33 не менялся. Стабильного релиза нет.
