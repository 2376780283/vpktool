package zzh.bin.vpktool

class VpkEngine {
    companion object {
        init {
            System.loadLibrary("vpk")
        }
    }

    external fun load(path: String): Long // Returns VPKHandle pointer
    external fun close(handle: Long)
    external fun isValid(handle: Long): Boolean
    external fun getFirstFile(handle: Long): String?
    external fun getNextFile(handle: Long): String?
    external fun extractFile(handle: Long, vpkFilePath: String, destPath: String): Boolean
    
    // Additional JNI methods will be added here to traverse and read the VPK
}
