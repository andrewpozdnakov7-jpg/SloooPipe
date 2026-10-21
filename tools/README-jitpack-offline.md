# Перенос зависимостей JitPack для SloooPipe

Лог завершился тайм-аутом обращения к `jitpack.io` при разрешении
`groupie:2.10.1`. После этого Gradle отключил репозиторий и сообщил ещё о шести
неразрешённых модулях. Компиляция приложения и unit-тесты ещё не начались.

Подготовлен архив `SloooPipe-jitpack-offline-20261020-01.zip`:

- `offline-maven/` — семь существующих модулей из version catalog NewPipe,
  оригинальные POM/Module Metadata и JAR/AAR, без изменения версий;
- `offline-maven/mirror-manifest.json` — URL, размер и SHA-256 каждого файла;
- `build-slooopipe-from-source-zip-wsl.sh` — ревизия
  `20261020-05-local-jitpack-mirror`, автоматически подключающая локальный Maven;
- эта инструкция.

Все бинарные библиотеки сверены с контрольными суммами исходного репозитория
JitPack. Контрольные суммы и структура проверены без выполнения библиотек.
Gradle, компиляторы и unit-тесты на компьютере подготовки не запускались.

## Действия на основном компьютере

Скопируйте ZIP в Windows Downloads. В **PowerShell**:

```powershell
Expand-Archive -LiteralPath "$env:USERPROFILE\Downloads\SloooPipe-jitpack-offline-20261020-01.zip" -DestinationPath '\\wsl$\Ubuntu-26.04\home\<linux-user>\projects\slooopipe-src' -Force -ErrorAction Stop
```

ZIP исходников `sources/SloooPipe-sources-20261020-01.zip` оставьте прежним.
Распакованный набор не заменяет исходники, SDK, ключи или общий кэш Gradle.

Из того же **PowerShell** запустите:

```powershell
wsl.exe -d Ubuntu-26.04 -- bash -lc 'cd ~/projects/slooopipe-src && bash ./build-slooopipe-from-source-zip-wsl.sh --gradle-user-home "$HOME/.gradle"'
```

Операция **2**, компоненты **3**. Проверьте в выводе:

```text
SloooPipe WSL builder 20261020-05-local-jitpack-mirror
Local Maven mirror verified: 7 modules, 20 files
Local Maven mirror: ~/projects/slooopipe-src/offline-maven
```

Набор закрывает зависимость от JitPack для этих семи модулей через
[exclusive repository content](https://docs.gradle.org/current/userguide/filtering_repository_content.html).
Другие зависимости берутся из общего кэша и остальных репозиториев. Поэтому
это не заявление о полностью офлайн-сборке: следующий лог может показать
другой недостающий артефакт или ошибку компиляции.

Подробности и список координат находятся в `RESULT.md` рабочего репозитория
и `offline-maven/mirror-manifest.json`.

В Windows-примерах `<linux-user>` замените именем пользователя своего WSL-дистрибутива.
