import org.jsoup.Jsoup
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.extractorApis
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName



class VidEmbedProvider : MainAPI() {
    override val mainUrl: String
        get() = "https://vidembed.cc"
    override val name: String
        get() = "VidEmbed"
    override val hasQuickSearch: Boolean
        get() = false
    override val hasMainPage: Boolean
        get() = false

    private fun fixUrl(url: String): String {
        return if (url.startsWith("//")) {
            "https:$url"
        } else if (url.startsWith("/")) {
            "$mainUrl$url"
        } else {
            url
        }
    }

    override val supportedTypes: Set<TvType>
        get() = setOf(TvType.Anime, TvType.AnimeMovie, TvType.TvSeries, TvType.Movie)

    override fun search(query: String): ArrayList<SearchResponse> {
        val link = "$mainUrl/search.html?keyword=$query"
        val html = khttp.get(link).text
        val soup = Jsoup.parse(html)

        return ArrayList(soup.select(".listing.items > .video-block").map { li ->
            val href = fixUrl(li.selectFirst("a").attr("href"))
            val poster = li.selectFirst("img")?.attr("src")
            val title = li.selectFirst(".name").text()
            val year = li.selectFirst(".date")?.text()?.split("-")?.get(0)?.toIntOrNull()

            TvSeriesSearchResponse(
                if (!title.contains("Episode")) title else title.split("Episode")[0].trim(),
                href,
                this.name,
                TvType.TvSeries,
                poster, year,
                null
            )
        })
    }

    override fun load(url: String): LoadResponse? {
        val html = khttp.get(url).text
        val soup = Jsoup.parse(html)

        var title = soup.selectFirst("h1,h2,h3").text()
        title = if (!title.contains("Episode")) title else title.split("Episode")[0].trim()

        val description = soup.selectFirst(".post-entry")?.text()?.trim()
        var poster: String? = null

        val episodes = soup.select(".listing.items.lists > .video-block").withIndex().map { (index, li) ->
            val epTitle = if (li.selectFirst(".name") != null)
                if (li.selectFirst(".name").text().contains("Episode"))
                    "Episode " + li.selectFirst(".name").text().split("Episode")[1].trim()
                else
                    li.selectFirst(".name").text()
            else ""
            val epThumb = li.selectFirst("img")?.attr("src")
            val epDate = li.selectFirst(".meta > .date").text()

            if (poster == null) {
                poster = li.selectFirst("img")?.attr("onerror")?.split("=")?.get(1)?.replace(Regex("[';]"), "")
            }

            val epNum = Regex("""Episode (\d+)""").find(epTitle)?.destructured?.component1()?.toIntOrNull()

            TvSeriesEpisode(
                epTitle,
                null,
                epNum,
                fixUrl(li.selectFirst("a").attr("href")),
                epThumb,
                epDate
            )
        }.reversed()
        val year = if (episodes.isNotEmpty()) episodes.first().date?.split("-")?.get(0)?.toIntOrNull() else null

        val tvType = if (episodes.size == 1 && episodes[0].name == title) TvType.Movie else TvType.TvSeries

        return when (tvType) {
            TvType.TvSeries -> {
                TvSeriesLoadResponse(
                    title,
                    url,
                    this.name,
                    tvType,
                    episodes,
                    poster,
                    year,
                    description,
                    ShowStatus.Ongoing,
                    null,
                    null
                )
            }
            TvType.Movie -> {
                MovieLoadResponse(
                    title,
                    url,
                    this.name,
                    tvType,
                    episodes[0].data,
                    poster,
                    year,
                    description,
                    null,
                    null
                )
            }
            else -> null
        }
    }

    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val iframeLink = Jsoup.parse(khttp.get(data).text).selectFirst("iframe")?.attr("src") ?: return false
        val html = khttp.get(fixUrl(iframeLink)).text
        val soup = Jsoup.parse(html)

        val servers = soup.select(".list-server-items > .linkserver").mapNotNull { li ->
            if (!li?.attr("data-video").isNullOrEmpty()) {
                Pair(li.text(), fixUrl(li.attr("data-video")))
            } else {
                null
            }
        }

        extractorApis.forEach { extractor ->
            servers.forEach {
                if (extractor.name.equals(it.first, ignoreCase = true)) {
                    extractor.getSafeUrl(it.second)?.forEach(callback)
                }
            }
        }

        return true
    }
}


fun main() {
    val api = VidEmbedProvider()
    val search = api.search("overlord")
    val overlordSeasonOne = search.first { it.name == "Overlord - Season 1" }

    val loadResponse = api.load(overlordSeasonOne.url)
    val episodeOne = (loadResponse as TvSeriesLoadResponse).episodes[0]

    fun printlnCallback(extractorLink: ExtractorLink) {
        println(extractorLink)
    }

    api.loadLinks(episodeOne.data, false, { _ -> Unit }) {
        println(it)
    }
}
