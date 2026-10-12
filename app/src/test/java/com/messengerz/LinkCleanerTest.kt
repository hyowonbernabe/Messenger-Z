package com.messengerz

import com.messengerz.core.LinkCleaner
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkCleanerTest {
    private fun c(u: String) = LinkCleaner.clean(u)

    @Test fun stripsTrackingKeepsEverythingElse() {
        assertEquals("https://example.com/p?id=5#top", c("https://example.com/p?id=5&utm_source=x&fbclid=abc#top"))
        assertEquals("https://example.com/p", c("https://example.com/p?fbclid=abc"))
        assertEquals("https://shop.com/item?q=a%20b&color=red", c("https://shop.com/item?utm_medium=s&q=a%20b&fbclid=Z&color=red"))
    }

    @Test fun leavesCleanAndNonWebLinksAlone() {
        assertEquals("https://example.com/a?b=1&c=2", c("https://example.com/a?b=1&c=2"))
        assertEquals("https://example.com/", c("https://example.com/"))
        assertEquals("fb-messenger://threads/123?fbclid=x", c("fb-messenger://threads/123?fbclid=x"))
        assertEquals("mailto:a@b.com", c("mailto:a@b.com"))
        assertEquals("not a url", c("not a url"))
    }

    @Test fun keepsParamsThatOnlyLookSimilar() {
        // "source" / "ref" / signed-URL params are needed by sites: must survive.
        assertEquals("https://x.com/a?source=app&ref=home&X-Amz-Signature=abc", c("https://x.com/a?source=app&ref=home&X-Amz-Signature=abc"))
        assertEquals("https://youtu.be/abc?t=42&si=QQ", c("https://youtu.be/abc?t=42&si=QQ"))
    }

    @Test fun unwrapsFacebookShim() {
        assertEquals("https://example.com/page?id=7",
            c("https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.com%2Fpage%3Fid%3D7%26fbclid%3DQQ&h=AT0x"))
        // Shim pointing at a non-web scheme is not followed.
        assertEquals("https://l.facebook.com/l.php?u=javascript%3Aalert(1)", c("https://l.facebook.com/l.php?u=javascript%3Aalert(1)"))
        // Other facebook paths untouched.
        assertEquals("https://www.facebook.com/l.php", c("https://www.facebook.com/l.php"))
    }

    @Test fun malformedInputIsReturnedAsIs() {
        assertEquals("https://exa mple.com/?fbclid=1", c("https://exa mple.com/?fbclid=1")) // URI rejects the space
        assertEquals("https://l.facebook.com/l.php?u=%E0%A4%A", c("https://l.facebook.com/l.php?u=%E0%A4%A"))
    }
}
