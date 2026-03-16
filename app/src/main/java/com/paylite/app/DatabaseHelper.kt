package com.paylite.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, "ljk_scanner.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {

        val createTable = """
            CREATE TABLE scan_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                npsn TEXT,
                kode_soal TEXT,
                nisn TEXT,
                kunci_jawaban TEXT,
                jawaban_siswa TEXT,
                benar INTEGER,
                salah INTEGER,
                score INTEGER,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """.trimIndent()

        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {

        db.execSQL("DROP TABLE IF EXISTS scan_history")
        onCreate(db)

    }

    fun insertScan(data: ScanResult): Long {

        val db = writableDatabase

        val values = android.content.ContentValues().apply {
            put("npsn", data.npsn)
            put("kode_soal", data.kodeSoal)
            put("nisn", data.nisn)
            put("kunci_jawaban", data.kunciJawaban)
            put("jawaban_siswa", data.jawabanSiswa)
            put("benar", data.benar)
            put("salah", data.salah)
            put("score", data.score)
        }

        return db.insert("scan_history", null, values)
    }

    fun getTotalScan(): Int {

        val db = readableDatabase

        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM scan_history",
            null
        )

        var count = 0

        if(cursor.moveToFirst()){
            count = cursor.getInt(0)
        }

        cursor.close()

        return count
    }

    fun getAllScans(): List<ScanResult> {

        val list = mutableListOf<ScanResult>()
        val db = readableDatabase

        val cursor = db.rawQuery("SELECT * FROM scan_history", null)

        if (cursor.moveToFirst()) {
            do {

                val scan = ScanResult(
                    id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                    npsn = cursor.getString(cursor.getColumnIndexOrThrow("npsn")),
                    kodeSoal = cursor.getString(cursor.getColumnIndexOrThrow("kode_soal")),
                    nisn = cursor.getString(cursor.getColumnIndexOrThrow("nisn")),
                    kunciJawaban = cursor.getString(cursor.getColumnIndexOrThrow("kunci_jawaban")),
                    jawabanSiswa = cursor.getString(cursor.getColumnIndexOrThrow("jawaban_siswa")),
                    benar = cursor.getInt(cursor.getColumnIndexOrThrow("benar")),
                    salah = cursor.getInt(cursor.getColumnIndexOrThrow("salah")),
                    score = cursor.getInt(cursor.getColumnIndexOrThrow("score"))
                )

                list.add(scan)

            } while (cursor.moveToNext())
        }

        cursor.close()

        return list
    }

    fun deleteAllScans(){

        val db = writableDatabase
        db.execSQL("DELETE FROM scan_history")

    }

    fun deleteScanById(id:Int){

        val db = writableDatabase

        db.delete(
            "scan_history",
            "id=?",
            arrayOf(id.toString())
        )

    }

}