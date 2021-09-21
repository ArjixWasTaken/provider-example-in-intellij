import org.jsoup.Jsoup

const val mainUrl = "https://vidembed.cc"

fun fixUrl(url: String): String {
    return if (url.startsWith("//")) {
        "https:$url"
    } else if (url.startsWith("/")) {
        "$mainUrl$url"
    } else {
        url
    }
}


data class SearchResult(
    val title: String,
    val link: String,
    val poster: String?,
    val year: Int? = null
)

fun search(query: String): List<SearchResult> {
    val link = "$mainUrl/search.html?keyword=$query"
    val html = khttp.get(link).text
    val soup = Jsoup.parse(html)

    return soup.select(".listing.items > .video-block").map { li ->
        val href = fixUrl(li.selectFirst("a").attr("href"))
        val poster = li.selectFirst("img")?.attr("src")
        val title = li.selectFirst(".name").text()
        val year = li.selectFirst(".date")?.text()?.split("-")?.get(0)?.toIntOrNull()

        SearchResult(
            if (!title.contains("Episode")) title else title.split("Episode")[0].trim(),
            href, poster, year
        )
    }
}


data class Episode(
    val title: String,
    val link: String,
    val poster: String?,
    val date: String? = null
)

data class tvShow(
    val title: String,
    val link: String,
    val poster: String?,
    val description: String?,
    val year: Int?,
    val episodes: List<Episode>
)

fun load(showLink: String): tvShow {
    val html = khttp.get(showLink).text
    val soup = Jsoup.parse(html)

    val title = soup.selectFirst("h1,h2,h3").text()
    val description = soup.selectFirst(".post-entry")?.text()?.trim()
    var poster: String? = null

    val episodes = soup.select(".listing.items.lists > .video-block").map { li ->
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

        Episode(
            epTitle,
            fixUrl(li.selectFirst("a").attr("href")),
            epThumb,
            epDate
        )
    }.reversed()
    val year = if (episodes.isNotEmpty()) episodes.first().date?.split("-")?.get(0)?.toIntOrNull() else null

    return tvShow(
        if (!title.contains("Episode")) title else title.split("Episode")[0].trim(),
        showLink,
        poster,
        description,
        year,
        episodes
    )
}

fun loadLinks(episodeLink: String): List<Pair<String, String>> {
    val iframeLink = Jsoup.parse(khttp.get(episodeLink).text).selectFirst("iframe")?.attr("src") ?: return listOf()

    val html = khttp.get(fixUrl(iframeLink)).text
    val soup = Jsoup.parse(html)

    val servers = soup.select(".list-server-items > .linkserver").mapNotNull { li ->
        if (!li?.attr("data-video").isNullOrEmpty()) {
            Pair(li.text(), fixUrl(li.attr("data-video")))
        } else {
            null
        }
    }
    return servers
}

fun main() {
    val link = "https://vidembed.cc/videos/the-circus-season-6-episode-9"
    println(loadLinks(load(link).episodes[0].link))
}
