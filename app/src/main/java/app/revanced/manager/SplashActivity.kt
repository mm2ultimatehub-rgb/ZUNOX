package app.revanced.manager

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo = findViewById<LogoIntroView>(R.id.logoIntro)
        logo.onFinished = {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        logo.start()
    }
}
