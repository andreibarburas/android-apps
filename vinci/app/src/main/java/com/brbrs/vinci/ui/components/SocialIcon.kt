package com.brbrs.vinci.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.brbrs.vinci.R

data class SocialPlatform(
    val key: String,
    val label: String,
    val drawableRes: Int,
    val brandColor: Color,
    val urlPrefix: String,
)

val SOCIAL_PLATFORMS = listOf(
    SocialPlatform("facebook",  "Facebook",  R.drawable.si_facebook,  Color(0xFF1877F2), "https://facebook.com/"),
    SocialPlatform("github",    "GitHub",    R.drawable.si_github,    Color(0xFF181717), "https://github.com/"),
    SocialPlatform("instagram", "Instagram", R.drawable.si_instagram, Color(0xFFE4405F), "https://instagram.com/"),
    SocialPlatform("linkedin",  "LinkedIn",  R.drawable.si_linkedin,  Color(0xFF0A66C2), "https://linkedin.com/in/"),
    SocialPlatform("xing",      "Xing",      R.drawable.si_xing,      Color(0xFF006567), "https://xing.com/profile/"),
    SocialPlatform("pinterest", "Pinterest", R.drawable.si_pinterest, Color(0xFFBD081C), "https://pinterest.com/"),
    SocialPlatform("qzone",     "Qzone",     R.drawable.si_qzone,     Color(0xFFFFCE00), "https://qzone.qq.com/"),
    SocialPlatform("tumblr",    "Tumblr",    R.drawable.si_tumblr,    Color(0xFF35465C), "https://tumblr.com/"),
    SocialPlatform("x",         "X",         R.drawable.si_x,         Color(0xFF000000), "https://x.com/"),
    SocialPlatform("wechat",    "WeChat",    R.drawable.si_wechat,    Color(0xFF07C160), "https://weixin.qq.com/"),
    SocialPlatform("youtube",   "YouTube",   R.drawable.si_youtube,   Color(0xFFFF0000), "https://youtube.com/@"),
    SocialPlatform("mastodon",  "Mastodon",  R.drawable.si_mastodon,  Color(0xFF6364FF), "https://mastodon.social/@"),
    SocialPlatform("diaspora",  "Diaspora",  R.drawable.si_diaspora,  Color(0xFF333333), "https://diaspora.social/"),
    SocialPlatform("nextcloud", "Nextcloud", R.drawable.si_nextcloud, Color(0xFF0082C9), "https://nextcloud.com/"),
    SocialPlatform("other",     "Other",     0,                        Color(0xFF888888), "https://"),
)

fun platformForUrl(url: String): SocialPlatform {
    val lower = url.lowercase()
    // Match by known domain fragments only — no urlPrefix matching to avoid false positives
    return SOCIAL_PLATFORMS.firstOrNull { p ->
        when (p.key) {
            "other", "nextcloud" -> false  // nextcloud has no reliable domain pattern
            "x"        -> "//x.com/" in lower || "//twitter.com/" in lower
            "wechat"   -> "weixin.qq.com" in lower || "wechat" in lower
            "qzone"    -> "qzone.qq.com" in lower
            "mastodon" -> "mastodon." in lower
            "diaspora" -> "diaspora." in lower || "diaspora*" in lower
            else       -> p.key + ".com" in lower || p.key + ".org" in lower
        }
    } ?: SOCIAL_PLATFORMS.last()
}

fun platformForKey(key: String): SocialPlatform =
    SOCIAL_PLATFORMS.firstOrNull { it.key == key } ?: SOCIAL_PLATFORMS.last()

@Composable
fun SocialIconBadge(
    platform: SocialPlatform,
    size: Dp = 32.dp,
    iconSize: Dp = 16.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 4))
            .background(platform.brandColor.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        if (platform.drawableRes != 0) {
            Icon(
                painter = painterResource(id = platform.drawableRes),
                contentDescription = platform.label,
                tint = platform.brandColor,
                modifier = Modifier.size(iconSize),
            )
        } else {
            // "Other" fallback — use a generic link icon tinted gray
            Icon(
                painter = painterResource(id = android.R.drawable.ic_menu_send),
                contentDescription = "Other",
                tint = platform.brandColor,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
