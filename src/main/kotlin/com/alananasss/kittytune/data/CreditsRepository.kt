package com.alananasss.kittytune.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Which tab of the credits a person is listed under. */
enum class ContributorCategory { DEV, TRANSLATION, COMMUNITY }

/**
 * One row of the credits.
 *
 * [roleResKey] and [descriptionResKey] are keys into the localized `about_role_*` strings, never
 * text: a role is written once per language and read in all of them, which is the whole reason this
 * is data rather than a sentence in the code.
 */
data class CreditContributor(
    val name: String,
    val roleResKey: String,
    val descriptionResKey: String,
    val badge: String,
    val url: String,
    val avatarUrl: String?,
    val category: ContributorCategory,
)

/**
 * Who gets credited, read from `resources/credits.json`.
 *
 * It was a list of constructors in the middle of the screen, which meant a translator joining needed
 * a build to be listed, and every addition was a diff through a 1700-line file to find the right
 * place in it. Now it is one line of JSON and the file documents itself at the top.
 *
 * The obvious alternative — asking GitHub who contributed — cannot do this job. The endpoint returns
 * the accounts that committed to the repository and nothing else: translations are made on Crowdin,
 * some contributors have no GitHub profile at all, and a bot is not somebody to thank. That data can
 * enrich this file later, but it cannot replace it, because the part worth reading — who translated
 * which language, who did QA — is a human judgement no API holds.
 */
object CreditsRepository {

    private const val RESOURCE = "/credits.json"

    /** Every role's description is its key plus this, which is why only the role has to be listed. */
    private const val DESCRIPTION_SUFFIX = "_desc"

    @Serializable
    private data class File(
        val version: Int = 1,
        val contributors: List<Entry> = emptyList(),
    )

    @Serializable
    private data class Entry(
        val github: String? = null,
        val name: String? = null,
        val url: String? = null,
        val avatar: String? = null,
        val role: String,
        val badge: String = "",
        val category: String = "community",
        val order: Int = Int.MAX_VALUE,
    )

    // Read once. A few kilobytes out of the jar, on first use, and kept from there — the same
    // bargain Strings makes for the string tables, which is the precedent here.
    private val cached: List<CreditContributor> by lazy { read() }

    fun all(): List<CreditContributor> = cached

    private fun read(): List<CreditContributor> = runCatching {
        val stream = CreditsRepository::class.java.getResourceAsStream(RESOURCE)
            ?: return emptyList()
        val file = Json { ignoreUnknownKeys = true }.decodeFromString(
            File.serializer(),
            stream.bufferedReader().use { it.readText() },
        )
        file.contributors
            .sortedBy { it.order }
            .mapNotNull { it.toContributor() }
    }.getOrElse { error ->
        // A credits screen is not worth taking the About dialog down over. An empty list shows the
        // filters and nothing under them, which is a poor but working screen.
        System.err.println("credits.json could not be read: ${error.message}")
        emptyList()
    }

    private fun Entry.toContributor(): CreditContributor? {
        val link = url ?: github?.let { "https://github.com/$it" } ?: return null
        val category = when (category.lowercase()) {
            "dev" -> ContributorCategory.DEV
            "translation" -> ContributorCategory.TRANSLATION
            else -> ContributorCategory.COMMUNITY
        }
        return CreditContributor(
            name = name ?: github ?: link,
            roleResKey = role,
            descriptionResKey = role + DESCRIPTION_SUFFIX,
            badge = badge,
            url = link,
            avatarUrl = avatar ?: github?.let { "https://github.com/$it.png" },
            category = category,
        )
    }
}
