package com.alananasss.kittytune.core

/**
 * Alternate app icon variants, ported from the Android icon switcher
 * (assets generated from the adaptive-icon layers of the Android project).
 * Generated list: keep in sync with resources/icons/variants/.
 */
object AppIconVariants {

    data class Variant(val key: String, val label: String)

    const val DEFAULT_KEY = "default"
    const val RESOURCE_DIR = "icons/variants"

    val ALL: List<Variant> =
        listOf(Variant(DEFAULT_KEY, "Default")) +
            listOf(

                Variant("algeria", "Algeria"),
                Variant("argentina", "Argentina"),
                Variant("australia", "Australia"),
                Variant("austria", "Austria"),
                Variant("belgium", "Belgium"),
                Variant("black", "Black"),
                Variant("blue", "Blue"),
                Variant("bosnia_herzegovina", "Bosnia & Herzegovina"),
                Variant("brazil", "Brazil"),
                Variant("canada", "Canada"),
                Variant("cape_verde", "Cape Verde"),
                Variant("chrome", "Chrome"),
                Variant("colombia", "Colombia"),
                Variant("croatia", "Croatia"),
                Variant("curacao", "Curacao"),
                Variant("czechia", "Czechia"),
                Variant("dr_congo", "DR Congo"),
                Variant("ecuador", "Ecuador"),
                Variant("egypt", "Egypt"),
                Variant("england", "England"),
                Variant("france", "France"),
                Variant("germany", "Germany"),
                Variant("ghana", "Ghana"),
                Variant("haiti", "Haiti"),
                Variant("hot_pink", "Hot Pink"),
                Variant("iran", "Iran"),
                Variant("iraq", "Iraq"),
                Variant("ivory_coast", "Ivory Coast"),
                Variant("japan", "Japan"),
                Variant("jordan", "Jordan"),
                Variant("lavender", "Lavender"),
                Variant("leopard", "Leopard"),
                Variant("lime", "Lime"),
                Variant("mexico", "Mexico"),
                Variant("morocco", "Morocco"),
                Variant("netherlands", "Netherlands"),
                Variant("new_zealand", "New Zealand"),
                Variant("norway", "Norway"),
                Variant("og", "OG"),
                Variant("panama", "Panama"),
                Variant("paraguay", "Paraguay"),
                Variant("portugal", "Portugal"),
                Variant("qatar", "Qatar"),
                Variant("rose_gold", "Rose Gold"),
                Variant("saudi_arabia", "Saudi Arabia"),
                Variant("scotland", "Scotland"),
                Variant("senegal", "Senegal"),
                Variant("silver", "Silver"),
                Variant("soft_purple", "Soft Purple"),
                Variant("south_africa", "South Africa"),
                Variant("south_korea", "South Korea"),
                Variant("spain", "Spain"),
                Variant("sunset", "Sunset"),
                Variant("sweden", "Sweden"),
                Variant("switzerland", "Switzerland"),
                Variant("tie_dye", "Tie Dye"),
                Variant("tunisia", "Tunisia"),
                Variant("turkey", "Turkey"),
                Variant("united_states", "United States"),
                Variant("uruguay", "Uruguay"),
                Variant("uzbekistan", "Uzbekistan"),
            )

    fun byKey(key: String?): Variant? = ALL.firstOrNull { it.key == key }

    fun resourcePath(key: String): String =
        if (key == DEFAULT_KEY) "icons/kittytune.png"
        else "$RESOURCE_DIR/$key.png"

    /**
     * The variants this build can actually draw.
     *
     * [ALL] is a hand-maintained list, so it can name a bitmap the packaged jar does not carry
     * — which is how a stale `.gitignore` rule once took the whole picker down with it
     * (issue #33): `painterResource` throws for a missing path, and it throws from inside
     * composition, where nothing catches it. Filtering here keeps a missing file to one absent
     * tile instead of a crash, and the list is resolved once because the answer cannot change
     * while the process runs.
     */
    val AVAILABLE: List<Variant> by lazy { ALL.filter { hasResource(it.key) } }

    fun hasResource(key: String): Boolean =
        loader()?.getResource(resourcePath(key)) != null

    private fun loader(): ClassLoader? =
        Thread.currentThread().contextClassLoader ?: AppIconVariants::class.java.classLoader
}