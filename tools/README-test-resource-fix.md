# Исправление ресурса app_name после первого запуска JVM-тестов

Пользовательский лог: семь тестов в `shared:jvmTest`, один упал —
`TopAppBarTest.testTopAppBarCanHaveNavigation`, ResourceEnvironment.kt:101.
Зависимости JitPack на этом запуске уже разрешились.

Статический анализ выявил отсутствующий fallback строки `app_name`:
переводы существовали, а значения в базовом `values/strings.xml` не было.
В исходном ZIP **02** добавлено базовое значение `NewPipe`. Тест сохраняется.
Полного ID из исключения в присланном логе нет; результат исправления должен
подтвердить следующий запуск на основном компьютере.

Внешний скрипт обновлён до `20261020-06-full-test-errors`: ошибки тестов теперь
выводятся полностью, вместе с причинами и stack trace.

## Установка обновления

Перенесите `SloooPipe-test-resource-fix-20261020-01.zip` в Windows Downloads
основного компьютера. В **PowerShell**:

```powershell
Expand-Archive -LiteralPath "$env:USERPROFILE\Downloads\SloooPipe-test-resource-fix-20261020-01.zip" -DestinationPath '\\wsl$\Ubuntu-26.04\home\<linux-user>\projects\slooopipe-src' -Force -ErrorAction Stop
```

После успешной распаковки запустите из **PowerShell**:

```powershell
wsl.exe -d Ubuntu-26.04 -- bash -lc 'cd ~/projects/slooopipe-src && bash ./build-slooopipe-from-source-zip-wsl.sh --gradle-user-home "$HOME/.gradle" --component both --operation build-test --newpipe-zip ./sources/SloooPipe-sources-20261020-02.zip --newplayer-zip ./sources/SloooPipe-sources-20261020-02.zip'
```

Команда явно выбирает новый ZIP 02 для обоих проектов и сборку с unit-тестами.
Скрипт создаст новую рабочую распаковку по SHA-256. Предыдущая распаковка и её
логи сохранятся; SDK, ключи, offline-maven и общий Gradle User Home продолжают
использоваться из существующих папок.

Выполнены только статические проверки и упаковка исходников. Здесь Gradle,
компиляторы и тесты проекта не запускались.

В Windows-примерах `<linux-user>` замените именем пользователя своего WSL-дистрибутива.
