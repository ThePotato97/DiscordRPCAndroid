package com.thepotato.discordrpc.parsing

import android.media.MediaMetadata

import com.thepotato.discordrpc.models.StatusDisplayTypes

data class ParsedMetadata(
    val details: String, // Top Line
    val state: String,   // Bottom Line
    val displayType: StatusDisplayTypes = StatusDisplayTypes.STATE
)

interface MetadataParser {
    fun parse(metadata: MediaMetadata): ParsedMetadata
}

class DefaultParser : MetadataParser {
    override fun parse(metadata: MediaMetadata): ParsedMetadata {
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown Title"
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
        return ParsedMetadata(
            details = title,
            state = artist ?: "",
            displayType = StatusDisplayTypes.STATE
        )
    }
}

class StremioParser : MetadataParser {
    override fun parse(metadata: MediaMetadata): ParsedMetadata {
        val rawTitle = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown Title"
        // Native format: "Show Name - S01E01 - Episode Title"
        val regex = Regex("^(.*?) - (S\\d+E\\d+) - (.*)$")
        val match = regex.find(rawTitle)
        
        return if (match != null) {
            val (showName, seasonEpisode, episodeTitle) = match.destructured
            ParsedMetadata(
                details = showName,
                state = "$seasonEpisode - $episodeTitle",
                displayType = StatusDisplayTypes.DETAILS
            )
        } else {
            // Fallback to default if regex fails
            DefaultParser().parse(metadata)
        }
    }
}

object MetadataParserFactory {
    private val parsers = mapOf(
        "com.stremio.one" to StremioParser()
    )
    
    private val defaultParser = DefaultParser()

    fun getParser(packageName: String?): MetadataParser {
        return parsers[packageName] ?: defaultParser
    }
}
