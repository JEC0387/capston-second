"""Optional server-side Unsplash random photo proxy. No credential in Android."""
import json
import os
import urllib.error
import urllib.parse
import urllib.request


class UnsplashUnavailable(Exception):
    pass


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def photo_url(query: str) -> str:
    key = os.environ.get("UNSPLASH_ACCESS_KEY", "").strip()
    if not key:
        raise UnsplashUnavailable()
    params = urllib.parse.urlencode({"query": query, "orientation": "portrait"})
    req = urllib.request.Request(
        "https://api.unsplash.com/photos/random?" + params,
        headers={"Authorization": "Client-ID " + key},
    )
    try:
        with urllib.request.build_opener(_NoRedirect()).open(req, timeout=8) as response:
            raw = response.read(128 * 1024 + 1)
        if len(raw) > 128 * 1024:
            raise ValueError("response_size")
        url = json.loads(raw)["urls"]["small"]
        if not isinstance(url, str) or not url.startswith("https://"):
            raise ValueError("response_url")
        return url
    except (urllib.error.URLError, TimeoutError, ValueError, KeyError, TypeError):
        raise UnsplashUnavailable() from None
