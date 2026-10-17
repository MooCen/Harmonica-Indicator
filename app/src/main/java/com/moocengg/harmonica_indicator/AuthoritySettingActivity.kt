package com.moocengg.harmonica_indicator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class AuthoritySettingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_authority_setting)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.authority_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val toolbar = findViewById<Toolbar>(R.id.authority_Toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "软件权限设置"
        toolbar.setNavigationOnClickListener { finish() }

        // 悬浮窗
        val overlayStatus = findViewById<TextView>(R.id.authority_overlay_status)
        findViewById<Button>(R.id.authority_overlay_manage).setOnClickListener {
            startActivity(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ))
        }

        // 通知（Android 13+ 才有意义）
        val notifStatus = findViewById<TextView>(R.id.authority_notification_status)
        val notifButton = findViewById<Button>(R.id.authority_notification_manage)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifButton.setOnClickListener {
                // 跳系统应用详情页
                startActivity(Intent(
                    Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                    Uri.parse("package:$packageName")
                ).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                })
            }
        } else {
            notifStatus.text = "系统自动授予"
            notifButton.visibility = android.view.View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        // 悬浮窗
        val overlayStatus = findViewById<TextView>(R.id.authority_overlay_status)
        val hasOverlay = Settings.canDrawOverlays(this)
        overlayStatus.text = if (hasOverlay) "已授权 ✓" else "未授权"
        overlayStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (hasOverlay) android.R.color.holo_green_dark else android.R.color.holo_red_dark
            )
        )

        // 通知
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifStatus = findViewById<TextView>(R.id.authority_notification_status)
            val hasNotif = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            notifStatus.text = if (hasNotif) "已授权 ✓" else "未授权"
            notifStatus.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (hasNotif) android.R.color.holo_green_dark else android.R.color.holo_red_dark
                )
            )
        }
    }
}