# code-index (يُولَّد تلقائياً — لا تقرأه كاملاً)

> الاستعمال: `Grep "اسم_الرمز" docs/code-index.md` ثم Read بنطاق أسطر من الملف الناتج.

<!-- repomap:auto:start (tools/repomap.py — لا تعدّل يدوياً) -->
## Code index (auto)
> 143 ملفاً · 22299 سطراً. للتفاصيل: Grep باسم الرمز ثم Read بنطاق أسطر، لا تقرأ الملفات الكبيرة كاملة.
> الأكبر: package-lock.json 10702 · 3.json 742 · 2.json 735 · 1.json 519 · FirestoreRemoteStore.kt 283 · home-directions.html 271 · screens.html 240 · index.js 230

### ./
- CLAUDE.md (44)
- README.md (22)
- build.gradle.kts (10)
- settings.gradle.kts (17)
- version.json (5)
### .github/workflows/
- apply-to-all-repos.yml (106)
- backend-tests.yml (21)
- build.yml (104)
- deploy-worker.yml (17)
- new-project.yml (95)
- repomap.yml (41)
- unpack-zip.yml (79)
### app/
- build.gradle.kts (117)
### app/schemas/com.anjaz.app.data.db.AnjazDatabase/
- 1.json (519)
- 2.json (735)
- 3.json (742)
### app/src/main/
- AndroidManifest.xml (47)
### app/src/main/java/com/anjaz/app/
- AnjazApp.kt (21): AnjazApp
- BrandConfig.kt (38): BrandConfig
- MainActivity.kt (33): MainActivity
### app/src/main/java/com/anjaz/app/backup/
- BackupManager.kt (84): BackupFile, RestoreSummary, BackupManager
### app/src/main/java/com/anjaz/app/cloud/
- AuthRepository.kt (75): CloudState, AuthRepository
### app/src/main/java/com/anjaz/app/core/
- DayMath.kt (43): DayMath
- Oklch.kt (36): Oklch, Color.toHex
- SelfTest.kt (88): Check, SelfTest
### app/src/main/java/com/anjaz/app/data/
- AccountsRepository.kt (75): AccountsRepository
- ChatRepository.kt (67): directRoomId, String.csv, ChatRepository
- Clock.kt (10): interface, SystemClock
- DashboardRepository.kt (41): DashboardCounts, DashboardRepository
- DemoSeeder.kt (39): DemoSeeder
- DirectoryRepository.kt (77): DirectoryRepository
- Outbox.kt (24): EntityOp, Outbox
- Session.kt (6): Session
- SessionStore.kt (35): SessionStore
- SosRepository.kt (58): SosStatus, SosRepository
- TaskQueries.kt (17): TaskQueries
- TaskRepository.kt (96): TaskOpPayload, TaskRepository
### app/src/main/java/com/anjaz/app/data/db/
- AnjazDatabase.kt (32): AnjazDatabase
- Daos.kt (216): TaskDao, TaskEventDao, OutboxDao, DirectoryDao, SosDao, ChatDao, ContactDao
- Entities.kt (154): RegionEntity, SquareEntity, CampEntity, UserEntity, TaskEntity, TaskEventEntity, OutboxEntity +5
- Migrations.kt (25): Migrations
### app/src/main/java/com/anjaz/app/di/
- DataModule.kt (35): DataModule
### app/src/main/java/com/anjaz/app/domain/
- Outcome.kt (7): Outcome
- Profile.kt (17): Profile
- Role.kt (12): Role
- TaskStatus.kt (39): TaskStatus, Priority
### app/src/main/java/com/anjaz/app/push/
- AnjazMessagingService.kt (33): AnjazMessagingService
- DeepLink.kt (8): DeepLink
- DeviceTokens.kt (26): DeviceTokens
- Notifications.kt (49): Notifications
### app/src/main/java/com/anjaz/app/report/
- PdfExporter.kt (32): PdfExporter
- PdfLayout.kt (60): PdfText, PdfRule, PdfPage, PdfLayout
- ReportData.kt (32): ReportTable, ReportData
- XlsxExporter.kt (56): XlsxExporter
### app/src/main/java/com/anjaz/app/schema/
- Schema.kt (54): FieldType, Choice, FieldSchema, ModuleSchema, SchemaValidator
- Schemas.kt (68): Schemas
### app/src/main/java/com/anjaz/app/sync/
- ApiClient.kt (39): ApiClient
- FirestoreRemoteStore.kt (283): FirestoreRemoteStore
- RemoteStore.kt (53): RemoteDenied, AuditEntry, RemoteStore, NoRemoteStore, interface
- SyncEngine.kt (159): PushResult, SyncEngine
- SyncWorker.kt (48): SyncWorker, SyncScheduler
### app/src/main/java/com/anjaz/app/ui/accounts/
- AccountsScreen.kt (111): AccountsScreen
- AccountsViewModel.kt (91): AccountsUi, AccountsViewModel
### app/src/main/java/com/anjaz/app/ui/audit/
- AuditScreen.kt (98): AuditRow, actionLabel, AuditViewModel, AuditScreen
### app/src/main/java/com/anjaz/app/ui/chat/
- ChatScreens.kt (184): ChatListScreen, ChatRoomScreen
- ChatViewModels.kt (95): RoomRow, ChatListViewModel, roomTitle, RoomUi, ChatRoomViewModel
### app/src/main/java/com/anjaz/app/ui/components/
- Components.kt (206): Tone, toneColors, Tag, AnjazCard, AnjazIcon, PrimaryButton, QuietButton +6
- DayStrip.kt (53): DayChip, DayStrip
- Motion.kt (69): rememberReduceMotion, Stagger, AmbientBackground
- SchemaForm.kt (114): SchemaForm
- TaskCard.kt (61): statusTone, TaskCard
### app/src/main/java/com/anjaz/app/ui/directory/
- DirectoryScreen.kt (103): DirectoryScreen
- DirectoryViewModel.kt (89): DirKind, SquareRow, DirectoryUi, DirForm, DirectoryViewModel
### app/src/main/java/com/anjaz/app/ui/home/
- HomeScreen.kt (153): HomeScreen
- HomeViewModel.kt (52): HomeUiState, HomeViewModel
### app/src/main/java/com/anjaz/app/ui/icons/
- Icons.kt (170): AnjazIcons
### app/src/main/java/com/anjaz/app/ui/login/
- LoginScreen.kt (87): LoginScreen
- LoginViewModel.kt (66): SessionState, LoginUiState, AppSessionViewModel, LoginViewModel
### app/src/main/java/com/anjaz/app/ui/more/
- ChatPlaceholder.kt (29): ChatPlaceholder
- MoreScreen.kt (106): MoreScreen
### app/src/main/java/com/anjaz/app/ui/nav/
- AnjazNav.kt (143): Routes, AnjazRoot
- SyncViewModel.kt (35): SyncViewModel
### app/src/main/java/com/anjaz/app/ui/reports/
- ReportsScreen.kt (75): ReportsScreen
- ReportsViewModel.kt (67): ReportsUi, ReportsViewModel
### app/src/main/java/com/anjaz/app/ui/sos/
- SosScreen.kt (188): SosScreen
- SosViewModel.kt (50): SosViewModel
### app/src/main/java/com/anjaz/app/ui/tasks/
- TasksScreen.kt (164): TasksScreen, NewTaskSheet
- TasksViewModel.kt (113): TaskFilter, TasksUiState, NewTaskState, TasksViewModel
### app/src/main/java/com/anjaz/app/ui/test/
- SelfTestScreen.kt (86): SeedViewModel, SelfTestScreen
### app/src/main/java/com/anjaz/app/ui/theme/
- Theme.kt (62): AnjazColors, Anjaz, AnjazTheme
- Type.kt (35): ltr
### app/src/main/res/drawable/
- ic_launcher_background.xml (6)
- ic_launcher_foreground.xml (11)
- ic_launcher_monochrome.xml (11)
- ic_notification.xml (6)
### app/src/main/res/mipmap-anydpi-v26/
- ic_launcher.xml (7)
- ic_launcher_round.xml (7)
### app/src/main/res/values/
- strings.xml (5)
- themes.xml (10)
### app/src/main/res/xml/
- data_extraction_rules.xml (16)
### app/src/test/java/com/anjaz/app/
- BackupReportTest.kt (112): BackupReportTest
- ChatSosTest.kt (131): ChatSosTest
- DirectoryTest.kt (96): DirectoryTest
- DomainTest.kt (108): RoleTest, TaskStatusTest, SchemaValidatorTest, BrandTest, DayMathTest
- MigrationTest.kt (64): MigrationTest
- RoomSelfTestTest.kt (22): RoomSelfTestTest
- SyncEngineTest.kt (181): SyncEngineTest
### backend/
- README.md (28)
### backend/firebase/
- firebase.json (5)
- firestore.rules (202)
- package-lock.json (10702)
- package.json (8)
### backend/firebase/test/
- rules.test.js (208): db, task
### backend/worker/
- package.json (8)
### backend/worker/src/
- auth.js (41): HttpError, keys, resetKeyCache, verifyIdToken
- b64.js (26): b64url, fromB64url, pemToDer
- backup.js (43): gzip, runBackup, readBackup
- cron.js (28): escalate
- firestore.js (97): encodeValue, encodeFields, decodeValue, decodeFields, decodeDoc, Firestore
- google.js (45): serviceAccount, signJwt, accessToken, resetTokenCache
- identity.js (37): Identity
- index.js (230): json, limited, body, str, profile, audit, createUser +12
- notify.js (57): sendFcm, leadsFor, targetsFor
- policy.js (53): validUsername, validPassword, isActive, isLead, inScope, shapeOk, canCreateUser +3
### backend/worker/test/
- harness.js (117): keys, idToken, d1, world
- worker.test.js (182): newUser
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
- outline_text.py (25): outline
### brand/variants/
- head-snippet.html (6)
### docs/
- repo-map.md (44)
### tools/
- repomap.py (146): files, symbols, build, main
<!-- repomap:auto:end -->
