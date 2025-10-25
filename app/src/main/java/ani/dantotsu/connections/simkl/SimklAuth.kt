package ani.dantotsu.connections.simkl

import android.content.Context
import android.content.Intent
import android.net.Uri
import ani.dantotsu.R

object SimklAuth {

    private const val AUTH_URL = "https://simkl.com/oauth/authorize"
    const val REDIRECT_URI = "dantotsu://simkl"

    fun startLogin(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(
            "$AUTH_URL?response_type=code&client_id=${Simkl.CLIENT_ID}&redirect_uri=$REDIRECT_URI"
        ))
        context.startActivity(intent)
    }
}
