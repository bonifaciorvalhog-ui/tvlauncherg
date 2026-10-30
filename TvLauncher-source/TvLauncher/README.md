# TV Launcher (Android TV, minSdk 24)

Launcher em Kotlin no estilo da home do Android TV: barra superior (Pesquisar · Início · Descobrir · Apps, engrenagem, relógio),
destaque rotativo, fileiras de apps, "Assistir a seguir", canais (TvContractCompat) e catálogo TMDB por plataforma.
Assistente de primeira execução (`SetupActivity`) no mesmo APK.

## Compilar
1. Abra a pasta no Android Studio (AGP 8.2.2, Kotlin 1.9.22, JDK 17).
2. Copie `local.properties.example` para `local.properties` e preencha `TMDB_API_KEY` (opcional; sem chave o catálogo some).
3. `./gradlew assembleDebug`

## Instalar como app privilegiado (root)
`extras/install-priv-app.sh app/build/outputs/apk/debug/app-debug.apk`
(copia o APK para `/system/priv-app` e a lista `privapp-permissions` para `/system/etc/permissions`; necessário para `READ_EPG_DATA`).

## Estrutura
- `MainActivity` — home (abas Início/Descobrir/Apps, destaque, fileiras, fallback de grade de apps).
- `ui/` — adapters (`HomeRowsAdapter`, `CardAdapter`, `AppAdapter`), `PagerDots`, extensões de foco/imagem.
- `data/` — `AppRepository`, `ChannelRepository` (TvProvider), `SetupPrefs`, `tmdb/*` (API, cache, deep links).
- `setup/` — `SetupActivity` (idioma → região → termos → concluir) e adapters.
- `res/layout/item_hero.xml`, `item_row.xml`, `item_app_card.xml`, `item_media_card.xml` — medidas tiradas da referência (960×540 dp).

## Observações
- Textos legais (`setup_*_paragraphs`) são modelo; revise com um profissional.
- Deep links abrem o app da plataforma (o TMDB não fornece ID por título), depois YouTube, depois navegador.
- A chave do TMDB fica extraível do APK.
- Para forçar a troca do assistente: aumente `TERMS_VERSION` em `SetupPrefs`.
