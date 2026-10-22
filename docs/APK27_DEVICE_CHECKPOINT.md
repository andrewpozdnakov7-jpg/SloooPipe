# APK27: проверка устройства и исправления ZIP28

2026-10-22, LDPlayer14 / Android14 API34, emulator-5554.
Установленный APK: SHA256 `1a380a8ba3d6a041c0e5d5ac4fefb3f9338442243f3b38984cdf80c613dfa7c1`,
45,426,082 bytes; version0.29.1/1015; lastUpdateTime2026-10-22 13:15:10.
Пользователь связывает его с ZIP27, commit d20c724f2879f6d45d79648e16061bcf6b990c7e.
WSL build/test log не предоставлен: это не криптографическая аттестация исходников.

## Подтверждено на APK27

- После чистого перезапуска без удаления данных MainActivity открывается. В начале
  показывался сохранённый ACRA-отчёт APK26; в DEX APK27 уже присутствует вызов
  ThemeHelper.resolveColorFromAttr вместо MaterialColors.getColor.
- NewPlayer воспроизводит Big Buck Bunny60fps и второй Big Buck Bunny. Через системные
  media keys работают play/pause. Видео видно, кадры меняются, выбраны аудиодорожки,
  логируется engine.audio_advancing. Слышимый звук на APK27 человек ещё не подтверждал.
- 720p60 → 480p MPEG-4: позиция21735 → 21738мс, пауза сохранена. Декодер реально
  переключился1280×720 → 854×480. План split A/V, parts=2, inlineDash=2.
- Десять циклов очереди из самого NewPlayer: pause/play, с выбором и без. Все состояния
  совпадают с намерением; Compose/ViewFactoryHolder имеют bounds0,0–720,405.
  Все пять playing-пар изображений различаются. Выбор второго/первого элемента работает.
  Чёрный начальный кадр на паузе в позиции0 не объявляется потерей поверхности.
- Play all из начала истории создаёт очередь. Play all с середины не проверен.
- Background после Home продолжает ту же сессию; popup показывает видео; возврат из
  popup сохраняет паузу/позицию. На уровне dumpsys видна одна активная MediaSession.
  Наличие пустого legacy PlayerService для detail UI не означает второй играющий движок.
- PiP: реальная pinned task, bounds864,472–1248,688 на экране1280×720; видео, play/pause,
  возврат из PiP сохраняет позицию31823мс. Это не полная проверка всех способов закрытия PiP.
- SAF cancel сохраняет исходную сетевую сессию на паузе31823мс. Локальный MP4/180с
  воспроизводится. M4A/30с воспроизводится; после уже скрытого network sheet его мини-панель
  сохраняет паузу, работает play/pause, повторное открытие и Close убирает сессию.
- Актуальный live BETBOOM MEDIA POKER: network.plan live=true kind=HLS, parts=1;
  настоящий Player.isCurrentMediaItemLive=true, seekable=true, duration3600000мс.
  После DVR seek позиция около2056976мс, liveOffset≈1592079мс, затем playing=true.
  Видео видно, кадры/аудио продвигаются. Наблюдались переходы BUFFERING/READY;
  долговременная стабильность и слышимый звук не проверены.
  position=-1 в dumpsys для live — ограничение legacy MediaSession, а не доказательство зависания.

## Найдено и исправлено только в исходниках ZIP28

1. **Аудио → видео даёт чёрный экран.** После Back с minimize=background очередь из
   мини-панели открывается в аудиорежиме. Встроенная кнопка Switch to fullscreen video mode
   меняла PlayMode библиотеки, но host presentation оставался AUDIO и запрещал видеодорожку.
   Доказательство: cycle-11, screenshot18, engine.track video selected=false при advancing audio.
   Добавлены PlaybackPresentationPolicy и runtime collector PlayMode. Popup/PiP и audio-only
   сохраняют отдельные правила; режимы host и NewPlayer больше не должны расходиться.
   В generic playQueue убран принудительный сброс активного audio/fullscreen режима:
   только IDLE получает стартовый EMBEDDED_VIDEO. Иначе новый collector сломал бы запросы
   background при асинхронном commit очереди. Уточнение найдено статическим разбором.
2. **Popup обрезается в landscape.** При960px ширины видео+кнопки+верхний offset выходили
   за720px экран. Теперь измеряются переведённые кнопки/drag handle, учитываются системные
   insets, ограничиваются videoHeight/top. Rotation перестраивает только окно, сохраняя engine.
3. **Возврат из первого local после network закрывает local.** LocalPlaybackMini скрывает
   прежний network detail sheet, а его cleanUp безусловно освобождал любой NewPlayer.
   В13:39:43 видно ExoPlayer/MediaSession release до onStop; локальная панель исчезает.
   Теперь bridge stopNetworkPlayback закрывает только активную сетевую очередь, не local URI.
4. **Длинные подписи режимов.** PiP по-русски разбивался на четыре строки в узкой колонке.
   Кнопки разложены по две в ряд, длинный заголовок ограничен двумя строками.
   Local picker теперь явно предлагает видео или аудио; инфраструктурная подсказка убрана.

Новая проверка исправлений на устройстве ещё НЕ выполнена. APK27 не содержит эти изменения.

## Матрица и границы

[Отдельная матрица APK27](APK27_ACCEPTANCE.csv) не переносит PASS APK25/26.
Серия мини-панели прервана после первого audio-mode цикла и воспроизведённого black-video:
оставшиеся9 не считаются пройденными. В APK28 нужны заново обе серии10+10, затем остальные
queue/consent/history/format/notification/focus/lifecycle сценарии. Физический Android16
не подключён. Настройки подтверждения, repeat/shuffle в конце ролика, pagination, удаления,
audio track/subtitles/speed/chapters/preview/Retry/network-loss не получили PASS этого цикла.

После тестов APK27 оставлен на паузе; rotation lock восстановлен0. Для popup через системный
UI включено SYSTEM_ALERT_WINDOW (до теста было denied). Пользовательские данные не очищались.
Создан только тестовый M4A в Download из ранее созданного тестового MP4; личные файлы не удалялись.

## Source/static/WSL/upstream

ZIP28 включает builder09 с ранее согласованной очисткой RAM. Базовые версии зависимостей,
подпись, Extractor/auth implementation и отдельный корневой NewPlayer demo не менялись.
Host package содержит51 JVM-декларацию тестов (41+6 presentation+4 popup), но здесь они
не исполнялись. Статически: ktlint host/tests, Checkstyle изменённого Java, XML, diff check,
scoped delta Gitleaks; это не компиляция и не независимый аудит безопасности.
Generic cumulative patch обновлён для официального212ca1c8d941; git apply --check на свежем
официальном архиве PASS. Hilt/embedding-границы сохранены. Upstream build/tests/demo и полный
refactor port не выполнены. Новых PR/push не было.

Нужно собрать main/build-test/auth with из конкретного ZIP28, сохранить весь run-* с тестами,
установить поверх текущего APK. Auth without build/test/isolation — отдельный обязательный
запуск до итоговой приёмки. Builder09 не требует нового копирования, если уже обновлён.
Реальная очистка RAM на основном WSL здесь не проверялась.

Доказательства: delivery/zip27-device-test-20261022-01; исходный ZIP/отчёт ZIP28 лежат отдельно.
Сырой APK, приватные auth-данные и неочищенные общие logcat в отчёт не включаются.
