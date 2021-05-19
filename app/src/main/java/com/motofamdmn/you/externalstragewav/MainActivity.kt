//内部ストレージにwavファイルを保存するプログラム

package com.motofamdmn.you.externalstragewav

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.*
import kotlinx.android.synthetic.main.activity_main.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {

    private val LOG_TAG = "ExternalStrageTest"

    var audioRecord //録音用のオーディオレコードクラス
            : AudioRecord? = null
    val SAMPLING_RATE = 44100 //オーディオレコード用サンプリング周波数

    private var bufSize //オーディオレコード用バッファのサイズ
            = 0

    private lateinit var shortData: ShortArray //オーディオレコード用バッファ

    private val wav1: myWaveFile = myWaveFile()

    private var fileName: String = ""

    private var player: MediaPlayer? = null

    private var newRecordFlg = 0
    private var stopBtnFlg = 0

    //録音時間のカウント用、Handlerを使って定期的に表示処理をする
    private val dataFormat: SimpleDateFormat = SimpleDateFormat("mm:ss.S", Locale.US)
    private var count = 0
    private var xPosition = 0f
    private var period : Int = 100

    // 'Handler()' is deprecated as of API 30: Android 11.0 (R)
    private val handler: Handler = Handler(Looper.getMainLooper())

    //periodで設定した時間ごとに録音時間とプログレスバーを更新
    private val updateTime: Runnable = object : Runnable {
        override fun run() {
            recordTimeText.text = dataFormat.format(xPosition*1000)   //xPositionは0.1秒刻み
            handler.postDelayed(this, period.toLong())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

                var flag = 0

        if ( PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            // 設定済
            flag = 1
        }else{
            flag = 0
        }
        /*
        if ( PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this, Manifest.permission.PERMISSION_DENIED)) {
            if (false == ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                // [今後表示しない]指定 -> 拒否確定
            }
        }
        */

        var grantResults = IntArray(10){0}
        var perm = arrayOf("test", "good", "bad")

        // リクエスト識別用のユニークな値
        val REQUEST_PERMISSIONS_ID = 1000

        // リクエスト用
        val reqPermissions = ArrayList<String>()
        // リクエスト に追加
        reqPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        reqPermissions.add(Manifest.permission.RECORD_AUDIO)
        // パーミション確認
        ActivityCompat.requestPermissions(this, reqPermissions.toTypedArray(), REQUEST_PERMISSIONS_ID)

        //外部ストレージ使用可否確認
        if(isExternalStorageReadable()){
            flag = 1
            Log.e(LOG_TAG, "外部ストレージ利用可能")
        }else{
            flag = 0
            Log.e(LOG_TAG, "外部ストレージ利用不可")
        }

        val mydirName = "testwav" // 保存フォルダー
        val ExtFileName = "sample2.wav" // ファイル名

        fileName = extFilePath(mydirName, ExtFileName)

        // フォルダーを使用する場合、あるかを確認
        val myDir = File(Environment.getExternalStorageDirectory(), mydirName)
        if (!myDir.exists()) {
            // なければ、フォルダーを作る
            myDir.mkdirs()
            Log.e(LOG_TAG, "フォルダ作成")
        }

        //AudioRecordの初期化
        initAudioRecord()

        recordBtn.setOnClickListener {
            if(newRecordFlg == 1) {
                xPosition = 0.0f
                newRecordFlg = 0
                recordTimeText.text = dataFormat.format(0)
            }

            handler.post(updateTime)
            startAudioRecord()

        }//レコードボタンリスナの設定

        stopBtn.setOnClickListener {
            if(stopBtnFlg == 0){
                stopAudioRecord()
                handler.removeCallbacks(updateTime);
            }else{
                stopPlaying()
                stopBtnFlg = 0
            }
        }//ストップボタンリスナの設定

        playBtn.setOnClickListener {
            //wavファイルを再生する
            startPlaying()
        }//プレイボタンリスナの設定

        newRecordBtn.setOnClickListener {

            initAudioRecord()
            xPosition = 0.0f
            count = 0
            newRecordFlg = 1
            recordTimeText.text = dataFormat.format(0)

        }//新しいwavファイル作成、のリスト設定

    }


    // 承認の結果(オペレータの操作)をこのコールバックで判断
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        // リクエスト識別用のユニークな値
        val REQUEST_PERMISSIONS_ID = 1000

        if (requestCode == REQUEST_PERMISSIONS_ID) {
            // 識別IDでリクエストを判断
            if (grantResults.isNotEmpty()) {
                // 処理された
                for (i in permissions.indices) {
                    // 複数リクエストがあった場合
                    if (permissions[i] == Manifest.permission.WRITE_EXTERNAL_STORAGE) {
                        // 外部ストレージのパーミッション
                        if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                            var flag = 1// 許可
                        } else {
                            var flag = 0// 拒否
                        }
                    }
                }
            }
        }
    }

    // 外部ストレージが読み取り可能かどうかをチェック
    fun isExternalStorageReadable(): Boolean{
        val state = Environment.getExternalStorageState()
        return Environment.MEDIA_MOUNTED == state || Environment.MEDIA_MOUNTED_READ_ONLY == state
    }

    // 現在の外部ストレージのログ・ファイル名(パス含め)
    fun extFilePath(mydirName : String, ExtFileName : String): String{
        val myDir = Environment.getExternalStorageDirectory().getPath() +"/"+mydirName
        return  myDir+"/"+ ExtFileName
    }

    //AudioRecordの初期化
    private fun initAudioRecord() {

        // wavのデータサイズ
        var dataSize : Int = 0

        wav1.createFile(fileName)
        //wav1.createFile(SoundDefine.filePath)
        // AudioRecordオブジェクトを作成
        bufSize = AudioRecord.getMinBufferSize(
            SAMPLING_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLING_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufSize
        )
        shortData = ShortArray(bufSize / 2)

        // コールバックを指定
        audioRecord!!.setRecordPositionUpdateListener(object :
            AudioRecord.OnRecordPositionUpdateListener {
            // フレームごとの処理
            override fun onPeriodicNotification(recorder: AudioRecord) {
                // TODO Auto-generated method stub
                audioRecord!!.read(shortData, 0, bufSize / 2) // 読み込む
                wav1.addBigEndianData(shortData) // ファイルに書き出す

                dataSize = wav1.getDataSize()
                // xPosition 現在のwav波形時間
                xPosition = dataSize.toFloat() / (SAMPLING_RATE * 2)  //1秒はサンプリング周波数 x 2 byte　なのでxPositionは1秒を表す

                if(xPosition > 200.0){
                    stopAudioRecord()  //20秒を超えたら録音停止
                    val toast = Toast.makeText(applicationContext, "  20秒超、録音停止  ", Toast.LENGTH_LONG)
                    // 位置調整
                    toast.setGravity(Gravity.CENTER, 0, -400)
                    toast.show()
                }

            }

            override fun onMarkerReached(recorder: AudioRecord) {
                // TODO Auto-generated method stub
            }
        })
        // コールバックが呼ばれる間隔を指定
        audioRecord!!.positionNotificationPeriod = bufSize / 2
    }

    //オーディオレコードを開始する
    private fun startAudioRecord() {
        audioRecord!!.startRecording()
        audioRecord!!.read(shortData, 0, bufSize / 2)
    }

    //オーディオレコードを停止する
    private fun stopAudioRecord() {
        audioRecord!!.stop()

    }

    //再生する
    private fun startPlaying() {

        //wavファイルを再生する
        player = MediaPlayer().apply {
            try {
                setDataSource(fileName)
                prepare()
                start()
            } catch (e: IOException) {
                Log.e(LOG_TAG, "prepare() failed")
            }
        }
    }

    private fun stopPlaying() {
        player?.release()
        player = null
    }

}