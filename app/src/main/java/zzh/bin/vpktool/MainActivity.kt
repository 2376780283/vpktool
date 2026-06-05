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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import zzh.bin.vpktool.ui.theme.MyComposeApplicationTheme
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "VPK_DEBUG"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyComposeApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
    
    var outputDirUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFiles by remember { mutableStateOf(setOf<String>()) }
    
    val treeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        outputDirUri = uri
        if (uri != null) {
            Toast.makeText(context, "Output directory set", Toast.LENGTH_SHORT).show()
        }
    }
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            isLoading = true
            coroutineScope.launch(Dispatchers.IO) {
                // ... (existing load logic)
                try {
                    val cursor = context.contentResolver.query(it, null, null, null, null)
                    val fileName = cursor?.use { c ->
                        val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        c.moveToFirst()
                        c.getString(nameIndex)
                    } ?: "temp.vpk"
                    
                    val tempFile = File(context.cacheDir, fileName)
                    context.contentResolver.openInputStream(it)?.use { input ->
                        FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                    }
                    
                    val handle = vpkEngine.load(tempFile.absolutePath)
                    
                    if (vpkEngine.isValid(handle)) {
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
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            isLoading = false
                            Toast.makeText(context, "Failed to load", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { launcher.launch(arrayOf("*/*")) }) { Text("Select VPK") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { treeLauncher.launch(null) }) {
                Icon(Icons.Default.Folder, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (outputDirUri == null) "Set Output Dir" else "Change Dir")
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Button(onClick = {
            val uri = outputDirUri
            if (uri == null) {
                Toast.makeText(context, "Set output directory first", Toast.LENGTH_SHORT).show()
                return@Button
            }
            coroutineScope.launch(Dispatchers.IO) {
                for (fileName in selectedFiles) {
                    extractSingleFile(context, vpkEngine, vpkHandle, fileName, uri)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Batch extraction finished", Toast.LENGTH_SHORT).show()
                }
            }
        }, enabled = selectedFiles.isNotEmpty() && outputDirUri != null) {
            Text("Extract Selected (${selectedFiles.size})")
        }
        
        LazyColumn {
            items(fileList) { fileName ->
                val isSelected = selectedFiles.contains(fileName)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedFiles = if (isSelected) selectedFiles - fileName else selectedFiles + fileName
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = isSelected, onCheckedChange = null)
                    Text(text = fileName, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

suspend fun extractSingleFile(context: android.content.Context, engine: VpkEngine, handle: Long, fileName: String, treeUri: Uri) {
    try {
        val pickedDir = DocumentFile.fromTreeUri(context, treeUri)
        val pathParts = fileName.split("/")
        var currentDir = pickedDir
        for (i in 0 until pathParts.size - 1) {
            val part = pathParts[i]
            currentDir = currentDir?.findFile(part) ?: currentDir?.createDirectory(part)
        }
        
        val file = currentDir?.createFile("application/octet-stream", pathParts.last())
        if (file != null) {
            // Use a unique temp file for each extraction
            val tempFile = File(context.cacheDir, "extract_${System.currentTimeMillis()}_${pathParts.last()}")
            val success = engine.extractFile(handle, fileName, tempFile.absolutePath)
            if (success) {
                context.contentResolver.openOutputStream(file.uri)?.use { output ->
                    tempFile.inputStream().use { input -> input.copyTo(output) }
                }
                tempFile.delete()
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error extracting $fileName: ${e.message}")
    }
}
