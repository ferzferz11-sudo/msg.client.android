package lavender.client.android.data.grpc

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.InetSocketAddress
import java.net.Socket

class ServerConnectivityTest {
    
    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult
    
    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting
    
    private var testJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    fun testServerReachability(serverAddress: String, port: Int = 50051) {
        testJob?.cancel()
        testJob = scope.launch {
            _isTesting.value = true
            _testResult.value = "Testing $serverAddress..."
            
            val result = withContext(Dispatchers.IO) {
                val socket = Socket()
                try {
                    socket.connect(InetSocketAddress(serverAddress, port), 5000)
                    socket.close()
                    "SUCCESS: Connected to $serverAddress"
                } catch (e: Exception) {
                    "FAILED: $serverAddress - ${e.message}"
                }
            }
            
            _testResult.value = result
            _isTesting.value = false
        }
    }
    
    fun cancel() {
        testJob?.cancel()
        _isTesting.value = false
    }
}
