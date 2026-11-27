import assert from "node:assert/strict";
import { test } from "node:test";
import { canonicalReel, captionAndAuthor, ogTags } from "../src/reel.ts";

test("accepts reel, reels, post and user-prefixed links; strips tracking", () => {
  assert.deepEqual(canonicalReel("https://www.instagram.com/reel/C9xYz_12ab/?igsh=abc"), { code: "C9xYz_12ab", url: "https://www.instagram.com/reel/C9xYz_12ab/" });
  assert.equal(canonicalReel("https://instagram.com/reels/C9xYz_12ab")?.url, "https://www.instagram.com/reel/C9xYz_12ab/");
  assert.equal(canonicalReel("https://www.instagram.com/p/Cabcde12/")?.url, "https://www.instagram.com/p/Cabcde12/");
  assert.equal(canonicalReel("https://www.instagram.com/kerala.trips/reel/C9xYz_12ab/")?.code, "C9xYz_12ab");
});

test("rejects non-Instagram and non-https links", () => {
  for (const bad of ["http://www.instagram.com/reel/C9xYz_12ab/", "https://evil.com/reel/C9xYz_12ab/", "https://www.instagram.com.evil.com/reel/C9xYz_12ab/",
    "https://www.instagram.com/explore/", "not a url", ""]) assert.equal(canonicalReel(bad), null, bad);
});

test("parses og tags in either attribute order and extracts caption and author", () => {
  const html = `<meta property="og:image" content="https://cdn/x.jpg?a=1&amp;b=2" />
    <meta content="Kerala Trips on Instagram: &quot;Munnar&quot;" property="og:title" />
    <meta property="og:description" content="1,234 likes, 56 comments - kerala.trips on May 1, 2026: &quot;Sunrise at Kolukkumalai &#x1F305; Jeep from Suryanelli ₹2,500. Go before 5am!&quot;." />`;
  const tags = ogTags(html);
  assert.equal(tags["og:image"], "https://cdn/x.jpg?a=1&b=2");
  const { caption, author } = captionAndAuthor(tags);
  assert.equal(author, "kerala.trips");
  assert.equal(caption, "Sunrise at Kolukkumalai 🌅 Jeep from Suryanelli ₹2,500. Go before 5am!");
});
