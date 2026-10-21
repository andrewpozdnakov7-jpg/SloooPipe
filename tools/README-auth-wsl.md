# Две проверки auth-пакета в WSL

На этом компьютере сборки и проектные тесты не выполнялись.
Пакет не закрывает SABR/полную токенизацию; статус:
[AUTH_IMPLEMENTATION_STATUS.md](../docs/AUTH_IMPLEMENTATION_STATUS.md).

Внешний `SloooPipe-auth-stage1-20261020-05.zip` распаковать содержимым в:
`~/projects/slooopipe-src`.
Получатся builder revision 07 и `sources/SloooPipe-sources-20261020-07.zip`.
ZIP 07 разрешает скриншоты и исправляет окна входа/импорт для Android 16:
[разбор и проверка](../docs/AUTH_ANDROID16_LOGIN_FIX.md).
Предыдущие исправления порядка TOML, оформления Java и выбора артефактов сохранены.
Внутренний исходный ZIP не распаковывать для запуска builder.
Существующие `signing`, `offline-maven` и общий Gradle cache сохраняются.

В терминале **Ubuntu**:

```bash
cd ~/projects/slooopipe-src
bash ./build-slooopipe-from-source-zip-wsl.sh --component main --operation build-test --auth without --newpipe-zip ./sources/SloooPipe-sources-20261020-07.zip
bash ./build-slooopipe-from-source-zip-wsl.sh --component main --operation build-test --auth with --newpipe-zip ./sources/SloooPipe-sources-20261020-07.zip
```

Сначала выполнить without; with запускать после завершения первой команды.
Два независимых каталога `out/run-*` содержат APK, логи, тестовые отчёты и
`reports/main/auth/dependency-graph.txt`. В BUILD_INFO указан Auth mode.
APK имеют тот же applicationId и подпись: для сравнения устанавливать по очереди,
а не ожидать два разных значка. Выходить из аккаунта перед проверкой without.

Auth-вариант впервые собирает локальный Java Extractor, поэтому могут понадобиться
новые артефакты из Maven Central/Gradle Plugin Portal и protoc. Это не полная
офлайн-сборка; существующий JitPack mirror покрывает прежние pinned модули.
Не снижать версии и не менять зависимости наугад при сетевом отказе — сохранить лог.

Эквивалентные команды в уже распакованном каталоге `SloooPipe/NewPipe`:

```bash
./gradlew -Pslooopipe.withAuth=false :youtube-auth-api:test :app:verifyAuthIsolation :app:assembleDebug --no-configuration-cache
./gradlew -Pslooopipe.withAuth=true :youtube-auth-api:test :app:verifyAuthIsolation :app:assembleDebug --no-configuration-cache
```

Builder дополнительно выполняет прежние app/shared тесты и подпись существующим
ключом. Команды выше сами подпись существующим ключом не повторяют.

Проверить после сборок:

1. Without: аккаунт не показан; graph `withAuth=false`, `extractors=1`, `hooks=0`.
2. With: graph `withAuth=true`, `extractors=1`, `hooks=1`, есть implementation project.
3. Настройки → Аккаунт YouTube → вход. Передача сессии — явная кнопка после входа.
   CAPTURED означает только сохранение; это не обещание доступа к видео.
   На Android 16 проверить скриншоты, системные панели, клавиатуру, альбомную
   ориентацию, большой шрифт и прокрутку кнопок. Внешний браузер не передаёт
   cookies автоматически; ручной импорт теперь запрашивает cookies/версию
   MWEB независимо от текущей страницы встроенного браузера.
4. Указать публичный YouTube URL и проверить получение. VERIFIED означает успешное
   извлечение с этой сессией; затем отдельно открыть и проверить воспроизведение.
5. При требовании SABR/отказе сервера сохранить категорию. Не публиковать cookies,
   Authorization, pot, raw response или URL видеопотока.
6. Выход, перезапуск, отмена входа, смена A→B во время проверки, повторный вход
   другим аккаунтом; не должен запускаться старый результат или сохраняться доступ
   со старой сессией. Проверить безавторизационное видео и другой сервис NewPipe.

Для ревью передать обе папки run-* и краткое описание результата на устройстве.
Не прикладывать экспорт cookies и содержимое хранилища WebView.
