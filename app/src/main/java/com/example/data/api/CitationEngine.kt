package com.example.data.api

import com.example.data.model.Citation
import java.net.URI

object CitationEngine {
    
    fun mapToCitations(sortedSources: List<DeepSearchRawSource>): List<Citation> {
        return sortedSources.mapIndexed { index, source ->
            Citation(
                id = index + 1, // 1-based citation ID
                title = source.title,
                domain = extractDomainPublic(source.url),
                url = source.url,
                snippet = source.snippet,
                credibilityScore = source.credibilityScore,
                provider = source.engine,
                content = source.content
            )
        }
    }

    fun buildEvidencePrompt(citations: List<Citation>): String {
        val sourcesBuilder = StringBuilder()
        citations.forEach { citation ->
            val tier = getCredibilityTier(citation.domain)
            sourcesBuilder.append("[Citation ID: ${citation.id}]\n")
            sourcesBuilder.append("Title: ${citation.title}\n")
            sourcesBuilder.append("Domain: ${citation.domain} (Credibility Tier: $tier)\n")
            sourcesBuilder.append("URL: ${citation.url}\n")
            val effectiveEvidence = if (!citation.content.isNullOrBlank()) {
                citation.content.take(2500)
            } else {
                citation.snippet.take(400)
            }
            sourcesBuilder.append("Evidence: $effectiveEvidence\n\n")
        }
        return sourcesBuilder.toString()
    }

    fun extractDomainPublic(urlStr: String): String {
        return try {
            val uri = URI(urlStr)
            var host = uri.host ?: return urlStr
            if (host.startsWith("www.")) host = host.substring(4)
            host
        } catch (e: Exception) {
            urlStr
        }
    }

    fun getCredibilityTier(domain: String): Int {
        val d = domain.lowercase()
        return when {
            // Tier 1: Government, Academic, Major International Bodies
            d.endsWith(".gov") || d.endsWith(".gov.bd") || d.endsWith(".edu") || d.endsWith(".edu.bd") ||
                    d.contains("who.int") || d.contains("un.org") || d.contains("worldbank.org") ||
                    d.contains("imf.org") || d.contains("ilo.org") -> 1
            // Tier 2: Established News & Journalism Outlets
            d.contains("reuters.com") || d.contains("apnews.com") || d.contains("bbc.com") ||
                    d.contains("bbc.co.uk") || d.contains("bloomberg.com") || d.contains("aljazeera.com") ||
                    d.contains("prothomalo.com") || d.contains("thedailystar.net") || d.contains("bdnews24.com") ||
                    d.contains("dw.com") || d.contains("theguardian.com") || d.contains("nytimes.com") ||
                    d.contains("ittefaq.com") || d.contains("samakal.com") || d.contains("jugantor.com") -> 2
            // Tier 3: Fact-checkers, Reference, Encyclopedia
            d.contains("rumorscanner.com") || d.contains("factwatch.org") || d.contains("factcheck.org") ||
                    d.contains("boomlive.in") || d.contains("snopes.com") || d.contains("afp.com") || 
                    d.contains("wikipedia.org") || d.contains("britannica.com") -> 3
            // Tier 5 (lowest): Low-quality / irrelevant domains
            d.contains("dictionary.cambridge.org") || d.contains("merriam-webster.com") ||
                    d.contains("vocabulary.com") || d.contains("etymonline.com") ||
                    d.contains("thesaurus.com") || d.contains("collinsdictionary.com") ||
                    d.contains("medium.com") || d.contains("wordpress.com") || d.contains("blogspot.com") ||
                    d.contains("quora.com") || d.contains("reddit.com") -> 5
            else -> 4
        }
    }
}
