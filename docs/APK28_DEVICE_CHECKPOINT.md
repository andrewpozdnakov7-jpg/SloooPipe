# APK28: проверка устройства и исправление ZIP29

Дата: 2026-10-22. Устройство emulator-5554, LDPlayer14, Android14/API34,
720×1280 и 1280×720, density2. Данные приложения не очищались. После проверки
приложение оставлено на паузе, rotation lock0. Новый APK29 ещё не собран/установлен.

## Происхождение

Установленный APK побайтно совпадает с переданным app-debug-signed.apk:
SHA256 `75de773353ec005a891a1ea3c9ed4a6805a9bfa7d6000649ada38bc543fbc7a3`,
45,426,082 байта, package org.schabi.newpipe.debug. Полные package metadata сохранены.
APK28 отличается от APK27. Проверка DEX подтвердила реальные вызовы policy,
popup bounds и stopNetworkPlayback; распаковка совпадает с APK.
Источник: ZIP28, commit a3676c8c8a8d9b930927c63b867e6e9093a6540c,
ZIP SHA256 `0fbda80b2d201f8398340838902c2b316882e745dda14ee4eb5daf1ee07b200a`.
Переданный build-main.log ссылается на work с этим SHA и заканчивается BUILD SUCCESSFUL
in28s; указаны app:testDebugUnitTest, youtube-auth-api:test, shared:jvmTest,
verifyAuthIsolation и assembleDebug. Это не криптографическая аттестация сборки;
XML/count отдельных тестов и полный STATUS/run reports не переданы. Auth-with —
режим основного цикла; auth-without не проверен.

## Что подтверждено именно на APK28

- Сетевой VOD Big Buck Bunny 60fps воспроизводится через NewPlayer, изображение видно,
  позиция MediaSession движется. Вход через системную ссылку/«Показать сведения»
  с настроенным автозапуском; отдельный ручной Play в этом цикле не проверен.
- Background -> мини-очередь -> audio UI -> Switch to fullscreen video mode:
  настоящее изображение вернулось, позиция сохранилась, пауза после возврата работает
  (05–09). Старый black-video APK27 не повторился. Это один переход, не вся матрица.
- Popup в landscape: frame [160,48][1120,720], нижние time/settings controls видны.
  Portrait после поворота: [90,144][630,628]; перетаскивание вниз:
  [180,796][720,1280]. Все внутри display. Паузу при открытии сохранил, затем play
  и поворот продолжили текущий элемент без сброса. Возврат из popup в host работает
  (10–14, popup-bounds-analysis.json).
- Network -> SAF -> MP4/180s -> Back сохраняет локальную сессию и мини-панель.
  Пауза из мини и повторное открытие сохраняют 23578ms. Close завершает сессию;
  перед историей активной MediaSession приложения уже нет (17–20).
  Локальная история не появилась среди сетевых записей. Audio-only отдельно не проверен.
- Play all из начала истории открывает NewPlayer с реальной очередью (21/22).
- Все10 открытий/закрытий очереди из самого NewPlayer завершены: пять playing,
  пять paused; три с выбором другого элемента, семь без выбора. Статусы соответствуют
  намерению, active item меняется0/2, поверхность остаётся в0,0–720,405. Все пять пар
  playing-кадров различаются (cycle-image-analysis.json). Paused на позиции0 после
  выбора Big Buck Bunny показывает чёрный первый кадр; следующий play отображает
  изображение без переоткрытия Activity (native03/04 и09/10).
- В сохранённом текущем PID log нет FATAL EXCEPTION. Это не доказательство отсутствия
  любых ошибок вне сохранённого временного окна. Слышимый звук человек на APK28 не подтверждал.

## Новый воспроизведённый дефект

После local -> Close -> история -> Play all -> фон активная сетевая MediaSession
есть, но мини-панель отсутствует. Повторный «В фоне» из истории также её не возвращает.
В23-missing-mini-activity.txt существующий fragment_player_holder имеет bounds
0,1232–720,2464 внутри content высотой1232: весь sheet за нижней границей.
23/24 PNG и XML подтверждают отсутствие элементов мини-панели при живой сессии.
Серия10 входов из мини-панели не началась: automation остановлена на недоступном
входе. Это дефект приложения, подтверждённый отдельно от ошибки поиска UI label.

Причина: LocalPlaybackMini намеренно скрывает старый network detail sheet.
MainActivity.openMiniPlayerIfMissing создаёт sheet лишь при отсутствии fragment,
а существующий hidden fragment не раскрывает. QUEUE_CHANGED обновляет его содержимое,
но не возвращает STATE_COLLAPSED. Результат — новая очередь без видимого управления.

ZIP29 меняет только MainActivity в production: при активной именно сетевой очереди
существующий STATE_HIDDEN возвращается в STATE_COLLAPSED. Уже collapsed/expanded
состояния сохраняются; локальный banner и legacy player не запускают этот путь.
Диагностическое событие: network.mini_restored hidden_to_collapsed.
Ни плеер, ни очередь, ни позиция при этом не пересоздаются.

Статически: изменённый Java прошёл Checkstyle13.8 с конфигурацией проекта, diff-check.
Нового зеркального unit-теста на условие не добавлено: требуется реальный lifecycle/UI
regression, выполнить на установленном APK29. Gradle/проектные JVM тесты здесь не запускались.
Версии зависимостей, auth, подпись, builder09, embedded NewPlayer и корневой demo не менялись.

## Следующий device-цикл и границы

Сначала APK29: local -> Close -> история -> Play all / В фоне / В окне; мини должна
появляться, показывать верный item, паузу и очередь. Повторить из paused и playing,
с прямым network replacement после local; проверить Close без самовосстановления.
Затем10 native +10 mini cycles с выбором/без, и остальные F01–F17 сценарии.
Отдельно нужны PiP на APK29, local audio, queue mutations/actual end repeat/shuffle,
consent/settings/stale, history preferences, source options/errors, notification,
focus/noisy, lifecycle/recovery, оба auth build/test/isolation и физический Android16.
Не переносить APK25/27/28 PASS на новый кандидат автоматически.

[Матрица APK28](APK28_ACCEPTANCE.csv) хранит отдельные статусы. Проект в целом
не объявляется принятым. Public GitHub и upstream PR не обновлялись этой работой.
Доказательства: delivery/zip28-device-test-20261022-01; исходники и отдельный отчёт29
передаются рядом. Raw APK/личные сессии/полные приватные logcat в report ZIP не входят.
