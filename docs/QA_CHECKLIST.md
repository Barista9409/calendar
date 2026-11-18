# リリース前チェックリスト

リリース（release.yml の実行）の前に、毎回すべて確認する。過去に指摘された不具合の再発防止用。
「自動」は CI の単体テストで確認できるもの、「目視」はコードと画面を確認するもの。

| # | 過去の不具合 | 確認方法 |
|---|---|---|
| 1 | 週・日表示を横スワイプすると 1970 年に飛ぶ | 目視：TimelineView のページ位置が相対（今日基準）であること |
| 2 | 月ウィジェットが表示されない／更新後に壊れる | 目視：AppUpdatedReceiver（MY_PACKAGE_REPLACED）が manifest にあること。RemoteViews に使えない View（View, Space など）をレイアウトに使っていないこと |
| 3 | 月ウィジェットで予定が二重・三重に表示される（行の使い回し） | 自動：MonthWidgetRenderTest.recycledRowIsClearedBeforeDrawing |
| 4 | 予定が見えないのに「+2」だけ出る／入りきらない表示 | 自動：MonthLayoutTest, MonthWidgetRenderTest.overflowShowsDotsOnlyWhereHidden / noDotsWithoutHiddenEvents |
| 5 | 複数日の予定が帯でつながらない | 自動：MonthWidgetRenderTest.multiDayEventIsOneChipSpanningDays |
| 6 | 右端の列（土曜）が切れる・文字が引き伸ばされる | 目視：幅を dp で計算せず、weight（7 等分）で並べていること。ビットマップで文字を描いていないこと |
| 7 | ウィジェットを広げても表示件数が増えない | 目視：MonthWidget.spec が OPTION_APPWIDGET_SIZES から高さを取り、文字サイズ設定から行数を計算していること |
| 8 | 月ウィジェットの上下スクロール（範囲は 35 で「今月から 3 か月」に変更） | 自動：SyncStatusTest.monthWidgetShowsThisMonthAndTwoMore |
| 9 | 月の変わり目が分からない | 自動：MonthWidgetRenderTest.firstOfMonthShowsMonth |
| 10 | 予定詳細で日付が二重に出る | 自動：EventDateLineTest |
| 11 | 添付画像のプレビューが小さい・出ない | 目視：AttachmentsSection で画像・PDF は大きなカードで表示 |
| 12 | 詳細シートを閉じるボタンがない | 目視：SheetPanel の右上に × |
| 13 | ウィジェット選択画面のプレビュー画像がない | 目視：res/xml/widget_*.xml に previewImage |
| 14 | アプリの月表示：シートを開くと行が縮み、空きがあるのに「+N」 | 自動：MonthViewSizeTest。目視：MonthView の行の高さが表示領域ではなく最大の高さから決まること |
| 15 | 同じ共有カレンダーを複数アカウントで登録していると、開くたびにカレンダー名と予定の色が入れ替わる | 目視：カレンダー名と色は端末のカレンダー（アカウントごとの行）からだけ読む。Google の API で取った名前・色で上書きしない（その仕組みは削除済み） |
| 16 | アプリの月表示とウィジェットで予定の色の付き方・並び方が違う | 自動：MonthWeekItemsTest。目視：アプリの MonthView とウィジェットがどちらも MonthLayout.weekItems / lanes / fit を使い、すべて塗りつぶしの帯で描いていること |
| 17 | 色の設定：選んだ色が残らない（黒が「標準」扱いになる）・意味の分からない丸・色が多すぎる | 自動：SettingsColorsTest。目視：色は名前つき 8 色＋「ほかの色」、選択中は枠・チェック・太字。明るさは言葉で選ぶ。ウィジェットの設定はアプリの色と分かれている |
| 18 | 月ウィジェットで 1 日の最大件数を指定できない | 自動：MonthLayoutTest.fixedMaxShowsUpToMaxAndDotsForTheRest / rowHeightGrowsWithMax。目視：ウィジェットの見た目に「自動・2〜6件」 |
| 19 | マナーモードで通知に気づかない（バイブが無効だった）・通知の種類を選べない | 自動：ReminderStyleTest。目視：ReminderReceiver.ensureChannel で種類ごとのチャンネルを作り enableVibration していること、設定に「通知の種類」と「テスト通知を送る」 |
| 20 | 日付・時刻の選択ダイアログが見にくい（暗い下地に暗い文字・下地が透ける） | 自動：PopupContrastTest（全背景色×明るさ×メインの色で、ダイアログの下地が不透明・文字との差 4.5 以上） |
| 21 | バイブが鳴らないときがある・短い・止めるまで鳴らせない | 自動：ReminderStyleTest（サイレント時の判断・長さ・繰り返し）。目視：manifest に VIBRATE 権限、Buzz がアラーム扱いで直接振動、通知に［止める］・消す／開くで停止、最長5分 |
| 22 | 通知の［止める］でバイブが止まらない・［止める］がないときがある | 自動：ReminderStyleTest.stopButtonIsAlwaysFirstWhenItBuzzes。目視：Buzz はアプリ全体で 1 つの Vibrator（applicationContext から取得し使い回す）で鳴らし・止めていること |
| 23 | メニューの色を分けられない・おすすめテーマから選べない・共有ボタンがない | 自動：ThemePresetTest（8 種類が明るさに合い、すべて読みやすい／共有の文面にダウンロード先）。目視：ドロワーが MenuTheme、色とテーマに「おすすめのテーマ」「細かく調整する」「マイテーマ」、メニューと設定に「このアプリを共有」 |
| 24 | 共有のダウンロード先が APK の直リンクでない | 自動：ThemePresetTest.shareTextHasDownloadLinkAndSteps。目視：release.yml が calendar.apk も Release に置き、--latest を付けていること |
| 25 | ウィジェットで今どの月か分からない・祝日が赤くない | 自動：MonthWidgetRenderTest.firstOfMonthShowsMonth / holidayIsRed。目視：見出しタップで今月に戻る |
| 26 | 設定が機種変更で引き継がれない・初回に通知が来ない | 目視：manifest の fullBackupContent / dataExtractionRules に calendar_prefs と month_widget、初回起動時に通知の受け取り先を聞く |
| 27 | Google の審査・テストユーザー登録が必要（制限付き・機密の権限を使っていた） | 自動：AttachmentServiceTest（権限は drive.file だけ／説明への添付リンクの追加・削除・編集で消えない）。目視：CalendarApi を使っていない、カレンダー管理に「Google カレンダーで設定を開く」 |
| 28 | 予定の入力画面で添付できない | 自動：AttachmentServiceTest.editingKeepsAttachments。目視：入力画面に「ファイルを添付」（複数選択・×で外せる・添付済みの表示）、保存後に uploadPending でアップロード |
| 29 | 月ウィジェットの見出しと表示がずれる | 自動：SyncStatusTest.widgetTitleIsTodayNotTheScrolledMonth（見出しは今日の日付）。目視：［‹］［›］・見出しタップで goAsync してスクロールを待つ |
| 30 | Google カレンダーにある予定が出ない・同期の状態が分からない | 自動：SyncStatusTest.summaryJudgesByResultsNotSettings。目視：「カレンダーと同期」の一番上のまとめ、「＋ ほかのカレンダーを追加」と［追加］、manifest に READ_SYNC_SETTINGS / READ_SYNC_STATS / WRITE_SYNC_SETTINGS |
| 31 | 月ウィジェットの見出し・ボタンが分かりにくい、スクロール中に何月か分からない | 自動：MonthWidgetRenderTest.railShowsMonthOfTheWeek / firstOfMonthShowsMonth、SyncStatusTest（「本日：…」）。目視：見出しは中央の「本日：…」（タップで今日へ）と右の＋だけ、左に月の列、曜日 13sp 太字、偶数月の塗り分け、日ウィジェットも「本日：…」中央 |
| 32 | 同期オフの［設定を開く］でパスワードの画面に飛び、何をすればいいか分からない | 目視：［直す］はアプリ内で設定する。「困ったとき」の［スマホの同期の設定を開く］は ACCOUNT_SYNC_SETTINGS（account 付き）でそのアカウントの同期画面を開く |
| 33 | 実際は同期オンなのに「カレンダー同期がオフ」と赤く警告される | 自動：SyncStatusTest.summaryJudgesByResultsNotSettings（アカウントごとの設定の値は判断に使わない）。目視：アカウント見出しに警告を出さない。設定の値は「困ったとき」に参考として出し、「正しくないことがある」と書く |
| 34 | 同期の状態がちゃんと分からない・同期が確実に動かない | 自動：SyncStatusTest。目視：［今すぐ同期］は requestSync（MANUAL+EXPEDITED）。CalendarRepository の全書き込みと添付の説明更新の後に sync.localChanged(予定 ID) で送信待ちとして記録し UPLOAD を依頼。アプリを開いたとき requestIfStale、メニュー「今すぐ同期」、月ウィジェットの見出しタップでも依頼 |

| 35 | 月ウィジェットの動作が重い、今日に戻るボタンが欲しい | 自動：SyncStatusTest.monthWidgetShowsThisMonthAndTwoMore（今月から 3 か月だけ読む）。目視：上へは今月より前に行かない、見出し「本日：…」タップで今月の先頭へ戻る |
| 36 | 同期の状態が見えず「設定を確認できませんでした」になる・同期の依頼が効かない（Android 13 以降、見えないアカウントは読み取りが常にオフ・依頼は無視） | 目視：同期の依頼はアカウント指定なし requestSync(null, …)（必ず届く）、進み具合は getCurrentSyncs |
| 37 | 同期の進捗と完了が分からない | 自動：SyncStatusTest.syncProgressDone。目視：同期中は上のバーの下に細い線、カレンダー管理に「同期しています…」、押した同期は「同期が完了しました（H:mm）」か、60 秒で「同期を頼みました…」と表示 |
| 38 | 予定の詳細の作成者に共有カレンダーの ID が出る | 目視：予定の詳細に「作成者」を出さない（ユーザーの判断で表示なし） |
| 39 | 添付のプレビューが切れる・出すか選べない | 目視：画像・PDF は ContentScale.Fit で全体（縦長は最大 420dp）、設定 → 添付ファイル →「画像・PDF のプレビューを表示」でオフにできる |
| 40 | 設定やカレンダー管理の背景がメニューと違う、ウィジェットの見た目がアプリの設定にある | 目視：設定・カレンダーと Google 連携・アップデートは MenuTheme。設定トップに「ウィジェットの見た目」が無い（ウィジェット長押しの設定だけ） |
| 41 | メニューを開いたままアプリを閉じ、ウィジェットの予定から開くとメニューが開いたまま | 目視：MainActivity.handleIntent で外から開いたらメニュー・設定・カレンダー管理を閉じる（closeDrawer は replay 1 で再生成時も閉じる） |
| 42 | 今日ボタンを中央、検索を右上に | 目視：上のバーは左にメニューと年月（入りきらないと文字を小さく）、中央に［今日］、右上に検索 |
| 43 | 添付ファイルがどの予定のものか分からない | 自動：AttachmentServiceTest.driveFileNameHasDateAndTitle（「日付_予定名_元の名前」）。目視：詳細からの添付・入力画面からの添付の両方で付く |
| 44 | 予定を消したときの添付ファイルの扱い | 目視：単発・「すべて」の削除で添付があれば「添付ファイルも削除しますか？」（［ファイルは残す］／［ファイルも削除］、外側タップでやめる）。「元に戻す」なら消さない。「この予定のみ」「これ以降」では聞かない |
| 45 | メニューに最終同期日時が無い | 自動：ScreenRenderTest.menuShowsSyncingWhileRunning。目視：メニューの「同期」の下に「最後の同期：…」（同期中は「同期しています…」、問題があれば赤字で「押して直す」） |
| 46 | 同期画面がややこしい | 自動：SyncStatusTest.summaryJudgesByResultsNotSettings。目視：一番上に 1 枚だけ（同期できています／同期しています…／送信待ち N 件／送れていない変更があります［もう一度送る］／自動同期がオフです［オンにする］）。細かい値は ︙ →「困ったとき」 |
| 47 | ［直す］で全部まとめて直す | 目視：［もう一度送る］は送信を依頼（見えるアカウントは自動同期もオン）。［オンにする］は確認してからスマホ全体の自動同期をオン |
| 48 | 初回に同期の準備を案内 | 目視：初回の案内は、スマホ全体の自動同期がオフのときだけ（［準備する］でオンにするか確認）。1 回だけ |
| 49 | 下に引っぱって同期 | 目視：カレンダーの一番上で下に引っぱると今すぐ同期（PullToRefreshBox）、終わると「同期が完了しました」 |
| 50 | Google にまだ届いていない予定が分からない | 自動：SyncStatusTest.pendingUploadMark。目視：「↑」はこのアプリで変えてまだ届いていない予定だけ（SyncCenter.pendingEdits。DIRTY が 0 になる・行が消えると外れる） |
| 51 | Google 側の変更が同期で届いてもウィジェットが変わらない（アプリを開くまで古いまま） | 目視：WidgetRefreshJob（CalendarContract.CONTENT_URI の変更で動くジョブ、2〜10 秒でまとめて描き直し、動くたびに予約し直し）。CalendarApp.onCreate で予約、manifest に BIND_JOB_SERVICE のサービス |
| 52 | ウィジェットの更新が始まったか分からない | 自動：ScreenStartupTest.monthWidgetShowsSyncingUntilDone。目視：見出しタップで左にくるくる＋見出しが「同期しています…」、同期が終わるまで出たまま（WidgetSyncJob が終わるのを待って印を消す、印は 45 秒で無効） |
| 53 | 実際は同期できているのに「同期が止まっています」と出る（スマホがアカウントごとの設定を正しく返さない） | 自動：SyncStatusTest.summaryJudgesByResultsNotSettings。目視：判断は「スマホ全体の自動同期」「このアプリで変えた予定が届いたか（15 分で送れていない扱い）」「同期用の欄（CAL_SYNC1〜10）が変わったか＝最後の同期」だけ。WidgetRefreshJob とアプリ起動時にも checkRemoteSync |
| 54 | 「カレンダーと同期」を開くとアプリが落ちる（ViewModel の起動処理が、まだ用意していない状態を使っていた） | 自動：ScreenStartupTest（ViewModel を実際に作る）、ScreenRenderTest（カレンダーと同期・設定・メイン画面を実際に描く）。目視：ViewModel の init はクラスの最後に置く |
| 55 | 月ウィジェットの見出しタップでくるくるが出ない（直後の描き直しで消えていた） | 自動：ScreenStartupTest.monthWidgetShowsSyncingUntilDone（何度描き直しても出たまま。部分更新で出し消しせず、描くたびに印を見る） |
| 56 | 日ウィジェットの見出しタップでアプリが開き、更新されたか分からない | 目視：日ウィジェットの見出しタップは DayRefreshAction（印を付けて描き直し、WidgetSyncJob で同期。終わるまでくるくる＋「同期しています…」）。アプリは開かない |
| 57 | ウィジェットのくるくるがすぐ消える・同期の完了まで出ない | 自動：ScreenStartupTest.syncStateExpires。目視：WidgetSyncJob（急ぎのジョブ）が同期の開始と終了を待ち、終わったら WidgetSyncState.clear と描き直し。止められたときも印を消す |
| 58 | 「同期できています」なのに「最後の同期：まだ確認していません」（端末によっては Google カレンダーアプリ自身が同期していて、標準の同期の動きが見えない） | 自動：SyncStatusTest.externalChangeCountsAsSync / lastSyncLineFormat、ScreenRenderTest（「最後の同期」が出る）。目視：このアプリ以外がカレンダーを書き換えたら（自分の書き込みから 15 秒以上あと）「同期した」と記録。アプリ内は書き込みが落ち着いてから判定、閉じているときは WidgetRefreshJob で判定。［今すぐ同期］の見守りは 20 秒で終える |
| 59 | 最後の同期の日時の場所 | 目視：まとめの 1 枚の 2 行目、［今すぐ同期］（などのボタン）の右に「最後の同期：今日 H:mm」。状態にかかわらず出す |
| 60 | ［今すぐ同期］が終わっても「最後の同期」が押す前の時刻のまま | 自動：SyncStatusTest.finishedSyncUpdatesLastSync。目視：見守りが終わったとき、動きが見えなくても、このアプリの変更がすべて届いていれば終わった時刻を記録（届いていなければ「送信待ち」のまま） |
| 61 | メニューを開き直すと「同期しています…」が消える | 自動：ScreenRenderTest.menuShowsSyncingWhileRunning（同期中に 2 回開き直しても出る。「同期しています…」は 1 か所だけ、「今すぐ同期」の別項目はない） |
| 62 | 設定が重なっている（アカウント・カレンダーの表示・同期の状態が何か所にもある） | 自動：ScreenRenderTest.menuHasOnlyViewItems / settingsScreenOpens / accountsAndCalendarsScreenOpens / syncScreenOpens。目視：メニュー＝カレンダーのチェック（アカウントの見出しでまとめてオン・オフ）・「同期」1 行・「設定」・（新しい版があるときだけ）「アップデートがあります」。設定＝アカウントとカレンダー／同期／見た目／予定／このアプリについて（アップデート・共有）。カレンダーのオン・オフはメニューだけ、アカウントの「このアプリで使う」は「アカウントとカレンダー」だけ、同期の状態は「同期」の画面とメニューの 1 行だけ |
作業環境のビルドと単体テストが成功し、上の目視項目を確認してからリリースする。
