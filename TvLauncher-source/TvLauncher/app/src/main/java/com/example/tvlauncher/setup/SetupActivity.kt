package com.example.tvlauncher.setup

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.ArrayRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.leanback.widget.BaseGridView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.tvlauncher.MainActivity
import com.example.tvlauncher.R
import com.example.tvlauncher.data.SetupPrefs
import com.example.tvlauncher.data.tmdb.TmdbRepository
import com.example.tvlauncher.databinding.ActivitySetupBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private val rowsAdapter = SetupAdapter(::onRowClick)
    private val prefs by lazy { SetupPrefs(applicationContext) }

    private var step = STEP_LANGUAGE
    private var doc: String? = null
    private var language = ""
    private var region = ""
    private var recommendations = true
    private var busy = false

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = goBack()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupList()
        onBackPressedDispatcher.addCallback(this, backCallback)

        if (savedInstanceState != null) {
            step = savedInstanceState.getInt(STATE_STEP)
            doc = savedInstanceState.getString(STATE_DOC)
            language = savedInstanceState.getString(STATE_LANGUAGE).orEmpty()
            region = savedInstanceState.getString(STATE_REGION).orEmpty()
            recommendations = savedInstanceState.getBoolean(STATE_RECS, true)
        }
        if (language.isEmpty()) language = prefs.language
        if (region.isEmpty()) region = prefs.region

        busy = true // bloqueia cliques até o fim da animação de entrada
        renderScreen(focus = true)
        enter(forward = true)
    }

    override fun onResume() {
        super.onResume()
        // Volta do seletor de launcher: reavalia se já somos o padrão.
        if (step == STEP_DONE && !busy) renderScreen(focus = false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_STEP, step)
        outState.putString(STATE_DOC, doc)
        outState.putString(STATE_LANGUAGE, language)
        outState.putString(STATE_REGION, region)
        outState.putBoolean(STATE_RECS, recommendations)
    }

    // ---------- Lista: item focado centralizado e bordas esmaecidas ----------

    private fun setupList() = with(binding.list) {
        itemAnimator = null
        adapter = rowsAdapter
        setWindowAlignment(BaseGridView.WINDOW_ALIGN_NO_EDGE)
        setWindowAlignmentOffsetPercent(50f)
        setItemAlignmentOffsetPercent(50f)
        addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) = updateFade()
        })
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateFade() }
    }

    private fun updateFade() {
        val list = binding.list
        val center = list.height / 2f
        if (center <= 0f) return
        for (i in 0 until list.childCount) {
            val child = list.getChildAt(i)
            val distance = ((child.top + child.bottom) / 2f - center).let { if (it < 0) -it else it } / center
            child.alpha = (1f - distance * distance * 0.85f).coerceIn(0.15f, 1f)
        }
    }

    // ---------- Conteúdo de cada tela ----------

    private fun renderScreen(focus: Boolean) {
        val description = descriptionText()
        binding.brand.text = getString(R.string.setup_brand)
        binding.brand.isVisible = step != STEP_LANGUAGE
        binding.title.text = titleText()
        binding.description.text = description
        binding.description.isVisible = description.isNotEmpty()
        backCallback.isEnabled = step > STEP_LANGUAGE || doc != null

        val rows = rowsFor()
        rowsAdapter.submitList(rows) {
            if (focus) {
                val index = initialFocusIndex(rows)
                // post: espera o layout dos itens antes de mover o foco.
                binding.list.post {
                    binding.list.selectedPosition = index
                    binding.list.requestFocus()
                }
            }
        }
    }

    private fun titleText(): String {
        doc?.let { return getString(DOCS.getValue(it).title) }
        return getString(TITLES[step])
    }

    private fun descriptionText(): String = when {
        doc != null -> ""
        step == STEP_REGION -> getString(R.string.setup_region_desc)
        step == STEP_TERMS -> getString(R.string.setup_terms_desc)
        step == STEP_DONE -> getString(
            R.string.setup_done_summary,
            SetupOptions.languages.firstOrNull { it.code == language }?.label ?: language,
            SetupOptions.regions().firstOrNull { it.code == region }?.label ?: region,
            getString(if (recommendations) R.string.setup_on else R.string.setup_off),
            getString(if (isDefaultHome()) R.string.setup_yes else R.string.setup_no),
        )
        else -> ""
    }

    private fun rowsFor(): List<SetupRow> {
        doc?.let { d ->
            val paragraphs = resources.getStringArray(DOCS.getValue(d).paragraphs)
            return listOf<SetupRow>(SetupRow.Action(ACTION_CLOSE_DOC, getString(R.string.setup_doc_close), true)) +
                paragraphs.mapIndexed { i, text -> SetupRow.Card("p$i", title = null, description = text) }
        }
        return when (step) {
            STEP_LANGUAGE -> SetupOptions.languages.map { SetupRow.Option(it.code, it.label) }
            STEP_REGION -> SetupOptions.regions().map { SetupRow.Option(it.code, it.label) }
            STEP_TERMS -> listOf(
                SetupRow.Action(ACTION_ACCEPT, getString(R.string.setup_accept), primary = true),
                SetupRow.Card(CARD_RECS, getString(R.string.setup_recs_title), getString(R.string.setup_recs_desc), recommendations),
                SetupRow.Card(DOC_TERMS, getString(R.string.setup_doc_terms_title), getString(R.string.setup_doc_terms_desc)),
                SetupRow.Card(DOC_PRIVACY, getString(R.string.setup_doc_privacy_title), getString(R.string.setup_doc_privacy_desc)),
                SetupRow.Card(DOC_CREDITS, getString(R.string.setup_doc_credits_title), getString(R.string.setup_doc_credits_desc)),
            )
            else -> buildList {
                if (!isDefaultHome()) add(SetupRow.Action(ACTION_SET_DEFAULT, getString(R.string.setup_set_default)))
                add(SetupRow.Action(ACTION_FINISH, getString(R.string.setup_finish), primary = true))
            }
        }
    }

    private fun initialFocusIndex(rows: List<SetupRow>): Int {
        if (doc != null) return 0
        val index = when (step) {
            STEP_LANGUAGE -> rows.indexOfFirst { it is SetupRow.Option && it.code == language }
            STEP_REGION -> rows.indexOfFirst { it is SetupRow.Option && it.code == region }
            else -> 0
        }
        return index.coerceAtLeast(0)
    }

    // ---------- Navegação com transição ----------

    private fun goBack() {
        when {
            doc != null -> navigate(forward = false) { doc = null }
            step > STEP_LANGUAGE -> navigate(forward = false) { step-- }
        }
    }

    /** Sai deslizando, troca o conteúdo e entra deslizando do lado oposto. */
    private fun navigate(forward: Boolean, change: () -> Unit) {
        if (busy) return
        busy = true
        val out = -shift(forward)
        binding.guidance.animate().alpha(0f).translationX(out).setDuration(EXIT_MS).start()
        binding.list.animate().alpha(0f).translationX(out).setDuration(EXIT_MS).withEndAction {
            change()
            renderScreen(focus = true)
            enter(forward)
        }.start()
    }

    private fun enter(forward: Boolean) {
        val start = shift(forward)
        for (view in listOf(binding.guidance, binding.list)) {
            view.alpha = 0f
            view.translationX = start
            val animator = view.animate().alpha(1f).translationX(0f)
                .setDuration(ENTER_MS).setInterpolator(DecelerateInterpolator())
            if (view === binding.list) animator.withEndAction { busy = false }
            animator.start()
        }
    }

    private fun shift(forward: Boolean) =
        SHIFT_DP * resources.displayMetrics.density * if (forward) 1f else -1f

    // ---------- Cliques ----------

    private fun onRowClick(row: SetupRow) {
        if (busy) return
        when (row) {
            is SetupRow.Option -> navigate(forward = true) {
                if (step == STEP_LANGUAGE) language = row.code else region = row.code
                step++
            }
            is SetupRow.Card -> onCardClick(row)
            is SetupRow.Action -> when (row.id) {
                ACTION_ACCEPT -> acceptTerms()
                ACTION_CLOSE_DOC -> goBack()
                ACTION_SET_DEFAULT -> requestDefaultHome()
                ACTION_FINISH -> finishSetup()
            }
        }
    }

    private fun onCardClick(card: SetupRow.Card) {
        when {
            card.id == CARD_RECS -> {
                recommendations = !recommendations
                renderScreen(focus = false)
            }
            DOCS.containsKey(card.id) -> navigate(forward = true) { doc = card.id }
        }
    }

    private fun acceptTerms() {
        prefs.saveChoices(language, region, recommendations)
        warmUpCatalog()
        navigate(forward = true) { step = STEP_DONE }
    }

    /**
     * Busca o catálogo do TMDB em segundo plano, sem nenhuma interface. Preenche o cache em
     * memória do processo, então a tela inicial abre com as fileiras prontas. Falhas são silenciosas.
     */
    private fun warmUpCatalog() {
        if (!recommendations) return
        lifecycleScope.launch {
            try {
                TmdbRepository(applicationContext).loadRows()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // sem chave, sem rede ou erro da API: a tela inicial lida com isso
            }
        }
    }

    // ---------- Launcher padrão e conclusão ----------

    private fun isDefaultHome(): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val info = packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        return info?.activityInfo?.packageName == packageName
    }

    private fun requestDefaultHome() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && requestHomeRole()) return

        // API 24–28 (ou ROM sem RoleManager): tela "App de início" e, por último, o seletor "Sempre".
        val fallbacks = listOf(
            Intent(Settings.ACTION_HOME_SETTINGS),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
        )
        for (intent in fallbacks) {
            try {
                startActivity(intent)
                return
            } catch (e: ActivityNotFoundException) {
                // tenta a próxima
            }
        }
        toast(R.string.app_unavailable)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @Suppress("DEPRECATION")
    private fun requestHomeRole(): Boolean {
        val roles = getSystemService(RoleManager::class.java) ?: return false
        if (!roles.isRoleAvailable(RoleManager.ROLE_HOME)) return false
        return try {
            startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), REQ_HOME_ROLE)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    private fun finishSetup() {
        prefs.markComplete()
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        finish()
    }

    private fun toast(@StringRes message: Int) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private class Doc(@StringRes val title: Int, @ArrayRes val paragraphs: Int)

    private companion object {
        const val STEP_LANGUAGE = 0
        const val STEP_REGION = 1
        const val STEP_TERMS = 2
        const val STEP_DONE = 3

        const val ACTION_ACCEPT = "accept"
        const val ACTION_CLOSE_DOC = "close_doc"
        const val ACTION_SET_DEFAULT = "set_default"
        const val ACTION_FINISH = "finish"
        const val CARD_RECS = "recommendations"
        const val DOC_TERMS = "doc_terms"
        const val DOC_PRIVACY = "doc_privacy"
        const val DOC_CREDITS = "doc_credits"

        const val REQ_HOME_ROLE = 1001
        const val STATE_STEP = "step"
        const val STATE_DOC = "doc"
        const val STATE_LANGUAGE = "language"
        const val STATE_REGION = "region"
        const val STATE_RECS = "recommendations"

        const val SHIFT_DP = 40f
        const val EXIT_MS = 140L
        const val ENTER_MS = 240L

        val TITLES = intArrayOf(
            R.string.setup_welcome_title, R.string.setup_region_title,
            R.string.setup_terms_title, R.string.setup_done_title,
        )
        val DOCS = mapOf(
            DOC_TERMS to Doc(R.string.setup_doc_terms_title, R.array.setup_terms_paragraphs),
            DOC_PRIVACY to Doc(R.string.setup_doc_privacy_title, R.array.setup_privacy_paragraphs),
            DOC_CREDITS to Doc(R.string.setup_doc_credits_title, R.array.setup_credits_paragraphs),
        )
    }
}
