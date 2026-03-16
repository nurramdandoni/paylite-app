package com.paylite.app

data class ScanResult(
    val id:Int,
    val npsn: String?,
    val kodeSoal: String,
    val nisn: String,
    val kunciJawaban: String,
    val jawabanSiswa: String,
    val benar: Int,
    val salah: Int,
    val score: Int
)
