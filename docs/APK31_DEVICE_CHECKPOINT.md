# APK31: проверка и исправление закрытия host в ZIP32

2026-10-22, LDPlayer14 / Android14(API34), emulator-5554,720×1280/density2.
Данные приложения/история не очищались; удалялись только элементы временной очереди.
Установленный APK SHA256 `f4354f39e3ba9bded37e2543ec16f4d4325813cd15167196a1ecf1cda55702f7`,
45,426,082 байта; тот же хеш подтверждён в конце. DEX содержит screen.session_closed,
network.mini_hide_released и hasNetworkSession. Пользователь сообщил сборку из ZIP31:
commit54b11d6b088685a4a87c90ff6b9a19872673986c,
source ZIP SHA256 `53ed4b6f8cdaac52981314588e189ea9e862a23c8041a60e673b068e884ec63d`.
Сам APK и build-main.log на проверяющую машину не переданы; source claim и markers
не являются полной source-to-APK аттестацией. Auth-with — режим основного цикла.

## Подтверждено именно на APK31

- Приложение запускается без старой пустой mini (01).
- История -> Play all -> фон -> mini queue: выбраны Repeat all и Shuffle on (02).
  После выхода в детали, Back и повторного входа mini queue обе настройки отображаются
  правильно (03). Исправление initial settings snapshot подтверждено для repeat/shuffle.
  Speed/pitch/silence и другие режимы пока не получили отдельный PASS.
- Удалены семь следующих элементов временной очереди; единственный текущий VOD
  остаётся paused81944ms (04). После его удаления MediaSession приложения отсутствует
  (05). После Back старая mini скрыта (06): соответствующая часть ZIP30/31 fix работает.
- Новый Play all после опустошения очереди снова открывает NewPlayer: реальное видео
  видно, PLAYING84960ms (07), затем PAUSED85637ms (08). Приложение оставлено на паузе.
- За тесты вылет не наблюдался, retained current-PID log содержит0 FATAL EXCEPTION.
  Слышимый звук человеком и физический Android16 в этом цикле не проверены.

## Оставшийся FAIL и причина

После удаления последнего элемента в05 остаётся пустой NewPlayerActivity со старым
названием/кнопками. Автоматического screen.session_closed нет; нужно вручную вернуться
назад. Это частично неустранённый lifecycle defect APK29, не новая успешная приёмка.

NewPlayerImpl.removePlaylistItem сначала вызывает Player.removeMediaItem. Media3
синхронно отдаёт onMediaItemTransition(null), и currentlyPlaying становится null ещё
до вызова release()/beforeRelease и queue.close(). Коллектор Activity видит null,
но session ещё active и защита не разрешает finish. Затем release сбрасывает
currentlyPlaying в то же null: StateFlow не эмитит его повторно. Finish больше не проверяется.

ZIP32 переносит решение о закрытии из currentlyPlaying collector в отдельный collector
player.exoPlayer. Терминальный engine=null появляется при release после очистки владельца
сессии. Сохраняются guards: было прикреплённое media, не открытый picker, нет активной
выборки local metadata, сетевой queue или local URI. Новая загрузка/замена не должны
закрываться ошибочно. Нет delay/polling, второго движка или изменения зависимости.
Production delta — один NewPlayerActivity.kt, восемь добавленных/одна удалённая строка.
Generic library/API fix ZIP31 и cleanup mini не менялись; portable generic patch остаётся
проверенным на официальном pinned212ca из ZIP31, повторного generic apply-check не требуется.

## Границы и следующий цикл

Статически host ktlint PASS и diff-check PASS. Проект локально не компилировался,
Gradle/JVM не запускались. Новый APK32 ещё не собран, не установлен и не проверен.
Полный WSL run с test XML/count/isolation и второй auth-вариант не предоставлены.

На APK32 сначала повторить last deletion -> автоматический возврат в MainActivity и
отсутствие mini/MediaSession; затем новый Play all, local picker/cancel/URI metadata,
замена queue/появление нового запроса во время hide, Close/reopen и repeat/shuffle snapshot.
После этого10 native+10 mini cycles и остальная F01–F17 матрица. На APK31 серии10+10
не запускались после обнаружения оставшегося дефекта: PASS APK29 сюда не переносятся.
Остальные queue/consent/history/source/quality/live/PiP/popup/focus/recovery сценарии
не объявляются пройденными. Проект в целом не принят.

[Матрица APK31](APK31_ACCEPTANCE.csv). Доказательства в delivery/zip31-device-test-20261022-01.
Исходники ZIP32 и отдельный ZIP-отчёт содержат ключевой код и очищенные события;
APK/приватные общие logcat/сессии аккаунта в report ZIP не включаются. GitHub/PR не обновлялись.
