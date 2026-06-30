import java.net.URL
import javax.net.ssl.HttpsURLConnection

fun main() {
    try {
        val url = URL("https://cloud-storage-f8ab3-default-rtdb.asia-southeast1.firebasedatabase.app/users.json")
        val conn = url.openConnection() as HttpsURLConnection
        conn.requestMethod = "GET"
        val code = conn.responseCode
        println("Code: \$code")
        val content = conn.inputStream.bufferedReader().use { it.readText() }
        println("Content: \$content")
    } catch(e: Exception) {
        println("Error: \${e}")
    }
}
