# APK26: падение запуска, кандидат исправления ZIP27

Дата: 2026-10-22. LDPlayer14, Android14/API34, emulator-5554, 720×1280.
Пользователь сообщил об установке APK26. Это заявление о происхождении, а не
криптографическое подтверждение сборки из исходников; WSL build/test log не передан.

## Что измерено

- Package: org.schabi.newpipe.debug, versionName0.29.1, versionCode1015.
- lastUpdateTime: 2026-10-22 12:56:07 (время устройства).
- APK: 45,426,082 bytes, SHA256
  `cb27db461d3a6364e1c9e0d2bb721e50c1ebb99ffa6b44d9609e80408ad1aa42`.
- Три падения процесса приложения в logcat: PID31260,31623,31833.
  Два последних воспроизведены в текущем ADB-сеансе: нажатие иконки и am start.
- Ошибка: IllegalArgumentException при MainActivity.onResume →
  LocalPlaybackMini.attach:36 → MaterialColors.getColor: colorSurface отсутствует.
- Вызов am start возвращает Status:ok, но процесс затем падает. Успех команды
  запуска не равен успешному открытию приложения.
- Данные приложения не очищались, preferences/история/авторизация не сбрасывались.

## Причина и исправление

LocalPlaybackMini.attach вызывается на каждом MainActivity.onResume, даже когда
локальное видео не выбрано и панель скрыта. Новый код требовал MaterialComponents
colorSurface, тогда как темы NewPipe наследуют Theme.AppCompat.DayNight.NoActionBar.

ZIP27 использует ThemeHelper.resolveColorFromAttr(activity,
R.attr.contrast_background_color). Этот цвет задан в светлой, тёмной и чёрной
базовых темах NewPipe и наследуется темами сервисов. MaterialColors import удалён.
Изменения глобальной темы и подавления исключений нет.
Builder08, signing, toolchain, зависимости, playback engine и очередь не менялись.

## Что не проверено

Сценарии воспроизведения APK26 BLOCKED из-за падения запуска. PASS APK25 сюда не
переносится. Сборка и JVM-тесты ZIP27 здесь не запускались; устройство с исправленным
APK27 ещё не проверено. Наличие установленного APK26 не доказывает успешное прохождение
всех WSL unit tests или auth-without. Человеческая проверка звука относилась к APK25.

## Следующий цикл

1. Собрать ZIP27 прежним WSL builder: main, build-test, auth with; установить поверх,
   без очистки данных. Сохранить полный run log и test reports.
2. Проверить холодный/повторный запуск MainActivity и возврат из плеера; локальная
   мини-панель в светлой/тёмной/чёрной темах, читаемость текста и кнопок.
3. Повторить всю F01–F17/A01–A02 матрицу из ZIP26_ACCEPTANCE.csv, включая обе серии
   очереди по10 циклов. Название CSV сохраняет исходный план, а не PASS для ZIP27.
4. Выполнить второй auth-without build/test/isolation до итоговой приёмки.
   Физический Android16 остаётся отдельной непроведённой проверкой.

Локальные доказательства: delivery/zip26-device-test-20261022-01:
installed-apk.json, startup-crashes.txt, startup-result.json, initial UI/screenshots.
Сырой APK в отчёт не включается. Новая публикация/PR не выполнялись.
