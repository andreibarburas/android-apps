package com.brbrs.vinci.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Deterministic per-minute ID for a call log, derived by formatting then re-parsing through
 * the exact same "yyyy-MM-dd HH:mm" pattern used for the exported markdown's Date field
 * (see WebDavRepository.buildMarkdown/parseCallLogMd) and its filename.
 *
 * Using this instead of System.currentTimeMillis() at creation time guarantees that
 * re-importing the same file later (e.g. via restoreFromNextcloud, which is now also run on
 * every regular sync) resolves to the same row instead of creating a duplicate -- the two id
 * computations would otherwise disagree down to the millisecond/second, since the exported
 * file only ever records minute precision.
 */
fun stableLogId(callTimestamp: Long): Long {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return runCatching { sdf.parse(sdf.format(Date(callTimestamp)))?.time }
        .getOrNull() ?: callTimestamp
}
