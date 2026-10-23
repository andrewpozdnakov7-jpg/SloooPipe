# Дополнение ZIP35: исправление в NewPipe-хосте

APK34 подтвердил кнопку скорости, но пересоздание paused Activity повторяет стартовый
Resume Intent и снимает паузу. ZIP35 ограничивает эту команду первоначальным onCreate;
текущее состояние живого движка и явный onNewIntent Play не подменяются.
[Доказательства и ограничения](APK34_DEVICE_CHECKPOINT.md). Нужен новый внешний build/retest.
Generic NewPlayer patch13/7 не менялся; это правка NewPipe-specific host.
Ниже — исторические этапы, их прежние статусы не являются текущей приёмкой.

---

# Дополнение 2026-10-23: ZIP34

APK33 подтвердил описанные сценарии overlay/consent, 10+10 циклов очереди, popup/PiP
и локальный mp4. Найден перенос подписи скорости; ZIP34 меняет только кнопку TopUI.
[Текущие результаты](../RESULT.md) фиксируют границы проверки.
Новый APK, полный набор тестов и приёмка ещё требуются; звук отложен до телефона.
Cumulative generic patch включает 13 файлов / 7 contract-кейсов, применён к исходному
pin с сохранением Hilt; upstream build/demo/PR не выполнялись.

Ниже — исторические этапы; их утверждения о неподтверждённом APK33 относятся к той дате.

---

# Переносимость: уточнение ZIP33

Новый generic cumulative patch содержит13 файлов: к прежним изменениям добавлены
actual speed label, два пропущенных logic файла и два contract test файла (7 cases).
Ранее text apply-check не выявлял неполный состав новых файлов; основные app source
ZIP уже содержали нужную логику и host tests. Новый пакет проверен на свежем exact
pin212ca: check, actual apply, наличие новых файлов, TopUI text и сохранение Hilt.
Host overlay ownership — отдельно в NewPipe. Compilation/demo/upstream PR не выполнены.
Текущие runtime результаты: [APK32](APK32_DEVICE_CHECKPOINT.md). Новый APK33 нужен.
Ниже сохранена история исследования и предыдущих проверок.

---

# Переносимость: текущее уточнение ZIP32

Generic cumulative patch ZIP31 исправляет initial skip-silence через `newPlayer.skipSilence`
и прошёл `git apply --check` на свежем официальном pin212ca с сохранением Hilt.
ZIP32 меняет только host NewPlayerActivity и не меняет generic библиотеку.
Патчи и подробные отчёты подготовлены как локальные review-артефакты; они не входят
в опубликованное дерево отдельными patch-файлами. Сам изменённый исходный код доступен
в этом репозитории. Upstream compilation/demo/PR и полный refactor-порт не выполнены.
Текущий runtime-статус — [APK31](APK31_DEVICE_CHECKPOINT.md); ZIP32 требует новой проверки.

Ниже — сопоставление официальных refs, зафиксированное при исследовании 22 октября,
и история подготовки. Это не заявление о непрерывном обновлении до latest HEAD.

## Зафиксированное исследование 2026-10-22

## Обновление ZIP30

ZIP30 дополняет generic cumulative patch initial UI state snapshot в библиотечном
NewPlayerViewModelImpl. Патч проверен git apply --check на неизменённом официальном
212ca1c8d9419a19cb5aed7f0a61e22e8732a25c; официальный Hilt сохранён. Host cleanup
не входит в generic patch. Upstream/library/demo compilation не выполнены; ссылки
ниже на APK25–28 остаются историческими. [Checkpoint APK29](APK29_DEVICE_CHECKPOINT.md).


Подготовка передачи без PR, писем и публикации новых исходников.

| Проект | Используемый pin | Проверенный официальный ref |
| --- | --- | --- |
| NewPipe | `f46290f28ab44fa514bac0a9f0252fd7d2afb66f` | dev/HEAD `7436ba1010493515ff1a5f5d60aacdc6168c8500` |
| NewPipe/refactor | Не база кандидата | `455b6f0de6bbc135a51f2a77510863edee8b3cae` |
| NewPlayer Codeberg/dev | `212ca1c8d9419a19cb5aed7f0a61e22e8732a25c` | Тот же HEAD |
| NewPipeExtractor | `2f7d9da4ce82248637fdd3f8025086bd345f7fc2` | dev/HEAD `e8f89145a830b4d6cae32412007f24ea8b36c73a` |

[NewPipe](https://github.com/TeamNewPipe/NewPipe),
[NewPlayer](https://codeberg.org/TeamNewPipe/NewPlayer),
[Extractor](https://github.com/TeamNewPipe/NewPipeExtractor).
git ls-remote и official compare JSON сохранены в ZIP-отчёте.

NewPipe dev опережает pin на один translation commit: изменены 8 ресурсов app/shared,
functional playback не менялся. База зафиксирована для воспроизводимости и не является
буквальным последним HEAD. Extractor pin совпадает с используемым NewPipe. Сам Extractor
dev на 2 коммита впереди: docs/typos и исправление URL helper function в
YouTubeThrottlingParameterUtils. Они не включены автоматически и не объявляются решением
конкретной ошибки YouTube. Toolchain не обновлялся. В app добавлена прямая dependency
на уже используемый Media3 UI 1.9.2 для popup widget.

## Dev и refactor

Пользователь выбрал dev-основу на предыдущем этапе; APK25 измерен на ней. Первоначальное
исследование 20 октября с рекомендацией старого refactor сохранено как историческое.
Текущее refactor-направление проверено по pinned README, catalog, app Gradle и playback
entry points. README направляет новые функции в refactor. Это официальное направление,
но не согласование приёма именно нашей реализации.

В refactor уже Kotlin VideoDetailFragment, PlayerHolder, PlayerService, PlayQueue и
PlayQueueItem, Android App использует Hilt; ExoPlayer 2.19.1 остаётся. Подключения нашего
NewPlayer host в этих точках нет. Перенос playback decisions в Kotlin не требует
переносить весь рефакторинг экранов/KMP в dev. Следующий runtime-кандидат поэтому
продолжает проверенную ветку. Полный refactor checkout с нашей интеграцией пока НЕ
портирован/собран; карта ниже не выдаётся за такой результат.

| Компонент | Точка refactor | Адаптация |
| --- | --- | --- |
| PlaybackRequests/Consent | Kotlin detail/NavigationHelper | Перенести решения запуска/замены; убрать ранний stopService до consent |
| QueuePlayback/Repository | Kotlin PlayQueue/Item | Адаптировать свойства/методы/Java interop; Feed для fetch, Media3 для actual order |
| Runtime/host | Hilt App/provider, PlayerHolder | Одна app instance; сохранить подходящую Hilt wiring |
| History | HistoryRecordManager/Room/preferences | Сохранить существующую DB и семантику |
| Mini/UI bridge | Kotlin detail overlay/lifecycle | Читать NewPlayer, привязать listeners/Surface к lifecycle |
| Factory/repository/details | Android app source set | Не переносить Media3/Extractor/аккаунт в shared/commonMain |
| Optional auth | Отдельный opt-in feature/API | Не делать обязательной зависимостью общей библиотеки |

## Разделение патчей

В ZIP-отчёте дельты к ZIP25 разделены на NewPlayer, host/Kotlin, regression tests
и документацию. Это не патчи прямо к чужому refactor HEAD. Для Extractor/auth в
ZIP26 новых изменений реализации нет; пустые патчи/PR не создаются. Ранее добавленные
fork auth hooks остаются отдельной экспериментальной частью дерева.

Дополнительно подготовлен cumulative generic NewPlayer patch относительно официального
`212ca1c8d941...`, с путями `new-player/...`. Он сохраняет upstream Hilt annotations и
service injection, исключает наш provider/build catalog и включает 7 production-contract
JVM-кейсов для request gate/duration. Наша DI packaging разница приложена отдельно.
`git apply --check` подтверждает текстовую применимость к точному pin; upstream
Gradle/test-app/standalone этим не собраны и не проверены.

Добавлены optional release callbacks и queue API. Для API review нужны внешние constructor
call sites, включая trailing-lambda rescueStreamFault. Бинарная совместимость уже
скомпилированных клиентов не обещается. Generic patch и refactor map не приняты upstream.

## Подготовленные описания review

**NewPlayer: keep one session across async queue and UI changes.** Delayed replacement/
insert results must not replace a newer queue. Add commit-time ownership, stable IDs,
lifecycle cleanup, safe duration and Surface/selection fixes. Validation: exact-pin apply
check; updated library/Hilt variant need compilation, tests and device acceptance.

**NewPipe: route playback and modes through a Kotlin NewPlayer host.** Play/background/
popup share one player and actual Media3 playlist. Consent precedes side effects. Separate
feeds, extraction, sources, history and views. Review on this pinned dev base; adapt Kotlin
property/DI/navigation boundaries for refactor. Validation: static lint/XML; both auth
builds and new APK matrix pending.

**Extractor/auth/fork packaging.** No new feature patch in this candidate. Retain existing
provenance/license and optional isolation. Existing sign-in/SABR limits remain explicit.

Патчи предназначены для изучения. Автоматическая отправка upstream не выполнялась.

## Первичные API-референсы

[MediaSessionService lifecycle](https://developer.android.com/media/media3/session/background-playback),
[PiP](https://developer.android.com/develop/ui/views/picture-in-picture),
[Media3 Player](https://developer.android.com/reference/androidx/media3/common/Player).
Host использует существующий MediaSessionService; popup не создаёт вторую сессию.
Subtitles обрабатывает современный DefaultMediaSourceFactory, без deprecated
SingleSampleMediaSource/legacy decoder path. Поведение pinned Media3 проверяется
в WSL/на устройстве; чтение документации его не доказывает.

## Уточнение ZIP28 после проверки APK27

Обновлён generic cumulative patch для official NewPlayer212ca1c8d941: playQueue
сохраняет audio/fullscreen PlayMode и задаёт EMBEDDED_VIDEO только из IDLE.
Patch создан поверх прежних generic изменений с сохранением Hilt и заново прошёл
`git apply --check` на свежем архиве официального pin (12 изменённых файлов).
Это apply-check, не compilation/tests/demo. Embedding-only patch не изменился.
Host mode bridge, popup sizing, local ownership и их10 новых JVM-кейсов находятся
в NewPipe части; полного refactor-порта по-прежнему нет. Новых upstream PR не было.
