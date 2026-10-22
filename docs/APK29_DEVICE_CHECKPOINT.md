# APK29: LDPlayer и исходники ZIP30

Дата: 2026-10-22. Serial emulator-5554, LDPlayer14, Android14/API34, density2.
Во время циклов1280×720, начало/конец720×1280. Данные приложения и история не очищались;
удалялись только элементы временной playback queue. Личные файлы не удалялись.
В конце движок освобождён; приложение оставлено в истории, rotation lock0.

## Происхождение

APK SHA256 `7a172673ca6a461c59736cd14687fa8ed82324684c3a0b36c173288cbfadf3cb`,
45,426,082 байта. Побайтно совпадает с переданным app-debug-signed.apk.
Package org.schabi.newpipe.debug; version0.29.1/code1015, update2026-10-22 15:12:45.
DEX содержит новый network.mini_restored hidden_to_collapsed; событие реально сработало.
Source ZIP29 SHA256 `d8a28de2beb86e19a861170cc9ed6ffe8e7ea7b657e17417eb98e37bfaa8836f`,
commit5a636c1d1e6289a9fa00313c9fd3c04b7b5af838. Источник заявлен пользователем и
сопоставлен с APK/маркером исправления; новый build-main.log и test XML не предоставлены.
Это не полная аттестация source-to-APK. Auth-with — заявленный режим основного цикла;
auth-without runtime/build/test не проверен. Локальной компиляции/Gradle не было.

## Подтверждено на APK29

- Запуск с сохранённой сетевой очередью и видимой мини-панелью.
- Network -> local MP4/180s -> Back сохраняет локальную мини и воспроизведение (04).
  Local Close -> история -> Play all -> фон возвращает network mini (05).
  Network Close убирает сессию и панель без самостоятельного восстановления (06).
  Прямой «В фоне» из истории вновь показывает network mini (07); в логах два события
  network.mini_restored. Исправление ZIP29 подтверждено в этих сценариях.
- Audio queue -> video fullscreen -> embedded возвращает реальное изображение (08–10).
-20 циклов очереди:10 native +10 из мини; в каждой серии5 paused/5 playing,
  три с выбором, семь без. В native06 и mini10 поймана краткая BUFFERING; есть последующее
  восстановление и две дополнительные успешные проверки native/mini на PLAYING.
  Все10 пар moving images основной серии и обе дополнительные различаются в области
  видео; у14 сценариев без выбора нет смены item, пауза сохраняет позицию с погрешностью
  менее100ms. Геометрия Compose0,0–1280,276, holder395,0–886,276 внутри родителя.
  Native queue button штатно скрыт библиотекой при единственном элементе; это не ошибка
  приложения. Для такой очереди используется вход из mini/audio UI.
- Play all из начала истории, selection0/1 и синхронное изменение metadata; середина
  списка отдельно не проверена. Ручной обычный Play в этом цикле отдельно не проверен.
- Удалены шесть следующих элементов временной очереди, сохранены два VOD. Текущий
  ролик перемещён1->0->1; затем удалён элемент перед ним. Каждый раз paused11351ms,
  текущий ролик тот же, индекс согласован (14–17). История не удалялась.
- MediaSession fast-forward изменил paused11351->26351ms. Через реальные media keys
  выполнена перемотка к626351ms при duration634566ms. При repeat-one после фактического
  окончания тот же ролик продолжил играть с3333ms (21/22). Без повтора плеер остановился
  на634626ms, MediaSession STOPPED, без самоперехода (25/26).
- Удаление последнего текущего элемента освобождает движок/MediaSession (30), но UI
  закрывается некорректно: см. FAIL ниже. Нельзя считать всю F06 пройденной.
- Не наблюдалось вылета приложения; retained current-PID log не содержит FATAL EXCEPTION.
  Это не гарантия отсутствия ошибок за пределами сохранённых данных. Слышимый звук
  человек на этом APK не подтверждал; дорожки/кадры/счётчики не заменяют слуховую проверку.

## Два подтверждённых дефекта

### Настройки UI не читаются при новом подключении к существующему движку

Выбран Repeat all (27). После выхода в сведения/Back и входа в mini queue новый экран
показывает No repeat (28). Следующее нажатие показывает Repeat currently playing (29),
поскольку движок сохранил All и перешёл All->One: ошибка именно в исходном UI состоянии.
Реальный repeat-one и остановка без повтора работают; UI after reopen — FAIL.

В NewPlayerViewModelImpl.installNewPlayer listener получал только будущие изменения.
Новый UI сохранял DEFAULT вместо актуальных repeat/shuffle/parameters/silence и статуса.
ZIP30 после addListener и до awaitCancellation заполняет UI текущими семью значениями:
playing, playbackActive, loading, repeat, shuffle, playbackParameters, skipSilence.
Движок и его настройки не изменяются, периодическое polling не добавляется.
Repeat воспроизведён на устройстве; остальные поля исправлены по той же установленной
причине отсутствия начального чтения и ещё требуют отдельной проверки.

### После удаления последнего элемента остаются пустой host и старая mini

В30 MediaSession приложения уже нет, но host показывает старые title/кнопки. После
Back в31 видна network mini со старым title и кнопкой «Пауза», хотя движок отсутствует.
Это FAIL очистки UI после последнего элемента, без реального фантомного воспроизведения.

ZIP30:
- NewPlayerActivity закрывается, когда ранее прикреплённое media исчезло и нет ни
  сетевой сессии, ни local URI. Начальный picker и загрузка принятого запроса не закрываются.
- hasNetworkSession учитывает принятую queue даже до готовности её playlist.
- VideoDetailFragment при пустой завершённой NewPlayer session скрывает старую mini
  и сбрасывает pause icon. При появлении новой сессии во время анимации hide она
  возвращается collapsed без cleanUp/release новой очереди; при отмене hide флаг сбрасывается.
- Прежний MainActivity fix ZIP29 сохранён.

## Static/source и следующий цикл

Production delta ZIP29->ZIP30: четыре файла, библиотечный ViewModel и три host файла.
Builder09, подпись, версии зависимостей, auth/extractor и отдельный demo не менялись.
Host ktlint и Java Checkstyle PASS, diff-check PASS. У библиотечного файла30 старых
style findings до/после, точное совпадение с поправкой на13 строк; parser errors0,
новых findings0. Это не полный lint PASS файла и не компиляция. Зеркальные unit-тесты
на чтение свойств не добавлялись: нужны реальные lifecycle/device проверки и WSL tests.

Generic cumulative patch для официального NewPlayer212ca1c8d941 обновлён; строгий
apply-check на неизменённом pinned архиве PASS, официальный Hilt сохранён.
Upstream/demo compilation, refactor port и принятие PR не выполнялись; публикаций нет.

Нужно собрать main/build-test/auth with из точного ZIP30, установить поверх и передать
полный run-* с test XML/count/isolation. Затем проверить:
1. All/One/Off и shuffle after close/reopen/background/popup, speed/silence values.
2. Последний элемент, вся очередь, закрытие host/mini, повторный вход и новая очередь
   во время hide; picker cancel и local/network replacement не должны закрываться ошибочно.
3. Повтор10+10 queue cycles и затронутых mode/lifecycle сценариев, затем остальная матрица.

[Матрица APK29](APK29_ACCEPTANCE.csv) не переносит PASS на будущий APK30.
Не завершены consent/stale/settings, история preferences, дубликаты/enqueue/auto-next/
repeat-all/shuffle фактических переходов, pagination, source options/errors/retry,
актуальный live/DVR на этом APK, notification/focus/noisy/recovery, оба auth режима,
физический Android16 и слуховая проверка. Весь проект не объявляется принятым.
Доказательства: delivery/zip29-device-test-20261022-01; отдельный ZIP-отчёт содержит
выбранные кадры/состояния/очищенные события и ключевые исходники, без APK/сессий аккаунта.
