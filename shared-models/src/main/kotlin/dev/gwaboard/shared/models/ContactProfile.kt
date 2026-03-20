package dev.gwaboard.shared.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * AI-generated profile for a contact, built by the companion app
 * from SMS history analysis. Used by the keyboard's Tier-2 LLM
 * to personalize suggestions based on conversation style.
 */
@Serializable
data class ContactProfile(
    /** Primary language detected in conversations (ISO 639-1 code) */
    @SerialName("dominant_language")
    val dominantLanguage: String,

    /** Detected communication tone (e.g., "formal", "casual", "friendly") */
    val tone: String,

    /** Average response length in characters */
    @SerialName("avg_response_length")
    val avgResponseLength: Int,

    /** Most frequent n-grams used with this contact */
    @SerialName("top_ngrams")
    val topNgrams: List<String>,

    /** Compact style embedding vector for Tier-2 LLM context */
    @SerialName("style_embedding")
    val styleEmbedding: List<Float>,
)
