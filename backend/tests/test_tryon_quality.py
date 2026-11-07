"""Synthetic local images/provider captures only; no paid service or user photographs."""
import asyncio
import base64
import io

from PIL import Image
import pytest
import tryon_proxy as p


def inline(image, **options):
    output = io.BytesIO()
    image.save(output, "PNG", **options)
    return "data:image/png;base64," + base64.b64encode(output.getvalue()).decode()


def test_normalization_preserves_proportions_fills_alpha_and_strips_metadata():
    source = Image.new("RGBA", (1600,800),(240,10,20,0))
    normalized = p.normalize_image(inline(source))
    image = Image.open(io.BytesIO(base64.b64decode(normalized.split(',')[1])))
    assert image.size == (1296,648)
    assert image.mode == "RGB"
    assert min(image.getpixel((100,100))) > 245
    assert not image.getexif()


def test_results_do_not_lose_resolution_to_the_input_thumbnail_limit():
    encoded = inline(Image.new("RGB", (1728,1152), "red"))
    result = p.normalize_image(encoded, max_side=2048, quality=95)
    assert Image.open(io.BytesIO(base64.b64decode(result.split(',')[1]))).size == (1728,1152)


def test_animated_image_is_rejected_instead_of_silently_using_one_frame():
    encoded = inline(Image.new("RGBA",(64,96),"red"),save_all=True,
        append_images=[Image.new("RGBA",(64,96),"blue")],duration=100,loop=0)
    with pytest.raises(ValueError, match="animated_image"):
        p.normalize_image(encoded)


def test_provider_uses_one_quality_generation_without_retry(monkeypatch):
    captured=[]
    provider=p.FashnProvider("TEST_NOT_A_REAL_API_KEY")
    def fake(path,payload=None):
        captured.append((path,payload))
        return {"id":"mock-id"}
    monkeypatch.setattr(provider,"_request",fake)
    assert asyncio.run(provider.run("person","garment","tops")) == "mock-id"
    assert len(captured)==1
    inputs=captured[0][1]["inputs"]
    assert inputs["mode"]=="quality" and inputs["num_samples"]==1
    assert inputs["return_base64"] is True
