# Актуальная основа NewPipe — 21 октября 2026

Прежняя база refactor сохранена в ветке `checkpoint/refactor-20261021`,
коммит `41a374e0f66cb945c86799b590d32b8d5a19debd`. Текущая работа —
`base/newpipe-dev-20261021`. Этот документ описывает исторический переход; текущий публичный снимок — см. README и RESULT.md.

## Зафиксированные исходники

- NewPipe `dev`: `f46290f28ab44fa514bac0a9f0252fd7d2afb66f`,
  2026-10-21 03:02:50 UTC, «Update extractor to the latest version».
  [Коммит](https://github.com/TeamNewPipe/NewPipe/commit/f46290f28ab44fa514bac0a9f0252fd7d2afb66f).
- NewPipeExtractor `dev`: `2f7d9da4ce82248637fdd3f8025086bd345f7fc2`,
  2026-10-20 18:39:45 UTC. Это ровно revision, на который ссылается version
  catalog выбранного NewPipe, а не произвольная независимая подмена зависимости.
  [Коммит](https://github.com/TeamNewPipe/NewPipeExtractor/commit/2f7d9da4ce82248637fdd3f8025086bd345f7fc2).

Архивы скачаны по полным SHA, CRC проверен. URL, SHA256 и точные ревизии —
в [UPSTREAM.json](../UPSTREAM.json). Версия приложения в выбранном dev —
0.29.1 / code 1015; это development snapshot, не обещание стабильности релиза.

## Что изменилось

Заменена вся основа NewPipe и весь локальный Extractor на выбранные снимки.
Актуальные исправления интерфейса, сервисов и декодирования приходят из upstream.
Прежние Android/iOS fetching branches Extractor удалены upstream: обычный путь
теперь использует visionOS. Он сохранён без восстановления старых ветвей.
Авторизованный MWEB hook использует собственные поля потоков/nonce/token,
независимые от обычного пути. HLS/DASH и прямые форматы поддерживаются отдельно;
SABR не выдаётся за обычную видеоссылку. Полная поддержка SABR не добавлена.

Сохранены отключаемый auth-модуль, Keystore, отдельный WebView-процесс,
явный захват сессии, региональные Google account redirects, отсутствие запрета
скриншотов, generation/cancel/cache guards и подробная DEBUG-диагностика.
Остановка старого плеера адаптирована к текущему Java PlayerHolder.getInstance().
Добавлен `backend.format_rejected`: отбрасываемые Extractor форматы теперь
передают itag и исключение в очищающий logger приложения, включая причины
ошибки n/signature decoder. Анонимный путь не зависит от auth-реализации.

Обновление: NewPlayer/dev теперь встроен в основной APK для локального просмотра.
Текущий этап и границы проверки — [LOCAL_NEWPLAYER_STAGE1.md](LOCAL_NEWPLAYER_STAGE1.md).
Отдельный демонстрационный проект `NewPlayer/` сохранён без изменений.
Выбор refactor в прежнем исследовании считается историческим решением.

## Инструменты и WSL

Новая основа сохраняет более новые upstream инструменты:
Gradle 9.8.2, AGP 9.4.1, Kotlin 2.4.21, KSP 2.3.12, JDK 21,
SDK platform 37.0, Build Tools 36.0.0, target 35, min 23.
NewPlayer собирается независимо, его инструменты не понижались.

Builder revision `20261021-08-current-upstream` меняет фильтр перенесённого
Maven mirror с includeModule на includeVersion: сохранённая старая версия
больше не запрещает скачивание новой версии того же модуля из обычных репозиториев.
[Gradle API](https://docs.gradle.org/current/javadoc/org/gradle/api/artifacts/repositories/RepositoryContentDescriptor.html).
Ключи подписи и их настройки остаются внешними. Новые Gradle/dependencies
потребуют наличия в кэше или скачивания на сборочном компьютере.

Для ZIP 13 заменить builder новым файлом из пакета и поместить исходный ZIP
в `sources`; запуск прежней универсальной командой, выбрать ZIP 13:

```bash
bash "$HOME/projects/slooopipe-src/build-slooopipe-from-source-zip-wsl.sh" --component main --operation build-test --auth with
```

Для проверки изоляции отдельно собрать `--auth without`. Сравнивать варианты
по очереди: applicationId сохранён, APK могут обновлять один и тот же пакет.

## Границы проверки

Внешняя сборка ZIP17 предоставлена пользователем и проверена через ADB
в LDPlayer14 / Android14: локальный MP4 играет через NewPlayer, основные
управления работают. Звук на слух, физический телефон, PiP и process-death
не подтверждены. Здесь Gradle/компиляция и проектные unit-тесты не запускались.

Сетевой мост добавлен следующим этапом и пока проверен только статически.
Нужны новый APK с основного компьютера и проверка выбранного обычного ролика:
изображение, звук, пауза, перемотка. См. [NETWORK_NEWPLAYER_STAGE2.md](NETWORK_NEWPLAYER_STAGE2.md).
Работа одной YouTube-трансляции через старый PlayerService наблюдалась
пользователем; она не подтверждает новый сетевой путь или все ролики YouTube.
