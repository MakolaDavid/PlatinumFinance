package com.example.platinumfinance

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class NoSqlVaultActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "NoSqlVaultActivity"
        private const val NOSQL_COLLECTION_FILE = "nosql_financial_docs.json"
        private const val BLOB_CONTAINER_DIR = "blob_container"
    }

    private var currentBlobPath: String? = null
    private var currentBlobSize: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        SettingsActivity.applySavedLocale(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nosql_vault)

        Log.d(TAG, "onCreate: NoSqlVaultActivity initialized")

        val etDocCategory = findViewById<EditText>(R.id.etDocCategory)
        val etDocNotes = findViewById<EditText>(R.id.etDocNotes)
        val ivBlobPreview = findViewById<ImageView>(R.id.ivBlobPreview)
        val tvBlobStatus = findViewById<TextView>(R.id.tvBlobStatus)
        val btnSelectBlob = findViewById<Button>(R.id.btnSelectBlob)
        val btnSaveNoSql = findViewById<Button>(R.id.btnSaveNoSql)
        val tvNoSqlOutput = findViewById<TextView>(R.id.tvNoSqlOutput)

        // Read existing documents from the NoSQL database on startup
        readDocumentsFromNoSql(tvNoSqlOutput)

        /**
         * 1. BLOB STORAGE IMPLEMENTATION
         * Picks an unstructured binary image (receipt/proof of payment) and streams
         * the raw bytes into the Blob Storage container.
         * Reference: Android Developers (2025) Activity Result APIs & Binary I/O Streams.
         */
        val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                try {
                    val blobDir = File(filesDir, BLOB_CONTAINER_DIR)
                    if (!blobDir.exists()) blobDir.mkdirs()

                    val blobFileName = "blob_${System.currentTimeMillis()}.jpg"
                    val blobFile = File(blobDir, blobFileName)

                    // Stream raw binary bytes into the Blob file
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        FileOutputStream(blobFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    currentBlobPath = blobFile.absolutePath
                    currentBlobSize = blobFile.length()

                    ivBlobPreview.setImageURI(uri)
                    tvBlobStatus.text = "Blob Stored: $blobFileName (${currentBlobSize / 1024} KB)"
                    Log.i(TAG, "Binary Large Object (Blob) stored at: $currentBlobPath ($currentBlobSize bytes)")
                    Toast.makeText(this, "Receipt saved to Blob Storage!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e(TAG, "Blob storage write failure: ${e.message}", e)
                    Toast.makeText(this, "Failed to store Blob: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        btnSelectBlob.setOnClickListener {
            Log.d(TAG, "Opening system file picker for Blob upload")
            pickImageLauncher.launch("image/*")
        }

        /**
         * 2. NoSQL DATABASE WRITE IMPLEMENTATION
         * Stores a schema-free JSON document inside the document collection.
         * Reference: JSON.org & Document Store Architecture (2025).
         */
        btnSaveNoSql.setOnClickListener {
            val category = etDocCategory.text.toString().trim()
            val notes = etDocNotes.text.toString().trim()

            if (category.isEmpty() || notes.isEmpty()) {
                Log.w(TAG, "NoSQL validation failed: Category or notes empty")
                Toast.makeText(this, "Please enter both category and metadata notes", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            try {
                val collectionArray = loadNoSqlCollection()

                // Construct a flexible, schema-free NoSQL JSON document
                val newDocument = JSONObject().apply {
                    put("_id", UUID.randomUUID().toString().substring(0, 8))
                    put("category", category)
                    put("metadataNotes", notes)
                    put("blobUri", currentBlobPath ?: "none_attached")
                    put("blobSizeBytes", currentBlobSize)
                    put("createdAt", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
                }

                collectionArray.put(newDocument)

                // Persist updated collection to the NoSQL document store
                val file = File(filesDir, NOSQL_COLLECTION_FILE)
                file.writeText(collectionArray.toString(2))

                Log.i(TAG, "NoSQL document stored with _id: ${newDocument.getString("_id")}")
                Toast.makeText(this, "Document stored in NoSQL DB!", Toast.LENGTH_SHORT).show()

                etDocCategory.text.clear()
                etDocNotes.text.clear()

                // Immediately read and refresh the NoSQL database view
                readDocumentsFromNoSql(tvNoSqlOutput)
            } catch (e: Exception) {
                Log.e(TAG, "Error storing document in NoSQL database: ${e.message}", e)
            }
        }
    }

    /**
     * 3. NoSQL DATABASE READ IMPLEMENTATION
     * Reads all JSON documents from the NoSQL collection and formats them for display.
     */
    private fun readDocumentsFromNoSql(outputView: TextView) {
        try {
            val collectionArray = loadNoSqlCollection()
            Log.d(TAG, "Reading NoSQL collection: ${collectionArray.length()} documents found")

            if (collectionArray.length() == 0) {
                outputView.text = "No documents in NoSQL collection yet."
                return
            }

            val builder = StringBuilder()
            for (i in 0 until collectionArray.length()) {
                val doc = collectionArray.getJSONObject(i)
                builder.append("Document #${i + 1} (_id: ${doc.optString("_id")})\n")
                builder.append("  • Category: ${doc.optString("category")}\n")
                builder.append("  • Notes: ${doc.optString("metadataNotes")}\n")
                builder.append("  • Blob File: ${File(doc.optString("blobUri")).name} (${doc.optLong("blobSizeBytes") / 1024} KB)\n")
                builder.append("  • Timestamp: ${doc.optString("createdAt")}\n\n")
            }

            outputView.text = builder.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading from NoSQL collection: ${e.message}", e)
        }
    }

    private fun loadNoSqlCollection(): JSONArray {
        val file = File(filesDir, NOSQL_COLLECTION_FILE)
        return if (file.exists()) {
            JSONArray(file.readText())
        } else {
            JSONArray()
        }
    }
}