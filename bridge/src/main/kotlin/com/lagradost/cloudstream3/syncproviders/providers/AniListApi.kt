package com.lagradost.cloudstream3.syncproviders.providers

import com.lagradost.cloudstream3.syncproviders.SyncAPI

/**
 * Headless stand-in for the app-module AniListApi. TorraStream / StreamPlay /
 * Anichi parse AniList GraphQL responses INTO these data classes, so the
 * shapes must match upstream (fields the plugins actually read).
 */
class AniListApi : SyncAPI("AniList", requireLogin = false) {

    data class Title(
        val romaji: String? = null,
        val english: String? = null,
    )

    data class CoverImage(
        val extraLarge: String? = null,
        val large: String? = null,
        val medium: String? = null,
    )

    data class MediaCoverImage(
        val large: String? = null,
        val medium: String? = null,
    )

    data class SeasonNextAiringEpisode(
        val airingAt: Int? = null,
        val timeUntilAiring: Int? = null,
        val episode: Int? = null,
    )

    data class LikePageInfo(
        val total: Int? = null,
        val hasNextPage: Boolean? = null,
    )

    data class RecommendedMedia(
        val id: Int? = null,
        val title: Title? = null,
        val coverImage: MediaCoverImage? = null,
    )

    data class Recommendation(
        val mediaRecommendation: RecommendedMedia? = null,
    )

    data class RecommendationEdge(
        val node: Recommendation? = null,
    )

    data class RecommendationConnection(
        val edges: List<RecommendationEdge> = emptyList(),
    )
}
