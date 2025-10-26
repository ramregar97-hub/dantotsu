package ani.dantotsu.connections.simkl

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import ani.dantotsu.startMainActivity
import ani.dantotsu.toast
import kotlinx.coroutines.launch

class Login : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val data = intent.data
        if (data != null && data.scheme == "dantotsu" && data.host == "simkl") {
            val code = data.getQueryParameter("code")
            if (code != null) {
                lifecycleScope.launch {
                    val success = Simkl.getInstance().getAccessToken(code)
                    if (success) {
                        Simkl.getInstance().setEnabled(true)
                        toast("Successfully logged in to Simkl! Anime sync is enabled.")
                    } else {
                        toast("Failed to login to Simkl.")
                    }
                    startMainActivity(this@Login)
                    finish()
                }
            } else {
                toast("Failed to get authorization code from Simkl.")
                startMainActivity(this@Login)
                finish()
            }
        } else {
            finish()
        }
    }
}
