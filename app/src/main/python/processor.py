import base64
import numpy as np
import cv2
import json


def process_ljk(key_answer, imageBase64):

    # decode base64
    image_bytes = base64.b64decode(imageBase64)

    np_arr = np.frombuffer(image_bytes, np.uint8)

    img = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)

    # grayscale
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

    # encode kembali
    _, buffer = cv2.imencode(".jpg", gray)

    gray_base64 = base64.b64encode(buffer).decode("utf-8")

    # parsing kunci jawaban
    answers = key_answer.split(",")

    result = {
        "status":200,
        "message":"Scan Berhasil!",
        "image": gray_base64,
        "total_question": len(answers),
        "detected_answers": ["A","C","B","D","A"],
        "score": 80,
        "correct": 16,
        "wrong": 4
    }

    return json.dumps(result)

def process_data_ljk(key_answer, imageBase64):
    # --- CONFIG ---
    kunci_jawaban = [x.strip().upper() for x in key_answer.split(",") if x.strip()]
    total_soal = len(kunci_jawaban)
    # kelas 4
    # kunci_jawaban = [
    #     'B', 'C', 'C', 'C', 'B', 'B', 'C', 'C', 'B', 'C',
    #     'B', 'B', 'C', 'C', 'A', 'C', 'C', 'C', 'B', 'C'
    # ]
    preview_max_width = 400  # Lebar maksimum saat preview (biar nggak kegedean di layar)
    output_width, output_height = 250, 400  # Ukuran hasil crop

    # --- LOAD GAMBAR ---
    # decode base64
    image_bytes = base64.b64decode(imageBase64)
    np_arr = np.frombuffer(image_bytes, np.uint8)

    original = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)

    if original is None:
        return json.dumps({
            "status": 400,
            "message": "Gambar tidak bisa dibaca"
        })

    # --- DETEKSI ARUCO UNTUK AUTO PERSPEKTIF ---
    aruco_dict = cv2.aruco.getPredefinedDictionary(cv2.aruco.DICT_4X4_50)
    # not compatible in chaquopy
    # aruco_params = cv2.aruco.DetectorParameters()
    # detector = cv2.aruco.ArucoDetector(aruco_dict, aruco_params)
    # corners, ids, rejected = detector.detectMarkers(original)
    aruco_params = cv2.aruco.DetectorParameters_create()
    corners, ids, rejected = cv2.aruco.detectMarkers(
        original,
        aruco_dict,
        parameters=aruco_params
    )

    if ids is None or len(ids) < 4:
        return json.dumps({
            "status": 400,
            "message": "Marker tidak terdeteksi lengkap"
        })

    # Simpan center tiap marker
    aruco_centers = {}

    for i in range(len(ids)):
        marker_id = ids[i][0]
        corner = corners[i][0]

        # hitung titik tengah marker
        center_x = int(np.mean(corner[:, 0]))
        center_y = int(np.mean(corner[:, 1]))

        aruco_centers[marker_id] = (center_x, center_y)

        # gambar center (debug)
        cv2.circle(original, (center_x, center_y), 10, (0, 0, 255), -1)
        cv2.putText(original, str(marker_id), (center_x-20, center_y-20),
                    cv2.FONT_HERSHEY_SIMPLEX, 1, (255,0,0), 2)

    required_ids = [0,1,2,3]

    for rid in required_ids:
        if rid not in aruco_centers:
            return json.dumps({
                "status": 400,
                "message": f"Marker ArUco ID {rid} tidak ditemukan"
            })



    # Hitung rasio scaling preview
    h, w = original.shape[:2]
    scale = preview_max_width / w if w > preview_max_width else 1.0
    preview = cv2.resize(original, (int(w * scale), int(h * scale)))


    # --- TRANSFORMASI PERSPEKTIF ---
    # URUTAN HARUS SESUAI POSISI FISIK
    # 0 = kiri atas
    # 1 = kanan atas
    # 2 = kanan bawah
    # 3 = kiri bawah

    pts1 = np.float32([
        aruco_centers[0],  # kiri atas
        aruco_centers[1],  # kanan atas
        aruco_centers[2],  # kanan bawah
        aruco_centers[3]   # kiri bawah
    ])

    pts2 = np.float32([
        [0, 0],
        [output_width, 0],
        [output_width, output_height],
        [0, output_height]
    ])
    matrix = cv2.getPerspectiveTransform(pts1, pts2)
    hasilCrop = cv2.warpPerspective(original, matrix, (output_width, output_height))

    # --- TAMPILKAN HASIL ---
    imgray = cv2.cvtColor(hasilCrop, cv2.COLOR_BGR2GRAY)
    # cv2.imshow("Gray  ", imgray)
    # kalibrasi
    minimum = 125 # diatas 150 akan menjadi putih
    ret, thresh = cv2.threshold(imgray, minimum, 255, cv2.THRESH_BINARY)
    # cv2.imshow("Tresh ", thresh)



    # PROSES JAWABAN
    all_centers = [[0, 0],
                   [output_width, 0],
                   [output_width, output_height],
                   [0, output_height]]

    roi_crop = cv2.cvtColor(imgray, cv2.COLOR_GRAY2BGR)
    heightCrop, widthCrop = roi_crop.shape[:2]
    # Jumlah kotak lebar
    num_boxes_width = 36

    # Jarak antara titik-titik untuk lebar
    width_interval = output_width / num_boxes_width  # Pembagi lebar
    # width_interval = 11  # Pembagi lebar

    # Jumlah kotak tinggi
    num_boxes_height = 53

    # Jarak antara titik-titik untuk tinggi
    height_interval = output_height / num_boxes_height  # Pembagi tinggi
    # height_interval = 10  # Pembagi tinggi

    # Inisialisasi array untuk menyimpan hasil analisis
    result = np.zeros((num_boxes_height, num_boxes_width), dtype=np.int_)

    no_soal = [0 for _ in range(60)] #untuk menyimpan box/koordinat bernilai A-E
    pixel_opsi_soal = [[0 for _ in range(5)] for _ in range(60)]  #untuk menyimpan jumlah pixel setiap opsi dalam 1 nomor

    pg_look = ["","A","B","C","D","E"]
    kode_soal = [""] * 10
    nisn = [""] * 10
    # Loop melalui setiap baris
    for i in range(num_boxes_height):
        # print("ini i ke ",i)
        # Loop melalui setiap kotak dalam baris
        for j in range(num_boxes_width):
            # Potong kotak dari gambar
            start_x = int(j * width_interval)
            # print("start_x : ",start_x)
            end_x = int((j + 1) * width_interval)
            start_y = int(i * height_interval)
            end_y = int((i + 1) * height_interval)
            # Tandai kotak sebagai hitam jika jumlah piksel hitam melebihi ambang batas
            cv2.rectangle(roi_crop, (start_x, start_y), (end_x, end_y), (150, 150, 150), 1) # garis bantu boleh dinyalakan
            # if i == 0:
            roi2 = roi_crop[start_y:end_y,start_x:end_x]
            # imgray2 = cv2.cvtColor(roi2, cv2.COLOR_BGR2GRAY)
            ret2, thresh2 = cv2.threshold(roi2, minimum, 255, 0)
            # cv2.imshow("cut",imgray2)
            black_pixels = np.sum(thresh2 == 0) #hitung jumlah pixel hitam dalam kotak thresh2
            # print("piksel",black_pixels)
            # param_piksel_count = 25

            # HITUNG LUAS KOTAK
            box_width = end_x - start_x
            box_height = end_y - start_y
            total_pixels = box_width * box_height

            # 50% threshold
            threshold_50 = total_pixels * 0.5
            if black_pixels > threshold_50:  # Ubah ambang batas sesuai kebutuhan
                #     # Gambar kotak dengan warna hijau
                if 10 < i < 52:
                    cv2.rectangle(roi_crop, (start_x+2, start_y+2), (end_x-2, end_y-2), (0, 255, 0), 1)
            # Kode Soal
            if 10 < i < 21:
                if 25 < j < 36:
                    if black_pixels > threshold_50:
                        cv2.rectangle(roi_crop, (start_x+2, start_y+2), (end_x-2, end_y-2), (0, 0, 255), 1)
                        digit = i - 11
                        kolom = j - 26

                        kode_soal[kolom] = digit
            # NISN
            if 23 < i < 34:
                if 25 < j < 36:
                    if black_pixels > threshold_50:
                        cv2.rectangle(roi_crop, (start_x+2, start_y+2), (end_x-2, end_y-2), (0, 0, 255), 1)
                        digit = i - 24
                        kolom = j - 26

                        nisn[kolom] = digit
            # ambil Data Jawaban dan komparasi nilai jawaban  ke array pg_look
            if 39 < i < 45:
                # assgin baris jawaban 1
                if 0 < j < 6:
                    if black_pixels > threshold_50:
                        cv2.rectangle(roi_crop, (start_x+2, start_y+2), (end_x-2, end_y-2), (0, 0, 255), 1)
                        no_soal[i-40] = pg_look[j]
                        pixel_opsi_soal[i-40][j-1] = black_pixels
                if 6 < j < 12:
                    if black_pixels > threshold_50:
                        cv2.rectangle(roi_crop, (start_x+2, start_y+2), (end_x-2, end_y-2), (0, 0, 255), 1)
                        no_soal[(i-40)+10] = pg_look[j-6]
                        pixel_opsi_soal[(i-40)+10][j-7] = black_pixels
                if 12 < j < 18:
                    if black_pixels > threshold_50:
                        cv2.rectangle(roi_crop, (start_x+2, start_y+2), (end_x-2, end_y-2), (0, 0, 255), 1)
                        no_soal[(i-40)+20] = pg_look[j-12]
                        pixel_opsi_soal[(i-40)+20][j-13] = black_pixels
                if 18 < j < 24:
                    if black_pixels > threshold_50:
                        cv2.rectangle(roi_crop, (start_x+2, start_y+2), (end_x-2, end_y-2), (0, 0, 255), 1)
                        no_soal[(i-40)+30] = pg_look[j-18]
                        pixel_opsi_soal[(i-40)+30][j-19] = black_pixels
                if 24 < j < 30:
                    if black_pixels > threshold_50:
                        cv2.rectangle(roi_crop, (start_x+2, start_y+2), (end_x-2, end_y-2), (0, 0, 255), 1)
                        no_soal[(i-40)+40] = pg_look[j-24]
                        pixel_opsi_soal[(i-40)+40][j-25] = black_pixels
                if 30 < j < 36:
                    if black_pixels > threshold_50:
                        cv2.rectangle(roi_crop, (start_x+2, start_y+2), (end_x-2, end_y-2), (0, 0, 255), 1)
                        no_soal[(i-40)+50] = pg_look[j-30]
                        pixel_opsi_soal[(i-40)+50][j-31] = black_pixels

            if 45 < i < 51:
                # assgin baris jawaban 1
                if 0 < j < 6:
                    if black_pixels > threshold_50:
                        cv2.rectangle(roi_crop, (start_x+2, start_y+2), (end_x-2, end_y-2), (0, 0, 255), 1)
                        no_soal[i-41] = pg_look[j]
                        pixel_opsi_soal[i-41][j-1] = black_pixels
                if 6 < j < 12:
                    if black_pixels > threshold_50:
                        cv2.rectangle(roi_crop, (start_x+2, start_y+2), (end_x-2, end_y-2), (0, 0, 255), 1)
                        no_soal[(i-41)+10] = pg_look[j-6]
                        pixel_opsi_soal[(i-41)+10][j-7] = black_pixels
                if 12 < j < 18:
                    if black_pixels > threshold_50:
                        cv2.rectangle(roi_crop, (start_x+2, start_y+2), (end_x-2, end_y-2), (0, 0, 255), 1)
                        no_soal[(i-41)+20] = pg_look[j-12]
                        pixel_opsi_soal[(i-41)+20][j-13] = black_pixels
                if 18 < j < 24:
                    if black_pixels > threshold_50:
                        cv2.rectangle(roi_crop, (start_x+2, start_y+2), (end_x-2, end_y-2), (0, 0, 255), 1)
                        no_soal[(i-41)+30] = pg_look[j-18]
                        pixel_opsi_soal[(i-41)+30][j-19] = black_pixels
                if 24 < j < 30:
                    if black_pixels > threshold_50:
                        cv2.rectangle(roi_crop, (start_x+2, start_y+2), (end_x-2, end_y-2), (0, 0, 255), 1)
                        no_soal[(i-41)+40] = pg_look[j-24]
                        pixel_opsi_soal[(i-41)+40][j-25] = black_pixels
                if 30 < j < 36:
                    if black_pixels > threshold_50:
                        cv2.rectangle(roi_crop, (start_x+2, start_y+2), (end_x-2, end_y-2), (0, 0, 255), 1)
                        no_soal[(i-41)+50] = pg_look[j-30]
                        pixel_opsi_soal[(i-41)+50][j-31] = black_pixels

    kode_soal_str = "".join(map(str, kode_soal))
    nisn_str = "".join(map(str, nisn))
    benar = 0
    salah = 0
    display = [0 for _ in range(total_soal)]
    for i in range(len(kunci_jawaban)):
        data = pixel_opsi_soal[i]
        max_value = max(data)

        if max_value == 0:
            no_soal[i] = "-"   # kosong
            display[i] = "-"
        else:
            index_jawaban = data.index(max_value)
            no_soal[i] = pg_look[index_jawaban+1]
            display[i] = pg_look[index_jawaban+1]
            if no_soal[i] == kunci_jawaban[i]:
                benar += 1
            else:
                salah += 1

    # AKHIR PROSES JAWABAN

    _, buffer = cv2.imencode(".jpg", roi_crop)
    img_base64 = base64.b64encode(buffer).decode("utf-8")

    result = {
        "status":200,
        "message":"Scan Berhasil",
        "kode_soal":kode_soal_str,
        "nisn":nisn_str,
        "image": img_base64,
        "total_question": total_soal,
        "key_answers": kunci_jawaban,
        "detected_answers": display,
        "score": benar,
        "correct": benar,
        "wrong": salah
    }

    return json.dumps(result)