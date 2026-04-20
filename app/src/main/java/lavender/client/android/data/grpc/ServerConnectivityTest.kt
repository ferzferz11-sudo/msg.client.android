package lavender.client.android.data.grpc

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.Socket
import java.util.concurrent.TimeUnit

class ServerConnectivityTest {
    
    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult
    
    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting
    
    fun testServerReachability(serverAddress: String, port: Int = 50051) {
        _isTesting.value = true
        _testResult.value = "Testing connection to $serverAddress:$port..."
        
        Thread {
            try {
                // Test basic TCP connectivity
                val socket = Socket()
                _testResult.value = "Attempting TCP connection..."
                
                // Set timeout
                socket.soTimeout = 5000 // 5 seconds
                
                // Try to connect
                socket.connect(java.net.InetSocketAddress(serverAddress, port), 5000)
                
                _testResult.value = "SUCCESS: TCP connection established to $serverAddress:$port"
                socket.close()
                
                // Test gRPC port specifically
                testGrpcPort(serverAddress, port)
                
            } catch (e: Exception) {
                _testResult.value = "FAILED: Cannot connect to $serverAddress:$port - ${e.message}"
            } finally {
                _isTesting.value = false
            }
        }.start()
    }
    
    private fun testGrpcPort(serverAddress: String, port: Int) {
        try {
            // Test if it's actually a gRPC service
            val socket = Socket(serverAddress, port)
            val output = socket.getOutputStream()
            
            // Send a simple gRPC prefix test
            val grpcPrefix = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00) // Simple gRPC frame
            output.write(grpcPrefix)
            output.flush()
            
            _testResult.value = "SUCCESS: gRPC port responds at $serverAddress:$port"
            socket.close()
            
        } catch (e: Exception) {
            _testResult.value = "PARTIAL: TCP works but gRPC test failed - ${e.message}"
        }
    }
    
    fun testLocalNetwork() {
        _isTesting.value = true
        _testResult.value = "Testing local network connectivity..."
        
        Thread {
            try {
                // Test common local network configurations
                val testAddresses = listOf(
                    "159.195.38.145", // Main server IP
                    "192.168.1.135"   // Local server IP
                )
                
                for (address in testAddresses) {
                    _testResult.value = "Testing $address:50051..."
                    
                    try {
                        val socket = Socket()
                        socket.connect(java.net.InetSocketAddress(address, 50051), 2000)
                        _testResult.value = "FOUND: Server responds at $address:50051"
                        socket.close()
                        break
                    } catch (e: Exception) {
                        _testResult.value = "No response from $address:50051"
                    }
                }
                
            } catch (e: Exception) {
                _testResult.value = "Network test failed: ${e.message}"
            } finally {
                _isTesting.value = false
            }
        }.start()
    }
}
