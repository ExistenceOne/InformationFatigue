# InformationFatigue

정보 과부하(Information Fatigue)를 측정하기 위한 Android 데이터 수집 앱입니다.  
스마트폰 사용 행태(화면 켜짐/꺼짐, 앱 전환, 알림 상호작용 등)를 10분 간격으로 주기적으로 수집·저장하고, 분석용 CSV로 내보낼 수 있습니다.

---

## 목차

- [주요 기능](#주요-기능)
- [아키텍처](#아키텍처)
- [수집 지표](#수집-지표)
- [사용 Android API](#사용-android-api)
- [필요 권한](#필요-권한)
- [라이브러리 및 의존성](#라이브러리-및-의존성)
- [프로젝트 구조](#프로젝트-구조)
- [빌드 및 실행](#빌드-및-실행)

---

## 주요 기능

| 기능 | 설명 |
|---|---|
| **화면 이벤트 추적** | 화면 ON/OFF, DOZE, DOZE_SUSPEND 상태 변화를 실시간 추적 |
| **앱 사용 분석** | 앱 전환 횟수, 고유 앱 수, 평균 앱 세션 시간 측정 |
| **알림 상호작용 모니터링** | 사용자가 탭/스와이프로 처리한 알림 수 집계 |
| **주기적 데이터 수집** | Foreground Service + AlarmManager로 10분마다 자동 수집 |
| **오늘 요약 대시보드** | 현재 세션의 총 화면 시간, 알림 수, 시간당 알림 빈도, 앱 전환 수 표시 |
| **전체 기록 조회** | 수집된 모든 레코드를 RecyclerView로 열람 |
| **CSV 내보내기** | 수집 데이터를 CSV 파일로 내보내고 공유 |
| **온보딩** | 앱 최초 실행 시 필수 권한 설정을 단계별로 안내 |
| **부팅 자동 시작** | 기기 재부팅 후 수집 서비스 자동 재시작 |

---

## 아키텍처

```
MVVM (Model - View - ViewModel)
```

```
ui/
 ├── OnboardingActivity   ← 권한 설정 안내 (ViewPager2)
 ├── MainActivity         ← 실시간 대시보드 + 서비스 제어
 │     ├── MainViewModel  ← LiveData / 오늘 요약 집계
 │     ├── LogAdapter     ← RecyclerView 어댑터
 │     └── CsvExporter    ← CSV 파일 생성 및 공유
 └── HistoryActivity      ← 전체 수집 기록 열람

service/
 ├── DataCollectionService ← Foreground Service (10분마다 수집 트리거)
 ├── AlarmReceiver         ← AlarmManager 수신 → 서비스 기상
 └── BootReceiver          ← 부팅 완료 → 서비스 재시작

collector/
 ├── ScreenEventTracker    ← BroadcastReceiver 기반 화면 상태 추적
 ├── UsageDataCollector    ← UsageStatsManager 기반 앱 사용 분석
 ├── NotificationMonitorService ← NotificationListenerService 기반 알림 집계
 └── DataAggregator        ← 세 수집기 데이터를 DataRecord로 통합

data/
 ├── AppDatabase           ← Room DB 싱글톤
 ├── DataRecord            ← Room Entity (수집 레코드)
 ├── DataRecordDao         ← Room DAO
 └── DataRepository        ← Repository 패턴 (DAO 추상화)
```

---

## 수집 지표

10분 단위로 다음 지표들이 하나의 `DataRecord`에 저장됩니다.

| 컬럼명 | 타입 | 설명 |
|---|---|---|
| `start_time` | Long (ms) | 수집 구간 시작 Unix 타임스탬프 |
| `end_time` | Long (ms) | 수집 구간 종료 Unix 타임스탬프 |
| `screen_on_count` | Int | 화면이 켜진 횟수 |
| `screen_off_count` | Int | 화면이 꺼진 횟수 |
| `screen_on_duration_ms` | Long (ms) | 화면 켜짐 총 지속 시간 |
| `max_consecutive_ms` | Long (ms) | 연속 화면 켜짐 최대 시간 |
| `avg_app_session_ms` | Long (ms) | 앱 세션 평균 지속 시간 |
| `app_switch_count` | Int | 앱 전환 횟수 |
| `distraction_index` | Float | 주의 분산 지수 = `app_switch_count / screen_on_count` |
| `unique_apps_count` | Int | 사용된 고유 앱 수 |
| `notification_interaction_count` | Int | 알림 탭/스와이프 횟수 |
| `cumulative_screen_time_ms` | Long (ms) | 연속 4시간 비화면 기준으로 리셋되는 누적 화면 시간 |

---

## 사용 Android API

| API | 용도 |
|---|---|
| **`UsageStatsManager`** (`android.app.usage`) | 앱 전환 횟수, 고유 앱 수, 평균 세션 시간 수집 |
| **`NotificationListenerService`** | 사용자의 알림 탭(REASON_CLICK) / 스와이프 해제(REASON_CANCEL) 감지 |
| **`BroadcastReceiver`** | `ACTION_SCREEN_ON` / `ACTION_SCREEN_OFF` / `ACTION_DEVICE_IDLE_MODE_CHANGED` 수신으로 화면 상태 추적 |
| **`AlarmManager`** (setExactAndAllowWhileIdle) | 10분 간격 정확한 알람으로 데이터 수집 트리거 |
| **`PowerManager`** | `isInteractive()`, `isDeviceIdleMode()` 로 현재 화면/DOZE 상태 판별 |
| **`ForegroundService`** | 백그라운드에서 지속적인 화면 이벤트 수신 및 서비스 유지 |
| **`SharedPreferences`** | 누적 화면 시간, 서비스 실행 상태 등 경량 상태값 영속화 |
| **`FileProvider`** | 내부 저장소의 CSV 파일을 외부 앱과 안전하게 공유 |
| **`AppOpsManager`** | 사용 통계 권한(`PACKAGE_USAGE_STATS`) 부여 여부 런타임 확인 |
| **`Settings.ACTION_*`** | 온보딩에서 각 권한 설정 화면으로 딥링크 이동 |

---

## 필요 권한

| 권한 | 설명 |
|---|---|
| `PACKAGE_USAGE_STATS` | 앱 사용 통계 조회 (설정에서 수동 허용 필요) |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | 알림 리스너 서비스 바인딩 (설정에서 수동 허용 필요) |
| `FOREGROUND_SERVICE` | Foreground Service 실행 |
| `FOREGROUND_SERVICE_DATA_SYNC` | 데이터 동기화 유형 Foreground Service (API 34+) |
| `RECEIVE_BOOT_COMPLETED` | 부팅 완료 브로드캐스트 수신 |
| `SCHEDULE_EXACT_ALARM` | 정확한 알람 예약 (API 32 이하) |
| `USE_EXACT_ALARM` | 정확한 알람 사용 (API 33+) |
| `WAKE_LOCK` | 데이터 수집 중 CPU 슬립 방지 |
| `POST_NOTIFICATIONS` | 포그라운드 서비스 상태 알림 표시 (API 33+) |

---

## 라이브러리 및 의존성

| 라이브러리 | 버전 | 용도 |
|---|---|---|
| **Kotlin** | 2.0.21 | 기본 언어 |
| **KSP** (Kotlin Symbol Processing) | 2.0.21-1.0.28 | Room 어노테이션 프로세싱 |
| **AndroidX Core KTX** | 1.17.0 | Android 코어 Kotlin 확장 |
| **AndroidX AppCompat** | 1.7.1 | 하위 호환 UI 컴포넌트 |
| **Material Design Components** | 1.13.0 | MaterialButton, MaterialCardView 등 UI |
| **Room Runtime** | 2.6.1 | 로컬 SQLite 데이터베이스 ORM |
| **Room KTX** | 2.6.1 | Room Coroutine 확장 |
| **Room Compiler (KSP)** | 2.6.1 | Room DAO/Entity 코드 생성 |
| **Lifecycle ViewModel KTX** | 2.8.7 | MVVM ViewModel |
| **Lifecycle LiveData KTX** | 2.8.7 | 반응형 데이터 스트림 |
| **Kotlinx Coroutines Android** | 1.9.0 | 비동기 DB 쓰기/읽기 |
| **RecyclerView** | 1.3.2 | 수집 로그 목록 표시 |
| **ViewPager2** | 1.1.0 | 온보딩 스텝 슬라이드 |

### 빌드 환경

| 항목 | 값 |
|---|---|
| Android Gradle Plugin | 8.13.2 |
| compileSdk | 36 |
| targetSdk | 36 |
| minSdk | 24 (Android 7.0+) |
| Java 호환성 | VERSION_11 |

---

## 프로젝트 구조

```
InformationFatigue/
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/informationfatigue/
│       │   ├── collector/
│       │   │   ├── DataAggregator.kt          # 수집 데이터 통합 → DataRecord 생성
│       │   │   ├── NotificationMonitorService.kt  # 알림 상호작용 카운터
│       │   │   ├── ScreenEventTracker.kt      # 화면 상태 실시간 추적
│       │   │   └── UsageDataCollector.kt      # UsageStatsManager 앱 사용 분석
│       │   ├── data/
│       │   │   ├── AppDatabase.kt             # Room DB 싱글톤
│       │   │   ├── DataRecord.kt              # Room Entity
│       │   │   ├── DataRecordDao.kt           # Room DAO
│       │   │   └── DataRepository.kt          # Repository 패턴
│       │   ├── service/
│       │   │   ├── AlarmReceiver.kt           # AlarmManager 수신 리시버
│       │   │   ├── BootReceiver.kt            # 부팅 완료 리시버
│       │   │   └── DataCollectionService.kt   # Foreground Service (10분 주기 수집)
│       │   └── ui/
│       │       ├── history/
│       │       │   └── HistoryActivity.kt     # 전체 기록 열람
│       │       ├── main/
│       │       │   ├── CsvExporter.kt         # CSV 파일 생성 및 공유
│       │       │   ├── LogAdapter.kt          # RecyclerView 어댑터
│       │       │   ├── MainActivity.kt        # 메인 대시보드
│       │       │   └── MainViewModel.kt       # 오늘 요약 집계 ViewModel
│       │       └── onboarding/
│       │           └── OnboardingActivity.kt  # 권한 설정 온보딩
│       └── res/
│           └── layout/
│               ├── activity_main.xml
│               ├── activity_onboarding.xml
│               └── activity_history.xml
├── gradle/
│   └── libs.versions.toml                     # Version Catalog
└── README.md
```

---

## 빌드 및 실행

1. **Android Studio** (최신 버전 권장)에서 프로젝트 열기
2. `Run > Run 'app'` 또는 `Shift+F10`으로 빌드 및 실행
3. 앱 최초 실행 시 온보딩 화면에서 아래 권한을 차례로 허용:
   - **사용 통계 접근 허용** (설정 → 앱 > 특별한 앱 액세스 → 사용 통계 접근)
   - **알림 접근 허용** (설정 → 알림 > 알림 접근)
   - **배터리 최적화 제외** (설정 → 배터리 > 배터리 최적화 제외)
   - **정확한 알람 허용** (설정 → 앱 > 특별한 앱 액세스 → 알람 및 알림)
4. 메인 화면에서 **"수집 시작"** 버튼 탭 → 10분마다 데이터 자동 수집
5. **"CSV 내보내기"** 버튼으로 수집 데이터를 파일로 저장·공유 가능

> **최소 Android 버전**: Android 7.0 (API 24)
