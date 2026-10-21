# Исправление verifyAuthIsolation после первого WSL-лога

Дата: 2026-10-20. Входной лог: `03 delivery/auth-stage1-20261020-01/build-main.log`.
Собирался ZIP 03, SHA-256 `9fb4bda198fb51ff6b7a3b50c52d6caa27455de43ac0c70440618592f5f923f5`.
В логе присутствует implementation project `:youtube-auth-pipepipe` — это withAuth.

Сбой произошёл при определении зависимостей задачи `:app:verifyAuthIsolation`,
до компиляции приложения. Прямые `inputs.files(debugRuntimeClasspath)` и
`runtime.files` запрашивали файлы всех Android-зависимостей без `artifactType`.
Gradle не смог выбрать между вторичными вариантами `:shared` и
`:youtube-auth-pipepipe`: classes, resources, metadata и другими.

Исправление использует [Gradle ArtifactView](https://docs.gradle.org/current/userguide/artifact_views.html):
полный runtime graph по-прежнему проверяется на наличие auth implementation,
а для проверки классов выбираются только JAR-файлы известного Extractor provider.
Для withoutAuth это `com.github.TeamNewPipe:NewPipeExtractor`, для withAuth —
подменяющий его included project `:extractor`. В artifact view задан тип `jar`;
входы задачи используют тот же view. Поэтому проверка не запрашивает файлы
несвязанных Android-модулей без типа артефакта.

Проверки одного Extractor JAR, наличия/отсутствия auth hook и implementation
сохраняются. Ошибки разрешения зависимостей не подавляются; lenient mode не включён.
Версии Gradle/AGP/JDK, builder и код авторизации не менялись.

Локально выполнены только статический просмотр изменения, `git diff --check`
и проверка архивов. Gradle configuration, компиляция и проектные тесты здесь
не запускались. Исправление нужно подтвердить повторной WSL-сборкой обоих вариантов.
Незакрытые SABR/NewPlayer зависимости остаются в AUTH_IMPLEMENTATION_STATUS.md.
