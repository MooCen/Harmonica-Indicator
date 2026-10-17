package com.moocengg.harmonica_indicator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class AboutActivity : AppCompatActivity() {
    private lateinit var contactAuthor: LinearLayout
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_about)

        contactAuthor = findViewById(R.id.about_contact_author)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.about_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val toolbar = findViewById<Toolbar>(R.id.about_Toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "关于"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)   // 会自己画返回箭头
        toolbar.setNavigationOnClickListener { finish() }

        contactAuthor.setOnLongClickListener { v ->
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("作者联系方式", "Bilibili：中二病晚期的漠笙\nQQ：848566295\n邮箱：MooCenGG@163.com"))
            Toast.makeText(applicationContext, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
            true
        }
    }
}