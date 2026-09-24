package com.owla19s.bridgefs
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.os.Environment
import java.io.File
object TreeUriResolver {
 fun resolve(context: Context, uri: Uri): String? = try { val id=DocumentsContract.getTreeDocumentId(uri); val p=id.split(":",limit=2); if(p.size!=2)return null; val base=if(p[0].equals("primary",true)) Environment.getExternalStorageDirectory() else File("/storage/${p[0]}"); File(base,p[1]).canonicalFile.absolutePath } catch(_:Exception){null}
}
