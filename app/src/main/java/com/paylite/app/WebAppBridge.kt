package com.paylite.app

import android.content.Context
import android.os.Environment
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.chaquo.python.Python
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WebAppBridge(
    private val context: Context,
    private val webView: WebView) {
    @JavascriptInterface
    fun isApp(): Boolean {
        return true
    }

    @JavascriptInterface
    fun getVersion(): String {
        return "1.0.0"
    }
    @JavascriptInterface
    fun processImage(key_answer: String, imageBase64: String) {
        try {

            Log.d("C_BRIDGE_TEST", "Called with Key Answer : $key_answer and ImageBase64 : $imageBase64")

            val py = Python.getInstance()
            val module = py.getModule("processor")

            val result = module.callAttr("process_data_ljk", key_answer, imageBase64).toString()

            Log.d("C_BRIDGE_RESULT", result)

            val json = JSONObject(result)

            val status = json.getInt("status")

            if (status == 200) {

                val db = DatabaseHelper(context)

                val scan = ScanResult(
                    id = json.optInt("id", ),
                    npsn = json.optString("npsn", null),
                    kodeSoal = json.getString("kode_soal"),
                    nisn = json.getString("nisn"),
                    kunciJawaban = json.getString("key_answers"),
                    jawabanSiswa = json.getString("detected_answers"),
                    benar = json.getInt("correct"),
                    salah = json.getInt("wrong"),
                    score = json.getInt("score")
                )

                db.insertScan(scan)

                val count = db.getTotalScan()

                webView.post {
                    webView.evaluateJavascript("setHistoryCount($count)", null)
                }

            }

            webView.post {
                webView.evaluateJavascript(
                    "showResult(${JSONObject(result)})",
                    null
                )
            }

        } catch (e: Exception) {
            Log.e("C_PYTHON_ERROR", Log.getStackTraceString(e))

            webView.post {
                webView.evaluateJavascript(
                    "alert('ERROR: ${e.message}')",
                    null
                )
            }
        }
    }

    @JavascriptInterface
    fun getHistoryCount(){

        val db = DatabaseHelper(context)

        val count = db.getTotalScan()

        webView.post {
            webView.evaluateJavascript("setHistoryCount($count)", null)
        }
    }

    @JavascriptInterface
    fun getHistory() {

        val db = DatabaseHelper(context)
        val data = db.getAllScans()

        val jsonArray = org.json.JSONArray()

        data.forEach {

            val obj = JSONObject()
            obj.put("id", it.id)
            obj.put("npsn", it.npsn)
            obj.put("kode_soal", it.kodeSoal)
            obj.put("nisn", it.nisn)
            obj.put("kunci_jawaban", it.kunciJawaban)
            obj.put("jawaban_siswa", it.jawabanSiswa)
            obj.put("benar", it.benar)
            obj.put("salah", it.salah)
            obj.put("score", it.score)

            jsonArray.put(obj)

        }

        webView.post {
            webView.evaluateJavascript(
                "showHistory($jsonArray)",
                null
            )
        }
    }

    fun jsonArrayToString(jsonArrayString: String): String {

        val arr = org.json.JSONArray(jsonArrayString)

        val list = mutableListOf<String>()

        for (i in 0 until arr.length()) {
            list.add(arr.getString(i))
        }

        return list.joinToString(":")
    }

    @JavascriptInterface
    fun exportCSV() {

        val db = DatabaseHelper(context)
        val data = db.getAllScans()

        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())

        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "ljk_scan_result_$timestamp.csv"
        )

        val writer = FileWriter(file)

        writer.append("id,npsn,kode_soal,nisn,kunci_jawaban,jawaban_siswa,benar,salah,score\n")

        data.forEachIndexed { index, d ->

            writer.append(
                "${index + 1}," +
                        "${d.npsn}," +
                        "${d.kodeSoal}," +
                        "${d.nisn}," +
                        "${jsonArrayToString(d.kunciJawaban)}," +
                        "${jsonArrayToString(d.jawabanSiswa)}," +
                        "${d.benar}," +
                        "${d.salah}," +
                        "${d.score}\n"
            )

        }

        writer.flush()
        writer.close()

        webView.post {
            webView.evaluateJavascript(
                "alert('File berhasil di download ke folder Download dengan nama : ljk_scan_result_$timestamp.csv')",
                null
            )
        }
    }

    @JavascriptInterface
    fun deleteAllHistory(){

        val db = DatabaseHelper(context)

        db.deleteAllScans()

        webView.post {
            webView.evaluateJavascript("historyDeleted()", null)
        }

    }

    @JavascriptInterface
    fun deleteHistoryItem(id:Int){

        val db = DatabaseHelper(context)

        db.deleteScanById(id)

        webView.post {
            webView.evaluateJavascript("itemDeleted($id)", null)
        }

    }
}