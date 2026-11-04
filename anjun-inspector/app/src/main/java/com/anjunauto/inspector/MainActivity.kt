package com.anjunauto.inspector
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
class MainActivity : Activity() {
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
  val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32,48,32,32) }
  layout.addView(TextView(this).apply { text = "Anjun Auto Inspector — observação local\n\nNão realiza cliques, baixas, envio de fotos nem transmissão de dados.\n\nAtive o serviço de acessibilidade e navegue manualmente na Anjun. O relatório registra apenas tipos e quantidades de elementos."; textSize = 18f })
  layout.addView(Button(this).apply { text = "Configurar acessibilidade"; setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } })
  layout.addView(Button(this).apply { text = "Compartilhar relatório"; setOnClickListener { val data = getSharedPreferences("inspector", MODE_PRIVATE).getString("report", "Nenhum evento observado") ?: ""; startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT,data) }, "Exportar relatório")) } })
  layout.addView(Button(this).apply { text = "Apagar relatório"; setOnClickListener { getSharedPreferences("inspector", MODE_PRIVATE).edit().remove("report").apply() } })
  setContentView(layout)
 }
}