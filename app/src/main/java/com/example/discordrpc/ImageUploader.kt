package com.thepotato.discordrpc

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

import java.util.concurrent.TimeUnit

class ImageUploader(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun uploadImage(bitmap: Bitmap): String? {
        val file = saveBitmapToCache(bitmap) ?: return null
        
        Log.i("ImageUploader", "Uploading image: ${file.length()} bytes")

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("reqtype", "fileupload")
            .addFormDataPart("fileToUpload", "cover_art.png", 
                file.asRequestBody("image/png".toMediaTypeOrNull()))
            .build()

        val request = Request.Builder()
            .url("https://catbox.moe/user/api.php")
            .post(requestBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("ImageUploader", "Upload failed: ${response.code} ${response.message}")
                    null
                } else {
                    val url = response.body?.string()
                    Log.i("ImageUploader", "Upload successful: $url")
                    url
                }
            }
        } catch (e: IOException) {
            Log.e("ImageUploader", "Network error during upload", e)
            null
        }
    }

    private fun saveBitmapToCache(bitmap: Bitmap): File? {
        return try {
            val cacheDir = context.cacheDir
            val file = File(cacheDir, "temp_cover_art.png")
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
            outputStream.close()
            file
        } catch (e: IOException) {
            Log.e("ImageUploader", "Failed to save bitmap to cache", e)
            null
        }
    }
}
