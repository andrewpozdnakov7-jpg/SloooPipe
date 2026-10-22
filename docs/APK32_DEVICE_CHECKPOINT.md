# APK32: широкая проверка NewPlayer и исправления ZIP33

Дата: 2026-10-22. LDPlayer14, Android14/API34, emulator-5554, portrait720×1280/density2.
APK SHA256 `a42439f13958d0b48616d5d7e6ac94bd5f8f92175b60a3ae23fd26d98c6f40b8`,
45,426,082 байта. Хеш извлечённого установленного APK и проверка в конце совпадают.
Пользователь установил новый APK после передачи ZIP32; это source claim, не полная
source-to-APK аттестация. WSL build-main.log/test XML не переданы. Ожидаемая база кода
ZIP32: `d638f97f432d74a89cd1231fba5b7e918bef31ca`.

Данные приложения/история не очищались. Удалялись только элементы временной очереди.
Для проверки восстановления сделан один явный force-stop: PID21325→4646. Это не вылет
и не обещание непрерывного воспроизведения после force-stop. Временное включение
подтверждения очистки возвращено в исходный Off; rotation lock0, speed/pitch1 и skip
silence Off восстановлены. Конец: Main/история, актуальная network mini, PAUSED5038ms.
В сохранённом логе конечного PID0 FATAL EXCEPTION; вылет в ходе проверок не наблюдался.

## Что подтверждено на этом APK

- **ZIP32 host close работает:**03/04 last delete автоматически закрыл пустой
  NewPlayerActivity, вернул Main, убрал mini и MediaSession.05 новый Play all снова
  воспроизводит видимое видео. Это закрывает конкретный FAIL APK31.
- **20 queue cycles:** native01–10 и mini01–10, поочерёдные play/pause, шесть операций
  выбора и возврат без выбора. Во всех20 intent соответствует ожидаемому, во всех10
  playing image pairs есть изменение кадров. Без выбора текущий item сохранён;
  максимальный drift паузы9ms. На position0 у выбранного paused item был чёрный кадр;
  последующий Play и paused seek15000ms дают реальное изображение,26. Это не доказательство
  декодирования каждой возможной первой картинки любого источника.
- **Очередь:**08/09 current reorder1→0→1 сохраняет PAUSED18045ms;10 delete-before
  сохраняет item/position.17 add-next/append оставляют два дубликата;22–24 next/previous
  выбирают отдельные slots.25 current delete переходит к следующему paused/position0.
- **Фактическое окончание:**13 repeat-one возвращается с конца к PLAYING4102/7409ms;
  16 Off останавливается596520ms;21 All последний→первый;96/97 обычный auto-next при
  repeat Off:4K634565ms→BBB index1 PLAYING30ms.18/19 и95 сохраняют settings после reopen.
  Полная последовательность shuffle и длинная lazy-pagination очередь ещё не приняты.
- **Обычный Play:**75 History/detail autoplay и76 обычная кнопка Play открывают NewPlayer.
  Тот же single queue возобновляется без лишнего подтверждения;98 другой Play работает
  при исходной выключенной настройке.93a context start-from-here дал single-item queue,
  поэтому не выдаётся за Play all с середины списка.
- **Качество:**27 смена на360p на паузе сохраняет15000ms;28 возврат720p60 при Play
  сохраняет Play. Реальный VOD plan:2 progressive parts,2 inline DASH manifests, merged A/V.
  Отдельный muxed progressive и удалённый DASH manifest в этом цикле не подтверждены.
- **Параметры:**30 actual MediaSession speed1.25;31 диалог после reopen сохраняет1.25.
  37/38 независимый pitch1.25 при speed1 и включённый skip-silence сохраняются после
  нового UI binding. Reset возвращает1/1/false. Кнопка скорости при этом неверно пишет1x — FAIL ниже.
- **Режимы/Surface:**33–36 fullscreen↔embedded и portrait↔landscape сохраняют картинку
  и PAUSED37726ms.39–42 Home/default background, Main reopen и video return сохраняют
  текущий item/воспроизведение.43–46 popup показывает видео, Play/Pause, перемещение
  в пределах viewport и возврат с65390ms на паузе.47–50c PiP имеет реальные pinned bounds,
  движущиеся кадры, Play/Pause и разворачивается обратно в fullscreen/embedded.
  Автоскрытие системного PiP-меню требовало повторного наблюдения; промахи жестов
  не классифицировались как падение приложения. Rotation обрабатывается configChanges;
  это не отдельный тест in-process Activity recreation.
- **Система:**51–54 notification Pause/Play/open работают;80 Close освобождает сессию.
  Во время переходов не наблюдалось второй одновременно playing app-сессии. Это не
  heap/leak proof или проверка физической гарнитуры/audio focus.
- **Local:**57–60 picker/cancel сохраняет сетевую PAUSED182584ms.61/62 Cancel диалога
  сохраняет ту же очередь/позицию;63 OK запускает local mp4.64 seek/pause,65–69 local
  mini Play/Pause/reopen/Close;70 новый пустой selector остаётся открыт без MediaSession.
  71–74 local m4a PLAYING, seek/pause20332ms, mini и Close.75 новая сеть после local Close.
- **Consent:**77/78 Cancel другой network request сохраняет PAUSED211281ms.79–81 Close
  очереди через notification при открытом dialog делает его stale: OK не запускает
  поток, событие queue.consent_stale. Account-change variant отдельно не проверен.
- **Live/HLS/DVR:**86–90 реально играет EXILE live, plan HLS/parts1, движущиеся кадры,
  engine.timeline live=true seekable=true duration3600000ms. Pause, rewind5000ms
  (3577302→3572302ms) и resume работают. Legacy dumpsys показывает position=-1/speed0
  и не используется как доказательство остановки: engine timeline и изображение движутся.
  84/85 другой эфир отвечает LIVE_STREAM_OFFLINE, event will begin in a few moments;
  Retry корректно повторяет unavailable. Это запланированный эфир, а не установленный IP-ban.
- **Saved queue:**93–95 после force-stop/relaunch восстановлены2 entries в прежнем
  порядке, выбран index1, PAUSED30000ms, Repeat all и Shuffle on. Новый процесс не
  начинает играть сам. После этого настройки вернули в Off/Off;97 auto-next проверен.
- **Звук:** пользователь явно подтвердил на текущем APK32: «Да, звук нормальный».
  Это человеческое подтверждение в LDPlayer, не физический Android16 и не отдельная
  акустическая проверка каждой дорожки/режима.

## Два найденных дефекта и ZIP33

### 1. Кнопка скорости всегда1x

TopUI.kt содержит literal1x, хотя actual speed и диалог корректны. ZIP33 выводит
uiState.playbackParameters.speed через уже существующий formatter. Это generic UI
изменение, не новый engine или новый источник. На установленном APK32 оно ещё не исправлено.

### 2. Ghost mini после закрытия во время другой detail page

В79/80 при открытом подтверждении другой страницы очередь закрыта через notification.
81 stale OK правильно отклонён. Но после Back к Main остаётся strip со старым4K title
и Play/Close, без активной MediaSession и queue button (83). Звука нет; зависло UI.
selectAndLoadVideo/setInitialData сбрасывают newPlayerSelected для просмотра новой
страницы. Cleanup и Back ошибочно теряют принадлежность overlay к NewPlayer и могут
вернуться в старый history stack.

ZIP33 хранит отдельный @State newPlayerOverlayOwned до cleanUp. Новая detail page
не меняет владельца старой сессии. Back/onResume не восстанавливают legacy queue.
При COLLAPSED: если network session жива, mini привязывается к её текущему item;
если сессии больше нет, strip скрывается. Expanded preview до решения пользователя
сохраняется. Принятый pending request считается сессией через hasNetworkSession,
а поздний HIDDEN callback по-прежнему не закрывает новую replacement queue.

Это изменение lifecycle/отображения требует новой сборки и повторного device-цикла.
Старые83 и32 FAIL не объявляются исправленными на устройстве только по исходникам.

## Статика и переносимость ZIP33

Production delta к ZIP32 — два файла: VideoDetailFragment.java и embedded TopUI.kt.
Checkstyle изменённого Java PASS. TopUI ktlint:27 старых замечаний→24; новых правил/
ошибок парсера нет, весь legacy файл не объявляется lint-clean. git diff --check PASS.
Gradle/JVM/build здесь не выполнялись;51 app/NewPlayer JVM-кейс не выдаётся за executed.

При проверке переносимого patch-пакета обнаружены четыре пропущенных новых файла:
PlaybackRequestGate/PlaylistDuration и два contract test файла. Они уже были в основных
исходниках приложения (тесты — в app suite), поэтому это не причина ошибки текущего APK.
ZIP31/32 generic patch проходил text apply-check, но был неполным. Новый cumulative
patch включает13 файлов и7 production-contract cases, actual speed label и сохраняет
официальный Hilt. Он заново применён на свежем exact pin212ca1c8d941 с проверкой наличия
новых файлов и текста TopUI. Upstream library/demo не компилировались, PR не отправлен.

## Что остаётся

[Матрица APK32](APK32_ACCEPTANCE.csv): отметки относятся к конкретным описанным
подсценариям; строки FAIL могут описывать один и тот же дефект. Полная приёмка не достигнута.

Нужны оба auth build/test/isolation с полными XML/counts, physical Android16, новый
APK33 и regression matrix, длинные feeds/pagination, rapid async races/cancellation,
все history/resume preference комбинации и explicit timestamp/recovery, shuffle actual
sequence, другие source/track/subtitle/chapter/preview комбинации, media fault recovery,
потеря сети, физические noisy/headset/audio focus и отдельная Activity recreation.
Не переносить PASS APK32 на APK33 без retest, особенно overlay/consent/late-hide paths.

Источник доказательств — отдельная локальная папка zip32-device-test-20261022-01.
Общие logcat, APK, account session, signing и notification screenshots/XML с сетевыми
идентификаторами не включаются в публичный Git и очищенный report ZIP.
Исходные архивы ZIP32 и ZIP33, builder09 и зависимости не менялись. После этого
цикла опубликован код ZIP33 с обновлённой документацией; см. [запись публикации](SOURCE_PUBLICATION.md).
Это публикация исходников, а не подтверждение исправлений на новом APK. Стабильного релиза нет.
