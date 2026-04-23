package com.jgd.pothbondhu.myapplication.app
class SplashScreenActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)  // Shows your logo layout

        // Waits 2 seconds, then decides where to go
        Handler(Looper.getMainLooper()).postDelayed({
            if (user is logged in) {
                go to MainActivity      // Skip login if already logged in
            } else {
                go to LoginActivity     // Show login page
            }
        }, 2000)  // 2000 milliseconds = 2 seconds
    }
}