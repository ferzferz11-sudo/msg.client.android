package msg.client.android

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivityMinimal : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_simple)
        
        // Check if username is already saved
        val savedUsername = getSavedUsername()
        if (savedUsername != null && savedUsername.isNotEmpty()) {
            // Auto-login with saved username
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("USERNAME", savedUsername)
            startActivity(intent)
            finish() // Close main activity so user can't go back
            return
        }
        
        // Show join chat button if no saved username
        val joinChatButton: Button = findViewById(R.id.joinChatButton)
        joinChatButton.setOnClickListener {
            showUsernameDialog()
        }
        
        // Add logout button
        val logoutButton: Button = findViewById(R.id.logoutButton)
        logoutButton.setOnClickListener {
            logout()
        }
        
        Toast.makeText(this, "Lavanda Messenger - Ready!", Toast.LENGTH_SHORT).show()
    }
    
    private fun showUsernameDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_join_chat, null)
        val titleText = dialogView.findViewById<TextView>(R.id.titleText)
        val editText = dialogView.findViewById<EditText>(R.id.editTextUsername)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnJoin = dialogView.findViewById<Button>(R.id.btnJoin)
        
        // Set localized text
        titleText.text = getString(R.string.welcome)
        editText.hint = getString(R.string.enter_username)
        btnCancel.text = getString(R.string.cancel_dialog)
        btnJoin.text = getString(R.string.join)
        
        // Pre-fill with saved username
        val savedUsername = getSavedUsername()
        if (savedUsername != null) {
            editText.setText(savedUsername)
        }
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        btnJoin.setOnClickListener {
            val username = editText.text.toString().trim()
            if (username.isNotEmpty()) {
                saveUsername(username)
                val intent = Intent(this, ChatActivity::class.java)
                intent.putExtra("USERNAME", username)
                startActivity(intent)
                dialog.dismiss()
            } else {
                Toast.makeText(this, getString(R.string.username_empty), Toast.LENGTH_LONG).show()
            }
        }
        
        dialog.show()
        
        // Make dialog wider
        val window = dialog.window
        window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    
    private fun getSavedUsername(): String? {
        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        return prefs.getString("username", null)
    }
    
    private fun saveUsername(username: String) {
        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        prefs.edit().putString("username", username).apply()
    }
    
    private fun logout() {
        Toast.makeText(this, getString(R.string.exiting_app), Toast.LENGTH_SHORT).show()
        
        // Exit the application completely
        finishAffinity()
        System.exit(0)
    }
}
