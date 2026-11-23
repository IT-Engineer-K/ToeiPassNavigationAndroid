# ToeiPassNavigationAndroid

都営交通（都営地下鉄・日暮里・舎人ライナー・都電荒川線・都営バス）の無料乗車券（乗車証）に対応し、都営区間（0円）＋JR・東京メトロ・私鉄を組み合わせた最安・最適ルートを検索できる Android ネイティブアプリです。

## 技術スタック
- **Kotlin** `2.1.0` + **Jetpack Compose (Material 3)** (`compose-bom:2026.04.01`)
- **AGP** `8.10.0` / **Gradle** `8.11.1`
- **compileSdk / targetSdk** `36` (Android 16) / **minSdk** `26`
- **完全オフライン動作**: `app/src/main/assets/transit-data.json` を同梱（アプリ内から JSON ファイル読み込みによる手動更新にも対応）

## ビルド・テスト
```bash
./gradlew testDebugUnitTest assembleDebug
```
