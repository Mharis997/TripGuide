// Pure helpers (no network) so they can be unit-tested with `npm test`.

/** Returns the canonical reel/post URL and shortcode, or null when the link isn't an Instagram reel/post. */
export function canonicalReel(raw: string): { code: string; url: string } | null {
  let u: URL;
  try { u = new URL(raw.trim()); } catch { return null; }
  if (u.protocol !== "https:" || !/^(www\.)?instagram\.com$/i.test(u.hostname)) return null;
  const m = u.pathname.match(/^\/(?:[\w.]+\/)?(reel|reels|p|tv)\/([A-Za-z0-9_-]{5,40})\/?/);
  if (!m) return null;
  const kind = m[1] === "p" || m[1] === "tv" ? "p" : "reel";
  return { code: m[2], url: `https://www.instagram.com/${kind}/${m[2]}/` };
}

const decode = (s: string) => s
  .replace(/&quot;/g, '"').replace(/&#x27;|&#39;/g, "'").replace(/&lt;/g, "<").replace(/&gt;/g, ">")
  .replace(/&#(\d+);/g, (_, n) => String.fromCodePoint(Number(n)))
  .replace(/&#x([0-9a-f]+);/gi, (_, h) => String.fromCodePoint(parseInt(h, 16)))
  .replace(/&amp;/g, "&");

/** Open Graph tags from the post page – the metadata Meta points developers to since oEmbed dropped thumbnails. */
export function ogTags(html: string): Record<string, string> {
  const out: Record<string, string> = {};
  for (const m of html.matchAll(/<meta\s+[^>]*?(?:property|name)="(og:[\w:]+|description)"[^>]*?content="([^"]*)"/gi)) {
    out[m[1].toLowerCase()] ??= decode(m[2]);
  }
  for (const m of html.matchAll(/<meta\s+[^>]*?content="([^"]*)"[^>]*?(?:property|name)="(og:[\w:]+|description)"/gi)) {
    out[m[2].toLowerCase()] ??= decode(m[1]);
  }
  return out;
}

/** Instagram's og:description looks like `1,234 likes, 56 comments - user on May 1, 2026: "caption"`. */
export function captionAndAuthor(tags: Record<string, string>): { caption: string; author: string } {
  const desc = tags["og:description"] ?? tags["description"] ?? "";
  const quoted = desc.match(/:\s*"([\s\S]*)"\s*\.?\s*$/);
  const author = desc.match(/-\s*([\w.]+)\s+on\s/)?.[1] ?? tags["og:title"]?.match(/^(.+?) on Instagram/)?.[1] ?? "";
  return { caption: (quoted?.[1] ?? desc).trim(), author };
}
