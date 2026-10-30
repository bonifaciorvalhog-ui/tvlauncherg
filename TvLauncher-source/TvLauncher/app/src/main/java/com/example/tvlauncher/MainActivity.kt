package com.example.tvlauncher

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.leanback.widget.BaseGridView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.example.tvlauncher.data.AppRepository
import com.example.tvlauncher.data.ChannelRepository
import com.example.tvlauncher.data.SetupPrefs
import com.example.tvlauncher.data.tmdb.TmdbRepository
import com.example.tvlauncher.databinding.ActivityMainBinding
import com.example.tvlauncher.model.HomeEntry
import com.example.tvlauncher.model.HomeItem
import com.example.tvlauncher.model.HomeRow
import com.example.tvlauncher.model.RowStyle
import com.example.tvlauncher.setup.SetupActivity
import com.example.tvlauncher.ui.AppAdapter
import com.example.tvlauncher.ui.HomeRowsAdapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private enum class Tab { HOME, DISCOVER, APPS }

    private class Content(val apps: HomeRow, val recommendations: List<HomeRow>)

    private lateinit var binding: ActivityMainBinding

    private val homeAdapter = HomeRowsAdapter(::onItemClick)
    private val appAdapter = AppAdapter(::onItemClick)
    private val appRepository by lazy { AppRepository(applicationContext) }
    private val channelRepository by lazy { ChannelRepository(applicationContext) }
    private val tmdbRepository by lazy { TmdbRepository(applicationContext) }

    private var loadJob: Job? = null
    private var content: Content? = null
    private var catalogRows: List<HomeRow> = emptyList()
    private var tab = Tab.HOME
    private var showingGrid = false
    private var focusPending = true
    private var arrowFromTab = false

    private val tabViews by lazy {
        mapOf(Tab.HOME to binding.tabHome, Tab.DISCOVER to binding.tabDiscover, Tab.APPS to binding.tabApps)
    }

    private val activeList: BaseGridView
        get() = if (showingGrid) binding.appGrid else binding.rows

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = loadHome()
    }

    private val backCallback = object : OnBackPressedCallback(true) {
        // Launcher nunca fecha: Back apenas retorna ao topo da aba Home.
        override fun handleOnBackPressed() = scrollToTop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Primeira execução: abre o assistente e sai. finish() dentro do onCreate vai direto ao onDestroy.
        if (!SetupPrefs(this).isSetupComplete) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this, backCallback)
        setUpLists()
        setUpTabs()
        binding.btnSettings.setOnClickListener { startSafely(Intent(Settings.ACTION_SETTINGS)) }

        // Destaque rotativo: avança a cada HERO_INTERVAL_MS enquanto a tela está visível.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(HERO_INTERVAL_MS)
                    homeAdapter.advanceHero()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        loadHome()
    }

    override fun onStop() {
        unregisterReceiver(packageReceiver)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Botão Home pressionado com o launcher já aberto.
        if (intent.hasCategory(Intent.CATEGORY_HOME)) scrollToTop()
    }

    // ---------- Configuração da tela ----------

    private fun setUpLists() {
        binding.rows.apply {
            adapter = homeAdapter
            // A fileira focada fica com o topo em ROW_ALIGN_DP: as duas primeiras fileiras
            // não rolam e o destaque só sai da tela a partir da terceira.
            setWindowAlignmentOffsetPercent(BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED)
            windowAlignmentOffset = dp(ROW_ALIGN_DP)
            setItemAlignmentOffsetPercent(0f)
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) = updateTopScrim()
            })
        }
        binding.appGrid.adapter = appAdapter
    }

    private fun setUpTabs() {
        binding.tabSearch.setOnClickListener { startSafely(Intent(Intent.ACTION_ASSIST)) }
        binding.tabHome.isSelected = true

        tabViews.forEach { (target, view) ->
            // Setas laterais entre abas já trocam o conteúdo; chegar por cima só foca.
            view.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    arrowFromTab = keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                }
                false
            }
            view.setOnFocusChangeListener { _, focused ->
                if (focused && arrowFromTab) selectTab(target)
                arrowFromTab = false
            }
            view.setOnClickListener {
                selectTab(target)
                focusContent()
            }
        }
    }

    private fun selectTab(target: Tab) {
        if (target == tab) return
        tab = target
        tabViews.forEach { (t, view) -> view.isSelected = t == target }
        render()
    }

    // ---------- Dados ----------

    private fun loadHome() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            // Etapa 1: conteúdo local, sem esperar rede.
            content = withContext(Dispatchers.IO) {
                Content(appRepository.loadAppsRow(), loadRecommendations())
            }
            render()

            // Etapa 2: catálogo TMDB (cache de 30 min), renderiza de novo ao chegar.
            val catalog = loadCatalog()
            if (catalog.isNotEmpty()) {
                catalogRows = catalog
                render()
            }
        }
    }

    /** Qualquer falha do TvProvider (ROM AOSP sem suporte a canais) vira lista vazia. */
    private fun loadRecommendations(): List<HomeRow> = try {
        channelRepository.loadRows()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    private suspend fun loadCatalog(): List<HomeRow> = try {
        tmdbRepository.loadRows()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    // ---------- Renderização ----------

    private fun render() {
        val data = content ?: return
        val (featured, channels) = data.recommendations.partition { it.style == RowStyle.FEATURED }
        val hasRows = featured.isNotEmpty() || channels.isNotEmpty() || catalogRows.isNotEmpty()

        // Grade completa: aba Apps, ou fallback quando não há nada além dos apps.
        showingGrid = tab == Tab.APPS || !hasRows
        binding.appGrid.isVisible = showingGrid
        binding.rows.isVisible = !showingGrid

        // Foco por D-Pad segue a lista ativa, vindo da barra superior.
        val listId = activeList.id
        tabViews.values.forEach { it.nextFocusDownId = listId }
        binding.tabSearch.nextFocusDownId = listId
        binding.btnSettings.nextFocusDownId = listId

        if (showingGrid) {
            homeAdapter.submitList(emptyList())
            appAdapter.submitList(data.apps.items) { afterCommit() }
        } else {
            appAdapter.submitList(emptyList())
            val appsRow = listOf(data.apps).filter { it.items.isNotEmpty() }
            val rows = when (tab) {
                // Ordem: apps favoritos, destaques do TV, catálogo TMDB, canais.
                Tab.HOME -> appsRow + featured + catalogRows + channels
                else -> featured + catalogRows + channels
            }
            val entries = buildList<HomeEntry> {
                add(HomeEntry.Hero(heroItems(featured, catalogRows, channels)))
                rows.forEach { add(HomeEntry.Row(it)) }
            }
            homeAdapter.submitList(entries) { afterCommit() }
        }
        updateTopScrim()
    }

    /** Até 5 destaques com imagem: lista do TV primeiro, depois um de cada plataforma, depois canais. */
    private fun heroItems(featured: List<HomeRow>, catalog: List<HomeRow>, channels: List<HomeRow>): List<HomeItem> {
        val catalogPicks = (0 until HERO_MAX).flatMap { i -> catalog.mapNotNull { it.items.getOrNull(i) } }
        return (featured.flatMap { it.items } + catalogPicks + channels.flatMap { it.items })
            .filter { (it.heroUri ?: it.imageUri) != null }
            .distinctBy { it.id }
            .take(HERO_MAX)
    }

    private fun afterCommit() {
        // post: aguarda o layout dos itens antes de pedir foco.
        if (focusPending) {
            focusPending = false
            activeList.post { focusContent() }
        } else if (!showingGrid) {
            binding.rows.scrollToPosition(0)
        }
    }

    /** Barra superior ganha fundo conforme o destaque sai da tela, para não misturar com as fileiras. */
    private fun updateTopScrim() {
        val progress = when {
            showingGrid -> 1f
            else -> {
                val hero = binding.rows.findViewHolderForAdapterPosition(0)?.itemView
                if (hero == null) 1f else (-hero.top / dp(SCRIM_FADE_DP).toFloat()).coerceIn(0f, 1f)
            }
        }
        binding.topScrim.alpha = progress
    }

    private fun focusContent() {
        if (showingGrid) {
            binding.appGrid.requestFocus()
            return
        }
        // Posição 0 é o destaque (não focável): o foco inicial vai para a primeira fileira.
        val first = if (homeAdapter.currentList.firstOrNull() is HomeEntry.Hero) 1 else 0
        binding.rows.selectedPosition = first
        binding.rows.requestFocus()
    }

    private fun scrollToTop() {
        tab = Tab.HOME
        tabViews.forEach { (t, view) -> view.isSelected = t == Tab.HOME }
        render()
        if (showingGrid) binding.appGrid.scrollToPosition(0)
        focusContent()
    }

    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()

    private fun onItemClick(item: HomeItem) = startSafely(item.intent)

    private fun startSafely(intent: Intent?) {
        if (intent == null) return
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.app_unavailable, Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, R.string.app_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        const val HERO_MAX = 5
        const val HERO_INTERVAL_MS = 8_000L
        const val ROW_ALIGN_DP = 303f
        const val SCRIM_FADE_DP = 40f
    }
}
