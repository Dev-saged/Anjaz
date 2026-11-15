# code-index (يُولَّد تلقائياً — لا تقرأه كاملاً)

> الاستعمال: `Grep "اسم_الرمز" docs/code-index.md` ثم Read بنطاق أسطر من الملف الناتج.

<!-- repomap:auto:start (tools/repomap.py — لا تعدّل يدوياً) -->
## Code index (auto)
> 225 ملفاً · 33807 سطراً. للتفاصيل: Grep باسم الرمز ثم Read بنطاق أسطر، لا تقرأ الملفات الكبيرة كاملة.
> الأكبر: package-lock.json 10702 · package-lock.json 1552 · 4.json 812 · 3.json 742 · 2.json 735 · 1.json 519 · VoiceUi.kt 331 · FirestoreRemoteStore.kt 329

### ./
- CLAUDE.md (44)
- README.md (64)
- build.gradle.kts (10)
- settings.gradle.kts (17)
- version.json (1)
### .github/workflows/
- apply-to-all-repos.yml (106)
- backend-tests.yml (21)
- build.yml (155)
- deploy-worker.yml (21)
- new-project.yml (95)
- repomap.yml (41)
- unpack-zip.yml (79)
### app/
- build.gradle.kts (157)
- google-services.json (29)
### app/schemas/com.anjaz.app.data.db.AnjazDatabase/
- 1.json (519)
- 2.json (735)
- 3.json (742)
- 4.json (812)
### app/src/main/
- AndroidManifest.xml (69)
### app/src/main/java/com/anjaz/app/
- AnjazApp.kt (27): AnjazApp
- BrandConfig.kt (55): BrandConfig
- MainActivity.kt (79): MainActivity
### app/src/main/java/com/anjaz/app/backup/
- BackupManager.kt (84): BackupFile, RestoreSummary, BackupManager
### app/src/main/java/com/anjaz/app/call/
- CallManager.kt (315): CallPhase, CallPeer, CallState, CallManager
- CallScreen.kt (146): CallViewModel, CallScreen
### app/src/main/java/com/anjaz/app/cloud/
- AuthRepository.kt (85): CloudState, AuthRepository
### app/src/main/java/com/anjaz/app/core/
- DayMath.kt (43): DayMath
- Oklch.kt (36): Oklch, Color.toHex
- SelfTest.kt (96): Check, SelfTest
### app/src/main/java/com/anjaz/app/data/
- AccountsRepository.kt (89): AccountsRepository
- ChatRepository.kt (97): directRoomId, String.csv, ChatRepository
- Clock.kt (10): interface, SystemClock
- DashboardRepository.kt (41): DashboardCounts, DashboardRepository
- DemoSeeder.kt (39): DemoSeeder
- DirectoryRepository.kt (297): DirectoryRepository
- Outbox.kt (24): EntityOp, Outbox
- Session.kt (6): Session
- SessionStore.kt (35): SessionStore
- SosRepository.kt (58): SosStatus, SosRepository
- TaskQueries.kt (17): TaskQueries
- TaskRepository.kt (178): TaskOpPayload, TaskRepository
- TaskTarget.kt (22): TargetKind, TaskTarget, Trash
### app/src/main/java/com/anjaz/app/data/db/
- AnjazDatabase.kt (33): AnjazDatabase
- Daos.kt (302): SquareCount, TaskDao, TaskEventDao, OutboxDao, DirectoryDao, SosDao, ChatDao +1
- Entities.kt (185): RegionEntity, SquareEntity, CampEntity, UserEntity, TaskEntity, TaskEventEntity, OutboxEntity +6
- Migrations.kt (64): Migrations
- SearchDao.kt (23): SearchDao
### app/src/main/java/com/anjaz/app/di/
- DataModule.kt (37): DataModule
### app/src/main/java/com/anjaz/app/domain/
- Dashboard.kt (50): Slice, Dashboard, DashboardBuilder
- Outcome.kt (7): Outcome
- Plural.kt (27): Noun, Plural
- Profile.kt (17): Profile
- Role.kt (12): Role
- TaskStatus.kt (42): TaskStatus, Priority
- Username.kt (49): Username
### app/src/main/java/com/anjaz/app/downloads/
- DownloadCenter.kt (125): DlStatus, DlItem, DownloadCenter
- DownloadsScreen.kt (108): DownloadsViewModel, DownloadsScreen
### app/src/main/java/com/anjaz/app/geo/
- GeoModels.kt (42): Place, Presence, PulseKind, Pulse, MapFilter, GeoMath
- GeoRepository.kt (97): GeoRepository
- LocationProvider.kt (45): LocationProvider
### app/src/main/java/com/anjaz/app/media/
- MediaCompressor.kt (62): Compressed, MediaCompressor
- MediaProgress.kt (9): MediaProgress
- MediaRepository.kt (173): previewOf, extensionFor, MediaRepository
- MediaTransport.kt (27): HttpFailure, Download, MediaTransport, NoMedia
- VideoCompressor.kt (76): CompressedVideo, VideoCompressor
- VoiceRecorder.kt (99): VoiceRecorder, VoicePlayer
### app/src/main/java/com/anjaz/app/pack/
- NativePacks.kt (129): PackState, NativePacks
- PackPrefetchWorker.kt (33): PackPrefetchWorker
### app/src/main/java/com/anjaz/app/push/
- AnjazMessagingService.kt (35): AnjazMessagingService
- DeepLink.kt (8): DeepLink
- DeviceTokens.kt (26): DeviceTokens
- Notifications.kt (51): Notifications
### app/src/main/java/com/anjaz/app/report/
- PdfExporter.kt (32): PdfExporter
- PdfLayout.kt (60): PdfText, PdfRule, PdfPage, PdfLayout
- ReportData.kt (32): ReportTable, ReportData
- XlsxExporter.kt (56): XlsxExporter
### app/src/main/java/com/anjaz/app/schema/
- Schema.kt (54): FieldType, Choice, FieldSchema, ModuleSchema, SchemaValidator
- Schemas.kt (64): Schemas
### app/src/main/java/com/anjaz/app/security/
- AppLock.kt (29): AppLock
### app/src/main/java/com/anjaz/app/stt/
- AudioPcm.kt (84): AudioPcm
- PcmRecorder.kt (39): PcmRecorder
- SttViewModel.kt (48): SttViewModel
- Transcriber.kt (51): Transcriber
- WhisperModel.kt (112): ModelState, WhisperModel
### app/src/main/java/com/anjaz/app/sync/
- ApiClient.kt (95): ApiClient
- CleanupWorker.kt (44): CleanupWorker
- FirestoreRemoteStore.kt (329): FirestoreRemoteStore
- RemoteStore.kt (56): RemoteDenied, AuditEntry, RemoteStore, NoRemoteStore, interface
- SyncEngine.kt (224): PushResult, SyncEngine
- SyncWorker.kt (54): SyncWorker, SyncScheduler
### app/src/main/java/com/anjaz/app/ui/about/
- AboutCard.kt (83): AboutCard
- Changelog.kt (53): ChangelogSheet
### app/src/main/java/com/anjaz/app/ui/accounts/
- AccountsScreen.kt (200): AccountsScreen
- AccountsViewModel.kt (156): AccountsUi, AccountsViewModel
### app/src/main/java/com/anjaz/app/ui/audit/
- AuditScreen.kt (99): AuditRow, actionLabel, AuditViewModel, AuditScreen
### app/src/main/java/com/anjaz/app/ui/chat/
- ChatScreens.kt (313): ChatListScreen, ChatRoomScreen
- ChatViewModels.kt (134): RoomRow, ChatListViewModel, roomTitle, RoomUi, ChatRoomViewModel
- MediaUi.kt (213): sizeLabel, clock, MediaContent, AttachSheet
- VoiceUi.kt (331): VoiceBubble, RecorderState, RecordingBar, HoldToRecord
### app/src/main/java/com/anjaz/app/ui/components/
- AnimatedLogo.kt (79): AnimatedLogo, SplashOverlay
- Components.kt (220): Tone, toneColors, Tag, AnjazCard, AnjazIcon, PrimaryButton, QuietButton +7
- DayStrip.kt (56): DayChip, DayStrip
- FitText.kt (36): FitText
- FormAlert.kt (59): FormAlert
- LockLayer.kt (34): LockLayer
- Motion.kt (69): rememberReduceMotion, Stagger, AmbientBackground
- Notice.kt (54): Notice
- SchemaForm.kt (114): SchemaForm
- TaskCard.kt (62): statusTone, TaskCard
### app/src/main/java/com/anjaz/app/ui/dashboard/
- DashboardScreen.kt (151): DashboardScreen
- DashboardViewModel.kt (44): DashboardViewModel
### app/src/main/java/com/anjaz/app/ui/directory/
- DirectoryScreen.kt (216): DirectoryScreen
- DirectoryViewModel.kt (192): DirKind, SquareRow, RegionSection, DirectoryUi, DirForm, DirectoryViewModel
### app/src/main/java/com/anjaz/app/ui/home/
- HomeScreen.kt (193): HomeScreen
- HomeShortcuts.kt (157): MapCard, CampsCard
- HomeViewModel.kt (57): HomeUiState, HomeViewModel
### app/src/main/java/com/anjaz/app/ui/icons/
- Icons.kt (228): AnjazIcons
### app/src/main/java/com/anjaz/app/ui/login/
- LoginScreen.kt (110): LoginScreen
- LoginViewModel.kt (66): SessionState, LoginUiState, AppSessionViewModel, LoginViewModel
### app/src/main/java/com/anjaz/app/ui/map/
- LiveMapScreen.kt (227): LiveMapScreen
- LiveOverlay.kt (139): MapPalette, LiveOverlay
- MapViewModel.kt (181): MapUi, MapViewModel
### app/src/main/java/com/anjaz/app/ui/more/
- ChatPlaceholder.kt (29): ChatPlaceholder
- MoreScreen.kt (220): MoreScreen
### app/src/main/java/com/anjaz/app/ui/nav/
- AnjazNav.kt (180): Routes, AnjazRoot
- SyncViewModel.kt (36): SyncViewModel
### app/src/main/java/com/anjaz/app/ui/reports/
- ReportsScreen.kt (83): ReportsScreen
- ReportsViewModel.kt (74): ReportsUi, ReportsViewModel
### app/src/main/java/com/anjaz/app/ui/search/
- SearchScreen.kt (127): HitKind, Hit, likeArg, SearchViewModel, SearchScreen
### app/src/main/java/com/anjaz/app/ui/sos/
- SosScreen.kt (188): SosScreen
- SosViewModel.kt (50): SosViewModel
### app/src/main/java/com/anjaz/app/ui/stats/
- StatsScreen.kt (208): StatsScreen
- StatsViewModel.kt (57): SquareStat, RegionChip, StatsUi, StatsViewModel
### app/src/main/java/com/anjaz/app/ui/sync/
- SyncCenter.kt (95): SyncCenterViewModel, SyncSheet
### app/src/main/java/com/anjaz/app/ui/tasks/
- TaskPhotos.kt (190): TaskPhoto, TaskPhotosViewModel, TaskPhotosSection
- TaskTargetPicker.kt (153): DeadlineField, TargetField
- TasksScreen.kt (264): TasksScreen, NewTaskSheet
- TasksViewModel.kt (240): TaskFilter, TaskCategory, TasksUiState, NewTaskState, TasksViewModel
### app/src/main/java/com/anjaz/app/ui/templates/
- Templates.kt (223): Template, TemplatesRepository, TemplatesUi, TemplatesViewModel, TemplatesScreen
### app/src/main/java/com/anjaz/app/ui/test/
- SelfTestScreen.kt (86): SeedViewModel, SelfTestScreen
### app/src/main/java/com/anjaz/app/ui/theme/
- Theme.kt (65): AnjazColors, Anjaz, AnjazTheme
- Type.kt (50): ltr
### app/src/main/java/com/anjaz/app/ui/trash/
- TrashScreen.kt (113): TrashScreen
- TrashViewModel.kt (79): TrashKind, TrashItem, TrashUi, TrashViewModel
### app/src/main/java/com/anjaz/app/update/
- AppFolder.kt (21): AppFolder
- UpdateCheckWorker.kt (52): UpdateCheckWorker
- UpdateChecker.kt (179): AppRelease, UpdateChecker
- UpdateDownloadService.kt (65): UpdateDownloadService
- UpdateInstallReceiver.kt (36): UpdateInstallReceiver, PackageReplacedReceiver
- UpdateManager.kt (279): UpdateManager
- UpdateNotifications.kt (82): UpdateNotifications
- UpdateUi.kt (230): UpdateLayer
- UpdateViewModel.kt (9): UpdateViewModel
### app/src/main/res/drawable/
- ic_flag_ps.xml (13)
- ic_heart.xml (11)
- ic_launcher_background.xml (6)
- ic_launcher_foreground.xml (11)
- ic_launcher_monochrome.xml (11)
- ic_notification.xml (6)
- ic_social_facebook.xml (11)
- ic_social_github.xml (11)
- ic_social_instagram.xml (15)
- ic_social_whatsapp.xml (13)
- ic_social_x.xml (13)
### app/src/main/res/mipmap-anydpi-v26/
- ic_launcher.xml (7)
- ic_launcher_round.xml (7)
### app/src/main/res/values/
- strings.xml (5)
- themes.xml (10)
### app/src/main/res/xml/
- data_extraction_rules.xml (16)
- file_paths.xml (7)
### app/src/test/java/com/anjaz/app/
- BackupReportTest.kt (112): BackupReportTest
- ChatSosTest.kt (131): ChatSosTest
- DashboardTest.kt (40): DashboardTest
- DirectoryTest.kt (96): DirectoryTest
- DomainTest.kt (108): RoleTest, TaskStatusTest, SchemaValidatorTest, BrandTest, DayMathTest
- MediaMathTest.kt (51): MediaMathTest
- MediaTest.kt (171): MediaTest
- MigrationTest.kt (65): MigrationTest
- RoomSelfTestTest.kt (22): RoomSelfTestTest
- SyncEngineTest.kt (181): SyncEngineTest
### app/src/test/java/com/anjaz/app/update/
- UpdateCheckerTest.kt (49): UpdateCheckerTest
### backend/
- README.md (28)
### backend/firebase/
- firebase.json (5)
- firestore.rules (274)
- package-lock.json (10702)
- package.json (8)
### backend/firebase/test/
- rules.test.js (208): db, task
### backend/worker/
- package-lock.json (1552)
- package.json (8)
### backend/worker/src/
- auth.js (41): HttpError, keys, resetKeyCache, verifyIdToken
- b64.js (26): b64url, fromB64url, pemToDer
- backup.js (43): gzip, runBackup, readBackup
- cron.js (28): escalate
- firestore.js (101): encodeValue, encodeFields, decodeValue, decodeFields, decodeDoc, Firestore
- google.js (45): serviceAccount, signJwt, accessToken, resetTokenCache
- identity.js (42): Identity
- index.js (251): json, limited, body, str, profile, audit, createUser +12
- media.js (91): roomOf, putMedia, getMedia, ackMedia, purgeMedia
- notify.js (57): sendFcm, leadsFor, targetsFor
- policy.js (55): validUsername, validPassword, isActive, isLead, inScope, shapeOk, canCreateUser +3
- recurring.js (56): localTime, due, runRecurring, weeklyReport
- trash.js (27): purgeTrash
### backend/worker/test/
- harness.js (119): keys, idToken, d1, world
- worker.test.js (246): newUser
### brand/
- BRAND.md (39)
- board-spec.json (32)
- presentation.html (86)
### brand/android-icons/drawable/
- ic_launcher_background.xml (6)
- ic_launcher_foreground.xml (11)
- ic_launcher_monochrome.xml (11)
### brand/android-icons/mipmap-anydpi-v26/
- ic_launcher.xml (7)
- ic_launcher_round.xml (7)
### brand/prototype/
- home-a.html (164)
- home-directions.html (271)
- screens.html (240)
### brand/tools/
- build_brand.py (76): oklch, symbol, svg, wordmark, write
- gen_icons.py (42): num, conv
- make_readme_assets.py (137): t, social_paths, icon_g, defs, wrap, face
- make_update_demo.py (82): ring, banner, main
- outline_text.py (25): outline
### brand/variants/
- head-snippet.html (6)
### docs/
- DEPLOY.md (18)
- media.md (7)
- repo-map.md (41)
### tools/
- repomap.py (146): files, symbols, build, main
<!-- repomap:auto:end -->
