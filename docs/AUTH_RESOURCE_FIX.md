# Восстановление строки подсказки auth-модуля

Дата: 2026-10-20. Лог: `03 delivery/auth-stage1-20261020-03/build-main.log`.
Исходники ZIP 05: `6aca4618a581756c3eb6f4c03083b68bc7e151dfa33842965e1f764ee43e5538`.

Во внешней сборке прошли `:app:checkDependenciesOrder`, `:app:runCheckstyle`,
`:app:runKtlint`, `:app:verifyAuthIsolation` и `:youtube-auth-api:test`.
Сбой: `:app:processDebugResources`, Android resource linking failed.

В `youtube-auth-pipepipe/src/main/res/values/strings.xml` значение
`youtube_auth_version_hint` оказалось заменено вопросительными знаками.
Значение начиналось с `?`, поэтому resource linker искал theme attribute
с именем из оставшегося текста. Восстановлен русский текст UTF-8:
«Версия клиента MWEB из браузера (INNERTUBE_CONTEXT.client.clientVersion)».
Обработка входа, зависимости, версии инструментов и builder не менялись.

Статически проверены добавленные/изменённые Java, Kotlin, XML и build-файлы
этапа auth: последовательности трёх и более вопросительных знаков и U+FFFD
в них после исправления отсутствуют. Изменённые XML разобраны парсером,
проверены начало значений строк на `?`/`@` и исходный UTF-8 текст в ZIP 06.
Проверены `git diff --check` и целостность ZIP.

Gradle, resource compiler/linker, проектные тесты и устройство локально
не запускались. Повторная WSL-сборка нужна для подтверждения исправления
и проверки оставшихся этапов APK. SABR/NewPlayer пока не завершены.
