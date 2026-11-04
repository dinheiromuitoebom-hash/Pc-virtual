package com.anjunauto.inspector

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.util.AtomicFile
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity : Activity() {
    private data class Screen(val id: String, val title: String, val notes: String, val file: String, val savedAt: Long)
    private data class Study(val screens: List<Screen> = emptyList(), val draft: String? = null)
    companion object {
        private val worker = Executors.newSingleThreadExecutor()
        private const val PICK_IMAGE = 101
        private const val EXPORT_ZIP = 102
        private const val MAX_SCREENS = 40
        private const val MAX_BYTES = 12L * 1024 * 1024
        private val imageName = Regex("[0-9a-f-]{36}\\.png")
    }
    private val folder get() = File(filesDir, "manual-study").apply { mkdirs() }
    private val metadata get() = AtomicFile(File(folder, "records.json"))
    private val formPrefs by lazy { getSharedPreferences("manual-form", MODE_PRIVATE) }
    private var study = Study()
    private var busy = false
    private var pendingTasks = 0
    private lateinit var titleInput: EditText
    private lateinit var notesInput: EditText
    private lateinit var status: TextView
    private lateinit var draftStatus: TextView
    private lateinit var rows: LinearLayout
    private lateinit var choose: Button
    private lateinit var preview: Button
    private lateinit var save: Button
    private lateinit var export: Button
    private lateinit var clear: Button
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(Color.rgb(246, 248, 252)) }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(28)) }
        fun label(text: String, size: Float = 16f) = TextView(this).apply {
            this.text = text; textSize = size; setTextColor(Color.rgb(17, 24, 39)); setPadding(0, dp(8), 0, dp(8))
        }.also { body.addView(it) }
        fun button(text: String, action: () -> Unit) = Button(this).apply {
            this.text = text; isAllCaps = false; setOnClickListener { if (!busy) action() }
        }.also { body.addView(it, LinearLayout.LayoutParams(-1, -2)) }
        label("Inspector · estudo manual", 25f)
        label("Escolha capturas para organizar as telas da Anjun. Este modo não lê nem controla outros aplicativos. As cópias e anotações ficam no celular; você decide quando exportar.")
        label("Escolha capturas com nomes, documentos, endereços e códigos de clientes já ocultos. O aplicativo não anonimiza as imagens.")
        status = label("Abrindo estudo…")
        choose = button("Escolher captura") {
            if (study.screens.size >= MAX_SCREENS) return@button
            launchPicker(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "image/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, PICK_IMAGE)
        }
        draftStatus = label("Nenhuma captura selecionada.")
        preview = button("Ver captura selecionada") { study.draft?.let { showImage(it, "Captura selecionada") } }
        titleInput = EditText(this).apply {
            hint = "Título da tela"; setSingleLine(); filters = arrayOf(InputFilter.LengthFilter(120))
            setText(savedInstanceState?.getString("title") ?: formPrefs.getString("title", ""))
        }.also { body.addView(it, LinearLayout.LayoutParams(-1, -2)) }
        notesInput = EditText(this).apply {
            hint = "O que aparece e como você chegou nesta tela?"; minLines = 3; maxLines = 6
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(InputFilter.LengthFilter(4000))
            setText(savedInstanceState?.getString("notes") ?: formPrefs.getString("notes", ""))
        }.also { body.addView(it, LinearLayout.LayoutParams(-1, -2)) }
        save = button("Salvar tela") { saveScreen() }
        export = button("Exportar estudo (.zip)") {
            AlertDialog.Builder(this).setTitle("Exportar capturas e anotações?")
                .setMessage("O ZIP incluirá as imagens salvas e o arquivo registros.json. Revise as capturas para não compartilhar dados pessoais.")
                .setNegativeButton("Cancelar", null).setPositiveButton("Escolher destino") { _, _ ->
                    launchPicker(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE); type = "application/zip"
                        putExtra(Intent.EXTRA_TITLE, "anjun-estudo-manual.zip")
                    }, EXPORT_ZIP)
                }.show()
        }
        clear = button("Apagar todas as telas") {
            AlertDialog.Builder(this).setTitle("Apagar o estudo?")
                .setMessage("As cópias de imagens e as anotações guardadas neste aplicativo serão apagadas.")
                .setNegativeButton("Cancelar", null).setPositiveButton("Apagar") { _, _ ->
                    titleInput.text.clear(); notesInput.text.clear(); persistForm()
                    task({
                        val empty = Study(); writeStudy(empty)
                        folder.listFiles()?.filter { imageName.matches(it.name) }?.forEach { it.delete() }
                        empty
                    }) { render(it) }
                }.show()
        }
        label("Telas salvas", 21f)
        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }.also { body.addView(it) }
        scroll.addView(body)
        setContentView(scroll)
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            scroll.setOnApplyWindowInsetsListener { view, insets ->
                val edge = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
                view.setPadding(edge.left, edge.top, edge.right, edge.bottom); insets
            }
            val light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(light, light)
            scroll.requestApplyInsets()
        }
        task({ readStudy() }) { render(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("title", titleInput.text.toString())
        outState.putString("notes", notesInput.text.toString())
        super.onSaveInstanceState(outState)
    }
    override fun onPause() { persistForm(); super.onPause() }
    private fun persistForm() {
        formPrefs.edit().putString("title", titleInput.text.toString()).putString("notes", notesInput.text.toString()).apply()
    }
    private fun launchPicker(intent: Intent, request: Int) {
        persistForm()
        try { startActivityForResult(intent, request) }
        catch (_: android.content.ActivityNotFoundException) { toast("O seletor de arquivos não está disponível neste aparelho.") }
    }
    @Deprecated("Platform callback used without external libraries")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            PICK_IMAGE -> task({ importImage(uri) }) { render(it); toast("Captura pronta. Complete o título e toque em Salvar tela.") }
            EXPORT_ZIP -> task({ exportZip(uri) }) { toast("Estudo exportado para o local escolhido.") }
        }
    }
    private fun saveScreen() {
        val title = titleInput.text.toString().trim()
        val notes = notesInput.text.toString().trim()
        if (title.isBlank()) { titleInput.error = "Informe o título"; return }
        task({
            val current = readStudy()
            require(current.screens.size < MAX_SCREENS) { "Limite de 40 telas atingido." }
            val file = current.draft ?: throw IOException("Escolha uma captura primeiro.")
            require(imageFile(file).isFile) { "A captura não está disponível. Escolha novamente." }
            Study(current.screens + Screen(file.removeSuffix(".png"), title, notes, file, System.currentTimeMillis()))
                .also { writeStudy(it) }
        }) { titleInput.text.clear(); notesInput.text.clear(); persistForm(); render(it); toast("Tela salva.") }
    }
    private fun render(value: Study) {
        study = value
        status.text = value.screens.size.toString() + " de 40 telas salvas · até 12 MiB por imagem"
        draftStatus.text = if (value.draft == null) "Nenhuma captura selecionada." else "Captura pronta para salvar."
        rows.removeAllViews()
        value.screens.forEachIndexed { index, screen ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(16))
                setBackgroundColor(Color.WHITE)
            }
            row.addView(TextView(this).apply {
                text = (index + 1).toString() + ". " + screen.title; textSize = 18f; setTextColor(Color.rgb(17, 24, 39))
            })
            if (screen.notes.isNotBlank()) row.addView(TextView(this).apply {
                text = screen.notes; textSize = 15f; setTextColor(Color.DKGRAY); setPadding(0, dp(6), 0, dp(6))
            })
            row.addView(Button(this).apply {
                text = "Ver imagem"; isAllCaps = false; setOnClickListener { if (!busy) showImage(screen.file, screen.title) }
            })
            row.addView(Button(this).apply {
                text = "Excluir esta tela"; isAllCaps = false
                setOnClickListener { if (!busy) confirmDelete(screen) }
            })
            rows.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        }
        if (value.screens.isEmpty()) rows.addView(TextView(this).apply { text = "As telas salvas aparecerão aqui."; textSize = 16f })
        updateControls()
    }
    private fun confirmDelete(screen: Screen) {
        AlertDialog.Builder(this).setTitle("Excluir esta tela?").setMessage(screen.title)
            .setNegativeButton("Cancelar", null).setPositiveButton("Excluir") { _, _ ->
                task({
                    val current = readStudy()
                    current.copy(screens = current.screens.filterNot { it.id == screen.id }).also { writeStudy(it) }
                        .also { imageFile(screen.file).delete() }
                }) { render(it) }
            }.show()
    }
    private fun showImage(name: String, title: String) {
        task({ decodeImage(imageFile(name), 900) }) { bitmap ->
            val image = ImageView(this).apply {
                setImageBitmap(bitmap); adjustViewBounds = true; maxHeight = dp(500)
                scaleType = ImageView.ScaleType.FIT_CENTER; setPadding(dp(8), dp(8), dp(8), dp(8))
            }
            AlertDialog.Builder(this).setTitle(title).setView(image).setPositiveButton("Fechar", null)
                .create().apply { setOnDismissListener { image.setImageDrawable(null) }; show() }
        }
    }
    private fun updateControls() {
        choose.isEnabled = !busy && study.screens.size < MAX_SCREENS
        preview.isEnabled = !busy && study.draft != null
        save.isEnabled = !busy && study.draft != null && study.screens.size < MAX_SCREENS
        export.isEnabled = !busy && study.screens.isNotEmpty()
        clear.isEnabled = !busy && (study.screens.isNotEmpty() || study.draft != null)
        titleInput.isEnabled = !busy; notesInput.isEnabled = !busy
        fun enable(view: View) {
            view.isEnabled = !busy
            if (view is ViewGroup) for (i in 0 until view.childCount) enable(view.getChildAt(i))
        }
        enable(rows)
    }
    private fun <T> task(work: () -> T, done: (T) -> Unit) {
        pendingTasks += 1
        busy = true; updateControls()
        worker.execute {
            val result = runCatching(work)
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                pendingTasks -= 1
                busy = pendingTasks > 0; updateControls()
                result.onSuccess(done).onFailure {
                    toast(if (it is OutOfMemoryError) "Memória insuficiente. Escolha uma captura menor." else it.message ?: "Não foi possível concluir a operação.")
                }
            }
        }
    }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    private fun imageFile(name: String): File {
        require(imageName.matches(name)) { "Nome de imagem inválido." }
        return File(folder, name)
    }
    private fun readStudy(): Study {
        val json = try { metadata.openRead().bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) } }
        catch (_: FileNotFoundException) { return Study() }
        val items = json.getJSONArray("screens")
        require(items.length() <= MAX_SCREENS) { "Arquivo de estudo inválido." }
        val screens = (0 until items.length()).map { i ->
            val item = items.getJSONObject(i)
            Screen(item.getString("id"), item.getString("title"), item.getString("notes"), item.getString("file"), item.getLong("savedAt"))
                .also { imageFile(it.file) }
        }
        val draft = if (json.isNull("draft")) null else json.getString("draft").also { imageFile(it) }
        return Study(screens, draft)
    }
    private fun writeStudy(value: Study) {
        val items = JSONArray()
        value.screens.forEach { items.put(JSONObject().put("id", it.id).put("title", it.title).put("notes", it.notes).put("file", it.file).put("savedAt", it.savedAt)) }
        val bytes = JSONObject().put("schema", 1).put("screens", items).put("draft", value.draft ?: JSONObject.NULL).toString().toByteArray(Charsets.UTF_8)
        val file = metadata; val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (error: Throwable) { file.failWrite(stream); throw error }
    }
    private fun importImage(uri: Uri): Study {
        val current = readStudy()
        require(current.screens.size < MAX_SCREENS) { "Limite de 40 telas atingido." }
        val raw = File.createTempFile("capture-", ".tmp", cacheDir)
        val name = UUID.randomUUID().toString() + ".png"
        val output = imageFile(name)
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                raw.outputStream().use { target ->
                    val buffer = ByteArray(65536); var total = 0L
                    while (true) {
                        val read = input.read(buffer); if (read == -1) break
                        total += read
                        require(total <= MAX_BYTES) { "A imagem excede 12 MiB. Escolha uma captura menor." }
                        target.write(buffer, 0, read)
                    }
                }
            } ?: throw IOException("Não foi possível abrir a imagem.")
            val bitmap = decodeImage(raw, 1600)
            try { output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Falha ao preparar a imagem." } } }
            finally { bitmap.recycle() }
            val next = current.copy(draft = name)
            writeStudy(next)
            current.draft?.let { imageFile(it).delete() }
            return next
        } catch (error: Throwable) { output.delete(); throw error }
        finally { raw.delete() }
    }
    private fun decodeImage(file: File, limit: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Formato de imagem não reconhecido." }
        var sample = 1
        while (bounds.outWidth / sample > limit || bounds.outHeight / sample > limit) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bitmap = BitmapFactory.decodeFile(file.path, options) ?: throw IOException("Não foi possível ler a imagem.")
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= limit) return bitmap
        val factor = limit.toDouble() / longest
        val resized = Bitmap.createScaledBitmap(bitmap, maxOf(1, (bitmap.width * factor).toInt()), maxOf(1, (bitmap.height * factor).toInt()), true)
        if (resized !== bitmap) bitmap.recycle()
        return resized
    }
    private fun exportZip(uri: Uri) {
        val current = readStudy()
        require(current.screens.isNotEmpty()) { "Salve pelo menos uma tela antes de exportar." }
        val temp = File.createTempFile("study-", ".zip", cacheDir)
        try {
            val entries = JSONArray()
            current.screens.forEach { entries.put(JSONObject().put("id", it.id).put("titulo", it.title).put("notas", it.notes).put("arquivo", "imagens/" + it.file).put("salvoEm", it.savedAt)) }
            val report = JSONObject().put("formato", 1).put("modo", "manual_local").put("exportadoEm", System.currentTimeMillis()).put("telas", entries)
            ZipOutputStream(temp.outputStream().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("registros.json")); zip.write(report.toString(2).toByteArray(Charsets.UTF_8)); zip.closeEntry()
                current.screens.forEach {
                    zip.putNextEntry(ZipEntry("imagens/" + it.file))
                    imageFile(it.file).inputStream().use { input -> input.copyTo(zip, 65536) }
                    zip.closeEntry()
                }
            }
            contentResolver.openOutputStream(uri, "w")?.use { output -> temp.inputStream().use { it.copyTo(output, 65536) } }
                ?: throw IOException("Não foi possível gravar o ZIP no destino escolhido.")
        } finally { temp.delete() }
    }
}
