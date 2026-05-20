# Searchable - コンテナ環境での実行ガイド

Searchable を Docker / Kubernetes 等のコンテナ環境で動かす際の
インデックス配置・永続化・配布戦略をまとめたガイド。

公式デモ環境 (`docker-compose.yml`) の起動手順は
[`demo-setup.md`](./demo-setup.md) を参照。本書はそれを踏まえて、
本番運用や独自アプリへの組み込みで判断が必要になる設計論点を扱う。

## 1. インデックスファイルの配置

Searchable は **設定で指定したディレクトリ** に Lucene インデックスと
H2 メタデータ DB を書き出す。コンテナ内のパスは設定次第。

### 1.1 公式 Docker イメージの既定値

`docker/Dockerfile` と `docker-compose.yml` で配布しているイメージは
以下の構成。

| 項目 | 既定値 |
| --- | --- |
| データ root | `/data` |
| Lucene インデックス | `/data/indexes/<namespace-id>/` |
| メタデータ DB (H2) | `/data/metadata.mv.db` |
| 宣言ボリューム | `VOLUME ["/data"]` |

環境変数で上書き可能。

| 変数 | 既定値 | 説明 |
| --- | --- | --- |
| `SEARCHABLE_DATA_DIRECTORY` | `/data` | データ root |
| `SEARCHABLE_INDEX_DIRECTORY` | `/data/indexes` | Lucene index 配置先 |
| `SEARCHABLE_PERSISTENCE_URL` | `jdbc:h2:/data/metadata;MODE=PostgreSQL` | H2 接続先 |

ディレクトリ構造:

```text
/data/
├── metadata.mv.db          # H2 メタデータ DB
└── indexes/
    └── <namespace-id>/     # namespace ごとの Lucene インデックス
        ├── segments_N
        ├── *.cfs
        └── ...
```

### 1.2 独自アプリへの組み込み

`searchable-core` を組み込んだ独自アプリでも仕組みは同じ。

- `application.properties`: `searchable.index.directory=/var/lib/myapp/indexes`
- 環境変数: `SEARCHABLE_INDEX_DIRECTORY=/var/lib/myapp/indexes`

設定キーの全体像は [`usage.ja.md`](./usage.ja.md) の設定セクションを参照。

### 1.3 ボリュームに置く際の最低限のルール

- インデックス書込み先は **必ず永続ボリュームをマウントする**
  （コンテナ書込み層に置くと再作成で全消失）
- メタデータ DB とインデックスは **整合性をペアで保つ必要があるため、
  同一ボリュームにまとめる**
- 書込みは **単一プロセス・単一マウント** が原則
  （Lucene の `MMapDirectory` がファイルロックを取る）

## 2. 永続化戦略の選択肢

「インデックスをどこから持ってくるか」で4パターンに大別できる。

| 方式 | 概要 | 向き |
| --- | --- | --- |
| (A) 永続ボリュームに置きっぱなし | PVC / named volume にそのまま保存 | 単一インスタンス・中規模まで |
| (B) 起動時に再構築 | ソース DB から都度 ingest し直す | ステートレス志向 / 件数少なめ |
| (C) backup / restore で配布 | スナップショットを S3 等から展開 | DR / マルチレプリカ |
| (D) イメージに焼き込み | ビルド時に index を含めて配布 | read-mostly / 更新頻度低い |

### 2.1 (A) 永続ボリュームに置きっぱなし

- 単一書込み Pod を前提に、PVC や Docker named volume に保存
- 公式 `docker-compose.yml` の `searchable-data` ボリュームがこのパターン
- **長所**: 最も素直、再起動でインデックスが消えない
- **弱点**:
  - スケールアウト時に同じボリュームを複数 Pod から書込み共有できない
    （ReadWriteOnce 制約）
  - NFS 系を選ぶと性能・整合性ともに劣化する（後述 §3）

### 2.2 (B) 起動時に再構築

- イメージは状態を持たず、起動時にソースから ingest し直す
- API: `POST /api/v1/index/rebuild`
- CLI: `searchable-cli rebuild --namespace <id>` ＋ `ingest`
  （[`setup-guide.md`](./setup-guide.md) §8 参照）
- **長所**: ステートレス、Pod を増やしやすい
- **弱点**: 件数が多いと起動が重い、再構築中の検索可否を別途設計

### 2.3 (C) backup / restore で配布

- `BackupService` が Lucene の snapshot とメタデータ DB をまとめて取り出す
  （`architecture.md` §`BackupService`、`usage.ja.md` の運用 API 表）
- API: `POST /api/v1/admin/backup` / `POST /api/v1/admin/restore`
- 取得したアーカイブを S3 等に置き、新コンテナ起動時に restore する
- **長所**: DR、環境間複製、マルチレプリカ配布の基盤になる
- **注意**: 命名規約ベースの世代管理（`tasks.md` の TASK-173）が ⏳ のため、
  ゼロダウンタイム再構築まわりは整備途上

### 2.4 (D) イメージに焼き込み

- ビルド時に `searchable-cli ingest` を走らせ、`/data` を含むイメージを作る
- 起動が速く、書込みしないので Pod 並列化が容易
- **長所**: immutable、スケール容易
- **弱点**: 更新のたびにイメージ再ビルド & 再デプロイ

## 3. ストレージ性能 — なぜ Lucene は影響を受けやすいか

「永続ボリューム = ネットワークドライブで遅い」という誤解があるが、
実態はストレージ種別で大きく異なる。

### 3.1 前提

Lucene の `MMapDirectory` は **mmap + OS の page cache** に強く依存して
高速化している。これが効くには:

- 低レイテンシ（μs 〜 数 ms オーダー）のランダム読み取り
- POSIX セマンティクス（mmap、fsync、ファイルロック）が素直に通る
- page cache が安定して効く

ネットワーク経由のストレージはここがどれも弱くなる。

### 3.2 永続ボリュームの種別と Lucene 適性

| タイプ | 例 | 接続形態 | Lucene 適性 | RWX 可否 |
| --- | --- | --- | --- | --- |
| ローカル SSD/NVMe | hostPath, local-PV, Docker bind mount | 物理ローカル | ◎ 最速 | × |
| クラウドブロック | AWS EBS, GCP PD, Azure Disk, Ceph RBD | 1ノード排他マウント | ○ ローカル並み | × (RWO) |
| ネットワーク FS | AWS EFS, NFS, SMB/CIFS, GlusterFS | 純粋な NFS over network | △〜× (mmap 弱い) | ○ (RWX) |
| 分散 FS | JuiceFS, S3FS, CephFS | object/metadata 分離 | × (mmap 互換性に難) | ○ |

K8s の `PersistentVolumeClaim` で `gp3` 等を取った場合は EBS = ブロック
ストレージで、Pod が動くノードに直接アタッチされた SSD のように見える。
**これなら遅くならない。**

遅くなるのは EFS / NFS / SMB を意識的に選んだ場合で、これは
「複数 Pod から同時マウントしたい（RWX）」要件があるとき。

### 3.3 ベンチ感覚（目安）

同一インデックス・同一クエリでの典型値:

| ストレージ | cold | warm |
| --- | --- | --- |
| NVMe ローカル | 0.5〜2 ms | 0.5〜2 ms |
| EBS gp3 | 5〜10 ms | 1〜3 ms |
| EFS / NFS | 30〜100 ms | 5〜15 ms |
| S3FS 系 | 数百 ms 〜秒 | — |

Lucene は1クエリで数十〜数百個の posting list / docvalue ブロックを
読みに行くため、**1 read あたりのレイテンシ差が倍率で効く**。

### 3.4 避けるべき組み合わせ

- EFS / NFS に Lucene index 本体を置く + 書込み Pod
- S3FS / goofys で `/data/indexes` をマウント
  （mmap が正常に動かないことがある）
- 複数 Pod から同じ NFS を RWX で書込み
  （Lucene の `write.lock` が壊れる）

## 4. Kubernetes でローカル並みのストレージを使う

ネットワーク FS を避けたい場合、K8s でも「ローカルディスクの速度」が
出る手段が複数ある。

### 4.1 `emptyDir`（ノードローカルのスクラッチ領域）

- Pod が走っているノードの実ローカル FS（または tmpfs）に作られる
- レイテンシは **NVMe/SSD ベタ書き** と同等
- Pod が消えると中身も消える — (B) (C) パターンとの相性が良い

典型例:

```yaml
volumes:
  - name: index-cache
    emptyDir:
      sizeLimit: 50Gi
      # medium: Memory  # tmpfs にしたい場合
initContainers:
  - name: restore-index
    image: searchable:latest
    args: ["restore", "--from", "s3://bucket/snapshot/latest", "--to", "/data"]
    volumeMounts:
      - {name: index-cache, mountPath: /data}
containers:
  - name: searchable
    volumeMounts:
      - {name: index-cache, mountPath: /data}
```

### 4.2 Local PersistentVolume

K8s 公式機能の `local` volume / Local PV。

- PV が**特定ノードのローカルディスク**に固定される
  （`nodeAffinity` 必須）
- Pod もそのノードに張り付くので、Pod 再起動でインデックスを失わない
- 代表的な provisioner:
  - Rancher `local-path-provisioner`
  - OpenEBS LocalPV（hostpath / device / LVM / ZFS バリアント）
  - TopoLVM（LVM ベース）
- **弱点**: そのノードが死ぬとボリュームごと失われる
  → 別途バックアップ（S3 等）を取る前提

### 4.3 レプリケート型ブロックストレージ

ノード障害耐性も欲しい場合の選択肢。
読み取りはローカルレプリカから返るため、Lucene 用途でも実用速度が出る。

- Longhorn（Rancher 製、3 レプリカ）
- OpenEBS Mayastor / Replicated PV（NVMe-oF）
- Portworx（商用）

EFS のような NFS とは別物で、性能特性はクラウドブロックに近い。

### 4.4 クラウド別の素早い手段

- **EKS**: `emptyDir` がノードの EBS（または NVMe instance store:
  `i3` / `c5d` / `m5d` 系）に乗る。instance store を使えば実 NVMe
- **GKE**: `local-ssd` 付きノードプール（`--local-ssd-count`）を
  指定すると emptyDir が NVMe に張る
- **AKS**: `ephemeral OS disk` ノードや `temporary disk` を活用

## 5. インデックス配布パターン（writer → readers）

(B) (C) を採るとき、再構築済みインデックスを各 reader Pod にどう届けるかが
肝になる。問題は2軸に分解できる。

- **Transport**: どこに置いて配るか
- **Activation**: 各 Pod でいつ切り替えるか

### 5.1 Transport の選択肢

| 方式 | 特徴 | 向き |
| --- | --- | --- |
| S3 / GCS / Azure Blob | バージョニング・ライフサイクル・IAM が揃う | ◎ 第一候補 |
| EFS / NFS を配布専用にマウント | 読み取り時はローカルに**コピーしてから**使う | △ 既存に NFS があれば |
| OCI Registry (ORAS) | インデックスを OCI artifact として push/pull | ○ GitOps と相性◎ |
| 内部 HTTP / MinIO | クラウド外に出したくない場合 | ○ オンプレ |

Lucene セグメントは **commit 後 immutable** なので、tar 一発で
アトミックに取り出せる。圧縮は **zstd** が相性良く、転送量を
40〜60% 削れる。

S3 配置例:

```text
s3://searchable-snapshots/<namespace>/
  ├── v20260520-101500.tar.zst
  ├── v20260520-104500.tar.zst
  └── LATEST                      # テキスト1行: 現在の正版バージョン
```

### 5.2 Activation A — ローリング再起動方式（推奨）

```text
[Writer Job] --backup--> S3 (LATEST=vNNN)
                          │
                          ▼
[Controller / CI] --kubectl rollout restart--> Reader Deployment
                          │
                          ▼
[Reader Pod] initContainer: aws s3 cp <LATEST> | restore
             main: 起動 → readinessProbe OK → トラフィック投入
```

- 切替を K8s の Rolling Update に乗せる
- 可用性は `maxUnavailable` / `maxSurge` で制御
- 反映ラグ = rollout 開始 → 全 Pod 入替え完了（数十秒〜数分）
- **現状の Searchable で最も確実に動く方式**

### 5.3 Activation B — サイドカー pull + ホットリロード方式

```text
[Reader Pod]
  ├─ main: searchable
  └─ sidecar: S3 LATEST を poll
       新版検知 → /data/next/ にダウンロード
       → searchable に reload signal
       → 旧 dir を grace period 後に削除
```

- Pod 再起動なしで切り替わり、反映が秒オーダー
- Lucene 側は `SearcherManager.maybeRefresh()` 相当で IndexReader を
  差し替える設計が必要
- **前提**: ディレクトリ差し替え or 別 namespace 経由の切替 API
- `tasks.md` の TASK-173（`<root>/<namespaceId>/<timestamp>/` の
  atomic rename + ref-count GC）が ⏳ のため、本番投入は完成後が現実的

### 5.4 Activation C — セグメント単位の差分配布

- writer の commit 後の新規セグメントだけを rsync / S3 sync する
- 転送量が小さく、反映ラグも小さい
- Lucene の commit point と `segments_N` の整合性を厳密に扱う必要があり、
  自作するなら難度が高い
- **現時点の Searchable のスコープ外**

## 6. 推奨構成（Searchable の現実解）

### 6.1 書込み側

- 単一の writer Pod または `CronJob` で再構築
- 完了後に `/api/v1/admin/backup` → tar+zstd → S3 アップロード
- **`LATEST` の更新を最後にやる**（実体アップロード完了後にポインタ更新）
- 古いスナップショットは S3 Lifecycle で N 世代保持

### 6.2 読取り側

- Deployment、`emptyDir` + initContainer で S3 から restore
- annotation `searchable.io/snapshot-version: vNNN` を Deployment に書き、
  これが変わったら自動 rolling restart
- 反映トリガは ArgoCD / Flux / `kubectl rollout restart` を CI から叩く
- readinessProbe は **restore 完了＋初回 warmup クエリ通過後** に Ready

### 6.3 スループットを上げたい場合

- emptyDir + 初回 warmup でローカル読み出しに揃える
- Pod を水平スケール（書込みは別系統に分離）
- どうしてもレプリカ間で完全リアルタイムが必要なら、TASK-173 完了後に
  サイドカー pull 方式へ移行

## 7. 落とし穴ポイント

- **`LATEST` を先に更新しない** — 古典的だがミスりやすい
- **再スケジュール時に全レプリカが同時に S3 を叩くと詰まる** —
  `maxSurge: 1` で順次入替え
- **initContainer のタイムアウト** — デフォルト 5 分で足りない場合あり。
  インデックスサイズに応じて延ばす
- **転送中の checksum 検証** — tar が途中で切れたまま起動して
  "Lucene index corrupted" になると原因究明に時間を食う。
  S3 ETag または別途 sha256 を併送
- **メタデータ DB (H2) を必ず同梱** — H2 とインデックスは整合性ペア。
  `/data/metadata.mv.db` と `/data/indexes/` を同じ snapshot に入れる
- **EFS / NFS にインデックス本体を置かない** — §3.4 のとおり
- **複数 Pod から同一ボリュームに書き込まない** —
  Lucene の `write.lock` が壊れる前提なので不可

## 8. 関連ドキュメント

- [`demo-setup.md`](./demo-setup.md) — Docker Compose ベースのデモ環境
- [`setup-guide.md`](./setup-guide.md) — Phase 1 構成のセットアップ手順
- [`usage.ja.md`](./usage.ja.md) — 設定キー一覧、運用 API
- [`architecture.md`](./architecture.md) — `BackupService` ほかの内部構造
- [`tasks.md`](./tasks.md) — TASK-173（インデックス世代管理）の進捗

---

**Document Version**: 1.0
**Last Updated**: 2026-05-20
**Status**: Phase 1 / 運用ドラフト
