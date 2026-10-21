# Авторизация: первый пакет исходников

> Обновление 2026-10-21: основа заменена на актуальный NewPipe/dev и его Extractor.
> Текущий статус — [перенос основы](CURRENT_UPSTREAM_BASE.md). Ниже сохранена история предыдущих этапов.

Дата: 2026-10-20. Стартовый коммит: `a7df79e`, исходная база до него: `7a8d9f3`.
Реализация начата по [ТЗ v2](specs/SloooPipe-AUTH-MODULE-CODEX-v2-20261020.md).

Внешняя WSL-сборка withAuth из ZIP 06 (`89053c86…`) подтверждена логом
`03 delivery/auth-stage1-20261020-04/build-main.log`: BUILD SUCCESSFUL,
3 мин 33 с, 136 задач выполнено. Прошли assembleDebug, app/shared/auth API tests,
verifyAuthIsolation и проверки оформления. Безавторизационный вариант того же
пакета и воспроизведение на устройстве ещё не подтверждены.

На OnePlus 13 / Android 16 пользователь обнаружил запрет скриншотов,
недоступные кнопки, общую ошибку захвата/импорта и отказ Google принимать браузер.
ZIP 07 содержит [исправление окон входа и импорта](AUTH_ANDROID16_LOGIN_FIX.md).
По прямому указанию пользователя скриншоты разрешены, включая окно авторизации;
это уточняет прежние требования ТЗ. ZIP 07 пока проверен только статически.
Отказ Google принимать embedded WebView не объявлен решённым.

История исправлений сборки: [artifact view](AUTH_GRADLE_ARTIFACT_FIX.md),
[TOML/Java style](AUTH_STYLE_FIX.md), [строка ресурса](AUTH_RESOURCE_FIX.md).

После ZIP 07 добавлена [диагностика logcat](../tools/README-auth-diagnostics.md).
ZIP 09 исправил generic inference после диагностических Rx callbacks. Его logcat
подтверждает блокировку main-frame перехода после ввода Google 2FA; старый лог
не содержит адреса назначения. ZIP 10 добавляет недостающий `accounts.youtube.com`
в WebView allowlist и существенно расширяет DEBUG-трассировку, включая изменившийся
текст страницы. Подробнее: [разбор 2FA](AUTH_2FA_REDIRECT_FIX.md).
Новый APK ещё не проверен: успешный вход и принятие кода Google не подтверждены.
JUnit-тесты расширены, локально не запускались.

Новый logcat ZIP 10 установил точный блокируемый адрес: `gds.google.com/web/landing`
после TOTP/CheckCookie (17:37:24). ZIP 11 разрешает этот origin в WebView;
предыдущее предположение про accounts.youtube.com не подтверждено этой записью.
Новый вход/YouTube session по-прежнему не получены, исправление требует телефона.

Logcat ZIP 11 показал следующий блокируемый переход — региональный
`accounts.google.ru/accounts/SetSID` после CheckCookie. Сессионные cookies YouTube
уже присутствуют, но захват и проверка не выполнялись. ZIP 12 разрешает точные
региональные account-hosts по встроенному снимку списка Google и добавляет
подсказку продолжения при наличии cookies. Успешный вход ещё не подтверждён.

## Что реализовано в исходниках

- `NewPipe/youtube-auth-api`: контракт без Android/NewPlayer/Extractor,
  анонимный provider, capability player-запроса без раскрытия секретов через
  toString, endpoint policy, фильтр диагностик и защита поздних UI-операций.
- `NewPipe/youtube-auth-pipepipe`: отдельная Android library с собственными
  ресурсами/Activity. PipePipe WebView-вход, явный импорт cookies; Keystore
  AES-GCM в noBackupFilesDir, состояние CAPTURED отдельно от VERIFIED.
- Два host bindings в `app/src/authEnabled` и `app/src/authDisabled`.
  `-Pslooopipe.withAuth=false` физически исключает implementation project и
  локальный fork Extractor из settings/classpath. Default — false.
- WithAuth получает локальный NewPipeExtractor v0.26.4 с маленьким optional
  authenticated-provider hook. WithoutAuth использует прежний pinned upstream
  Maven/JitPack artifact. Два одинаковых Extractor в runtime не подключаются.
- Host backend делает настоящий MWEB player POST с Cookie/SAPISIDHASH, текущей
  конфигурацией клиента и signatureTimestamp из существующего decoder.
  Cookies разрешены только на точном HTTPS `/youtubei/v1/player` endpoint;
  redirect запрещён, сетевые proxy/TLS настройки наследуются от NewPipe.
- Авторизованные direct-format ответы проходят через обычный StreamInfo и
  прежний плеер. Нет изменения публичных типов StreamInfo/Stream/DeliveryMethod.
- В настройках подключён аккаунт; на распознанном требовании входа есть действие
  входа с выбранной ссылкой. После получения сессии выбранное действие повторяется
  только после реального извлечения источников, без бесконечных повторов.
- При изменении credentials отменяются активные auth HTTP calls, сбрасываются
  InfoCache и прежняя анонимная токенизация, останавливается старый PlayerService.
  Поздние результаты не пишутся в новый кэш. Проверка в account UI имеет ticket
  и generation guards, поздний A не запускает видео после B/выхода.
- WebView входа отделён от анонимных PoToken/reCAPTCHA WebView. Android 9+ использует
  отдельный процесс/data directory; его каталог исключён из cloud/device backup.
  На Android 6–8.1 WebView-вход не предлагается, остаётся явный импорт.
- `:app:verifyAuthIsolation` сохраняет разрешённый dependency graph и проверяет
  наличие/отсутствие implementation и fork hook, ровно один Extractor JAR.
- WSL builder revision 07 принимает `--auth with|without`, запускает новую
  проверку, api unit tasks и сохраняет их результаты отдельно.

## Незакрытые зависимости — не считать вход завершённым

**SABR не реализован.** В текущем доноре MWEB может вернуть session-based SABR.
В этом случае источник намеренно не маскируется под HTTP-файл: извлечение
заканчивается `AUTH_SABR_BACKEND_REQUIRED`, экран объясняет неподдержанный поток.
Получение cookies в таком случае не засчитывается как восстановление просмотра.

**Полная токенизация PipePipe не перенесена.** Сейчас есть cookies, clientVersion,
visitorData и при наличии video-bound streaming pot из WebView. Это не замена
`LocalDomPoTokenProvider`/свежему player PoToken. Возможен честный отказ
`AUTH_DECODER_NOT_READY`, HTTP/LOGIN_REQUIRED или иной отказ сервера.
Сначала нужны две реальные сборки, затем проверка именно пользовательской сети
и сравнение того же видео с донором. Гарантии, что данный этап решает блокировку,
нет. Это первый интеграционный пакет исходников, не закрытие всего ТЗ.

NewPlayer в рамках этого пакета не обновлялся и не интегрировался. Принятый
план требует сначала внешней проверки auth-вариантов; зависимости плеера и
аккаунтов остаются независимыми.

## Происхождение fork Extractor

Исходник: TeamNewPipe/NewPipeExtractor v0.26.4,
Git `43f8e6ebeef469db7c5328714bc5f33c9f06f092`.
Код сохранён в `NewPipe/third-party/NewPipeExtractor-auth` вместе с LICENSE.
Донорские auth-компоненты и ревизии — в
[THIRD_PARTY.md](../NewPipe/youtube-auth-pipepipe/THIRD_PARTY.md).

Изменения fork: optional provider interface; ветка authenticated response в
YoutubeStreamExtractor; отказ для SABR; bounded metadata fallback; toolchain
21/output17; исключён ненужный generator module из included build; git-derived
publication hash заменён фиксированным идентификатором для сборки исходного ZIP.
Остальной Extractor не заменён реализацией PipePipe. Источники анонимных запросов
и другие сервисы сохранены.

## Проверки

| Проверка | Статус |
| --- | --- |
| Синтаксический разбор изменённых/новых Java через javalang | PASS (статический разбор, не компиляция) |
| Разбор XML, TOML; Bash `-n`; Python AST встроенных блоков | PASS |
| Git diff whitespace / состав архива | Проверяется при упаковке |
| Сборки withoutAuth и withAuth | NOT RUN |
| Разрешённые dependency graphs двух вариантов | NOT RUN; task/команды подготовлены |
| Unit-тесты доменной политики, stale A/B, редактирования диагностик | Написаны, NOT RUN |
| Keystore, backup, WebView, cancel/logout/session restore на устройстве | NOT RUN |
| Реальный вход, запрос в ограниченной сети, воспроизведение | NOT RUN |
| SABR / полноценные player PoTokens | НЕ РЕАЛИЗОВАНО |

Все секреты в примерах тестов искусственные. Пользовательские аккаунты,
cookies, ключи подписи и пароли при разработке не читались и не использовались.

Команды и инструкция: [README-auth-wsl.md](../tools/README-auth-wsl.md).
