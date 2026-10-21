# Происхождение и границы реализации

Донор — PipePipe, лицензия GPL-3.0-or-later; сохранена копия LICENSE.
Авторство исходных компонентов принадлежит разработчикам PipePipe/NewPipe;
изменения SloooPipe перечислены ниже, файлы не выдаются за неизменённый upstream.

- PipePipeClient `24db7c535d82edf1af30d1cdb201b51f21c1a48d`:
  `views/YouTubeLoginWebViewActivity.java`, `views/BaseLoginWebViewActivity.java`,
  `settings/YouTubeAccountSettingsFragment.java`, `App.java`.
  Источник: https://github.com/InfinityLoop1308/PipePipeClient/tree/24db7c535d82edf1af30d1cdb201b51f21c1a48d
- PipePipeExtractor `73442076c671ae09bd5c0f9635932a5a67bdd609`:
  `services/youtube/YoutubeParsingHelper.java` (`getAuthorizationHeader`, MWEB),
  `services/youtube/extractors/YoutubeStreamExtractor.java`.
  Источник: https://github.com/InfinityLoop1308/PipePipeExtractor/tree/73442076c671ae09bd5c0f9635932a5a67bdd609

Адаптированы механизм WebView-входа/явного импорта, получение cookies и
videoplayback pot, SAPISIDHASH и выбор MWEB для авторизованного запроса.
Это переработанная реализация по указанным компонентам, а не готовый AAR донора.

Изменения SloooPipe: независимый API, физическое отключение реализации,
Keystore/AES-GCM/noBackup, отдельный WebView-процесс с data-directory suffix
на Android 9+, явная передача сессии пользователем, отдельное подтверждение
реальным запросом, generation guards, строгий player-endpoint allowlist,
отказ от redirect с credentials. На Android 6–8.1 доступен только явный импорт
raw Cookie header и версии MWEB клиента: WebView не создаётся, так как там
нет используемого механизма изоляции его каталога.

Не перенесены `LocalDomPoTokenProvider`, полный SABR-протокол и его MediaSource.
Захваченный videoplayback pot не выдаётся за player-attestation token и не
переиспользуется для другого videoId. Требующий SABR ответ выдаёт
`AUTH_SABR_BACKEND_REQUIRED`, а не фиктивный MP4/пустой список.

Приложение пока использует старый плеер NewPipe. Модуль не зависит от него,
NewPlayer, Extractor, app.R, базы и общих настроек приложения.
