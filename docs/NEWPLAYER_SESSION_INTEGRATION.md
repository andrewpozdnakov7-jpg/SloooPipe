# Общая интеграция NewPlayer: кандидат ZIP33

APK32 прошёл описанные runtime проверки: [checkpoint](APK32_DEVICE_CHECKPOINT.md),
[матрица](APK32_ACCEPTANCE.csv). ZIP33 отделяет сохраняемый overlay ownership от
выбранной detail page и показывает actual speed в generic UI. Expanded preview не
заменяется старым playing item; collapsed overlay привязывается к active session или
скрывается после release. Guards pending metadata/late hide сохраняются. Новый APK
ещё не проверен; нижние разделы — история предыдущих состояний.

---

# Общая интеграция NewPlayer: снимок ZIP32

Текущее состояние: [APK31](APK31_DEVICE_CHECKPOINT.md), [матрица](APK31_ACCEPTANCE.csv).
ZIP31 исправил чтение skip-silence через API NewPlayer. ZIP32 наблюдает terminal
engine release для закрытия пустого экрана; APK32 ещё ожидает внешнюю сборку и проверку.
Описание владельцев ниже сохраняется; разделы ZIP28/30 — история отдельных изменений,
а не утверждение о прохождении текущей приёмки. Публичный снимок включает этот код;
upstream PR и полная приёмка не выполнены.

## История архитектурного checkpoint ZIP28

## Обновление ZIP30

APK29 подтвердил10+10 queue cycles, mini restoration после local, reorder/delete-before
и repeat-one/off на реальном окончании. ZIP30 добавляет read-only начальный UI snapshot
семи параметров движка и очистку host/mini после release. Принятая queue считается
сессией до завершения async playlist; запоздавший hide не должен закрыть replacement.
Новый APK30 ещё не проверен. Полные факты/границы: [APK29](APK29_DEVICE_CHECKPOINT.md).


Дата: 2026-10-22. APK27 проверен частично; найдены связанные режимные/ownership/UI
дефекты. ZIP28 содержит исправления и ещё не собран/не проверен на устройстве.
[Checkpoint APK27](APK27_DEVICE_CHECKPOINT.md), [матрица](APK27_ACCEPTANCE.csv).

## Управление сессией и режимами

Обычный Play, background и popup направлены в Kotlin `PlaybackRequests`.
`PlaybackConsent` проверяет фактический Media3-порядок, выбранное вхождение и
идентичность сессии/аккаунта. Пауза не означает отсутствие очереди. Отмена диалога
не запускает action; согласие действует однократно для захваченного snapshot.
Порядок и дубликаты учитываются. Enqueue не проходит через замену. Legacy detachment
и playback mutations выполняются внутри разрешённого action. Два пункта Start here
сохраняют Activity-контекст, чтобы подтверждение могло открыть диалог.

`NewPlayerRuntime` владеет одним NewPlayerImpl, его ExoPlayer и presentation.
При замене меняется playlist, а не движок/MediaSession. VIDEO/AUDIO/POPUP/PIP
не создают новый плеер. AUDIO отключает видеодорожку. Popup — WindowManager
surface/controller того же ExoPlayer, с разрешением поверх окон, ручкой перемещения,
Open player и Close. Ошибка окна сохраняет аудиосессию. PiP использует штатный
NewPlayer UI и Android API26+, если поддержан системой. Minimize-on-exit preference
определяет Home/Back: background, popup либо pause. Audio-only источники используют
поддержанную audio UI. Obsolete BACKGROUND_AUDIO/BACKGROUND_VIDEO не применяются.

Сетевая мини-панель пока остаётся View-частью VideoDetailFragment, читающей состояние
NewPlayer. Для локального документа без NewPipe service/detail page добавлен Kotlin
`LocalPlaybackMini`. Старые Player/PlayerService/media-browser классы не удалены.
Основные UI-маршруты play/background/popup/enqueue/timestamp не запускают legacy engine.
Android Auto/внешний старый MediaBrowser token не прошёл device-приёмку. Сохранённые
классы не доказывают совместимость. Явные external-player/Kodi опции остаются внешними.

## Владельцы состояния и отмена

| Состояние | Владелец | Ограничение/освобождение |
| --- | --- | --- |
| Active order/index/position, repeat/shuffle, play intent | Media3 в NewPlayerImpl | Queue mutations идут в него; последний delete закрывает сессию |
| Source pages и исходные offsets | QueuePlayback.Feed | Клоны PlayQueue для fetch; retired subscriptions disposed |
| Occurrence keys и ссылки на элементы | QueueMediaRepository | Удалённые entries очищаются; тяжёлые StreamInfo ограничены 8 |
| Источники и форматы | NetworkMediaRepository/Selector | Auth generation проверяется до публикации; 8 handoff entries |
| HTTP, merge A/V и lazy source | NetworkMediaSourceFactory/DeferredNetworkMediaSource | Job отменяется при release; queue/account проверяется повторно |
| Preview atlases | NetworkMediaDetails | LRU 8MiB, IO/Mutex, invalidation epoch |
| История | PlaybackHistory → HistoryRecordManager | Один writer, barrier до resume; old-account writes отклоняются |
| Presentation | NewPlayerRuntime | UI/overlay обслуживают тот же engine и MediaSession |
| Collectors/listeners | ViewModel/Activity lifecycle | Binding child job; removeListener в finally; старый VM не отвязывает новый |
| Сохранённая очередь | PlaybackSessionStore | AtomicFile/noBackupFilesDir; conflated IO writer, лимит 16MiB |

A→B отменяет replacement job и меняет generic PlaybackRequestGate token. Insert
ждёт захваченную замену и отбрасывается, если уже выбрана другая очередь. Anchor
вычисляется после async metadata, по mediaId/feed. Восстановление защищено revision.
Lookup mediaId регистрируется при commit: параллельное получение metadata не теряет
ссылку после prune. Service eviction сохраняет recoverable snapshot; пользовательский
Close очищает его. Preview/link/decoder retry связаны с item identity и не должны
оживать после смены ролика, окончания seek или закрытия экрана.

## Очередь, история и восстановление

Feed обслуживает pagination; фактический порядок/selection/reorder/delete/shuffle
принадлежат Media3. Страница добавляется после последнего сохранившегося элемента
своего feed. Удалённый feed не дописывает позднюю страницу в другую очередь. Fetch
использует исходный source offset после drag. Unknown/negative/overflow duration
даёт неизвестную сумму, а не ноль или исключение.

Сохраняются actual occurrence slots/order, index, position, repeat/shuffle и source
feeds для следующих страниц. Штатное восстановление сетевой очереди начинается на
паузе. Непрерывное воспроизведение после force-stop не обещается. Локальные URI и
position восстанавливаются из Activity state с SAF grant; дисковый queue store
предназначен для сетевых feeds. Page cursors могут включать служебные cookies самого
extractor. Private/no-backup файл не экспортируется в отчёт/Git/source ZIP. StreamInfo,
подписанные media plans и credentials auth-модуля туда не копируются. Смена аккаунта
и выключенная история очищают сохранённую очередь.

Просмотр записывается при реальном playing. Локальные файлы не входят в БД NewPipe.
Обычный VOD position сохраняется при pause/seek/transition/release; live/DVR offset
так не записывается. Explicit recovery, включая ноль, timestamp и unfinished history
имеют определённые приоритеты. Resume требует включённых watch-history и resume.
Position-in-lists остаётся настройкой отображения, а не запретом записи позиции:
сохранена семантика NewPipe. Account change инвалидирует source/cache/pending queue.

## Источники и действия

Подключены progressive, split A/V, HLS/DASH; YouTube adaptive VOD сохраняет
extractor-generated seekable DASH. Inline manifest идёт через data URI в
DefaultMediaSourceFactory, чтобы subtitles использовали современный parser path.
YouTube POST/range policy применяется к googlevideo/videoplayback, не к manifest hosts.

Sources в host открывает дополнительные progressive качества/аудиодорожки по
стабильному stream identity. Выбор сохраняет occurrence/index/position/play intent
при пересоздании source. Muxed audio не должен маскировать альтернативную дорожку.
Для manifest tracks, subtitles и speed используется UI NewPlayer. Chapters и storyboard
frames адаптированы из реальных данных; preview учитывает NONE/LOW/HIGH preference.
Таймкоды описания идут в существующую очередь либо создают новый запрос с явным recovery
и обычным подтверждением. Retry обновляет extractor data выбранного entry без новой
очереди. Live и post-live проверяются отдельно. Полный SABR/PoToken не добавлялся.

## Три части на передаче

| Часть | Реализовано/подготовлено | Проверено | До приёмки |
| --- | --- | --- | --- |
| 1: функции | Общие режимы, consent, feeds/restore/history/source adapters | Static этого кандидата; APK25 отдельно | WSL build/test и новая полная device-матрица |
| 2: архитектура | Kotlin decisions, generic/host граница, отмена/lifetimes, 51 JVM-кейс | ktlint/Checkstyle/XML/source review | Компилятор, оба auth-варианта, runtime ownership |
| 3: передача | Свежие refs, refactor map, раздельные patches/описания | Official source/ref checks и patch applicability | Полный refactor port не собран; upstream acceptance отсутствует |

[Матрица APK27](APK27_ACCEPTANCE.csv): есть отдельные PASS и связанные FAIL;
исправления ZIP28 ещё требуют новой сборки и полного device-цикла. [Upstream/портирование](UPSTREAM_PORTING_20261022.md).

## Следующий цикл

ZIP28 положить в `~/projects/slooopipe-src/sources`. Builder остаётся
`20261022-09-memory-cleanup`. Основной запуск явно выбирает архив, auth with,
build-test. Полная копируемая команда с SHA и фиксированным выходным APK-путём
находится в `BUILD-ZIP28.txt` рядом с архивами.

Underlying tasks: `:app:testDebugUnitTest :youtube-auth-api:test :shared:jvmTest
:app:verifyAuthIsolation :app:assembleDebug`. Builder использует xvfb-run для
headless shared tests. JDK21/SDK37.0/BuildTools36.0.0/signing папка сохранены.
Второй запуск с `--auth without` обязателен до окончательной приёмки. Без-auth APK
не нужно устанавливать поверх основного до согласованной проверки варианта.

Native builder оставляет уникальный out/run-* и печатает полные APK/Results пути.
Там находятся STATUS.txt, UNIT_TESTS-main.txt, Gradle log и скопированные reports.
Приёмка требует exit code и результатов задач. После установки пользователь сообщает
готовность; ADB/UI проверки выполняются здесь: F01–F17, обе queue-entry серии по
10 циклов, consent/stale, pagination/repeat/shuffle, history preferences, источники/
Retry, фон/popup/PiP, notification/focus/lifecycle. Android16 проверяется отдельно.

## Исправления после APK27

Runtime наблюдает NewPlayer.playBackMode через PlaybackPresentationPolicy: встроенный
переход audio→video включает видеодорожку, обратный переход отключает. POPUP/PIP и
аудио-only не перехватываются этим адаптером. Generic playQueue сохраняет активный
PlayMode; только IDLE получает EMBEDDED_VIDEO, чтобы асинхронный commit не отменял
background-запрос и не сбрасывал fullscreen.

Network detail cleanUp вызывает stopNetworkPlayback, ограниченный queue.active и
отсутствием local URI. Скрытие старой панели для LocalPlaybackMini больше не должно
освобождать локальную сессию. Это исправление источника; device retest ожидается.

Popup измеряет высоту drag/buttons, использует доступные WindowMetrics/insets,
PopupBounds ограничивает высоту видео и top. Rotation пересоздаёт только overlay,
не player/queue. Кнопки Activity идут по две в ряд, заголовок ограничен двумя строками.
Добавлены6+4 JVM-кейса production policy/geometry; здесь они не исполнялись.
