# ZIP30: ошибка компиляции и исправление ZIP31

Лог WSL от2026-10-22: build-main.log, SHA256 `4b9ff58bad3211861e4ca8e468724720089bdbec02093384f0a50ea3521d84f6`.
Work SHA b655f42fc04a100c46ef5250e01f9ec1ae1fe5a21bd64ec567a2188be9e4184a
соответствует ZIP30, commit ec89006b231564443287d37def4658f03e8d922f.
Сборка завершилась BUILD FAILED на :newplayer-local:compileDebugKotlin:
NewPlayerViewModelImpl.kt:277:58, unresolved reference skipSilenceEnabled on Player.
Предупреждение Checkstyle PipePipeBackend.java не является причиной остановки.

Ошибка в добавленном начальном UI snapshot: NewPlayer.exoPlayer публично имеет тип
StateFlow<Player?>. Свойство skipSilenceEnabled доступно внутреннему ExoPlayer,
но отсутствует у этого публичного Player. Уже существующий NewPlayer.skipSilence
оборачивает getter внутреннего ExoPlayer. Исправлена ровно одна production-строка:
`skipSilence = newPlayer.skipSilence` вместо обращения к Player.skipSilenceEnabled.
Остальные исправления ZIP30, builder09, зависимости, подпись и auth сохранены.

Статически сверены объявление NewPlayer.skipSilence и реализация getter; parser errors0,
новых style findings0 (те же30 прежних). Generic cumulative apply-check на официальном
pinned212ca PASS. Это не проверка компиляции. Здесь Gradle/проектные тесты не запускались;
APK31 ещё не собран и не проверен. Нужны main/build-test/auth with в WSL из точного ZIP31.
Auth-without/full tests и полная device-матрица остаются открытыми.

Последние runtime-доказательства относятся к APK29: [checkpoint](APK29_DEVICE_CHECKPOINT.md).
ZIP30 device PASS не имеет; APK из завершившейся ошибкой сборки устанавливать не следует.
