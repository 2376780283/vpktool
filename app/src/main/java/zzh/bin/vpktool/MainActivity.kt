package zzh.bin.vpktool

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import zzh.bin.vpktool.ui.theme.MyComposeApplicationTheme
import java.io.File
import java.io.FileOutputStream
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "VPK_DEBUG"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyComposeApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background) {
                    VpkApp()
                }
            }
        }
    }
}

@Composable
fun VpkApp() {
    val context = LocalContext.current
    var vpkHandle by remember { mutableStateOf(0L) }
    var fileList by remember { mutableStateOf(listOf<String>()) }
    var isLoading by remember { mutableStateOf(false) }
    val vpkEngine = remember { VpkEngine() }
    val coroutineScope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            isLoading = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    Log.d(TAG, "Starting VPK process for: $it")
                    val cursor = context.contentResolver.query(it, null, null, null, null)
                    val fileName = cursor?.use { c ->
                        val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        c.moveToFirst()
                        c.getString(nameIndex)
                    } ?: "temp.vpk"
                    
                    val tempFile = File(context.cacheDir, fileName)
                    Log.d(TAG, "Copying to: ${tempFile.absolutePath}")
                    context.contentResolver.openInputStream(it)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    Log.d(TAG, "Loading VPK...")
                    val handle = vpkEngine.load(tempFile.absolutePath)
                    
                    if (vpkEngine.isValid(handle)) {
                        Log.d(TAG, "VPK loaded, handle: $handle")
                        val files = mutableListOf<String>()
                        var currentFile = vpkEngine.getFirstFile(handle)
                        while (currentFile != null) {
                            files.add(currentFile)
                            currentFile = vpkEngine.getNextFile(handle)
                        }
                        
                        withContext(Dispatchers.Main) {
                            vpkHandle = handle
                            fileList = files
                            isLoading = false
                            Toast.makeText(context, "VPK loaded! Found ${files.size} files.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Log.e(TAG, "Invalid handle returned by engine.")
                        withContext(Dispatchers.Main) {
                            isLoading = false
                            Toast.makeText(context, "Failed to load VPK (Invalid handle)", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during VPK processing: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    Column {
        Button(onClick = { launcher.launch(arrayOf("*/*")) }, enabled = !isLoading) {
            Text(if (isLoading) "Loading..." else "Select VPK File")
        }
        
        LazyColumn {
            items(fileList) { fileName ->
                Text(text = fileName)
                Button(onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            Log.d(TAG, "Extracting: $fileName")
                            val destFile = File(context.filesDir, fileName.substringAfterLast("/"))
                            val success = vpkEngine.extractFile(vpkHandle, fileName, destFile.absolutePath)
                            withContext(Dispatchers.Main) {
                                if (success) {
                                    Toast.makeText(context, "Extracted to ${destFile.absolutePath}", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Failed to extract", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Exception during extraction: ${e.message}", e)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Extraction error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }) {
                    Text("Extract")
                }
            }
        }
    }
}
